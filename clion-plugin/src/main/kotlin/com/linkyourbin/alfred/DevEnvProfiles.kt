package com.linkyourbin.alfred

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.stream.Stream

internal object DevEnvProfiles {
    const val TITLE: String = "Alfred"

    private const val ACTIVE_PROFILE_FILE = ".active-profile"

    private val projectFiles = listOf(
        ".idea",
        "CMakePresets.json",
        "CMakeUserPresets.json",
    )

    private val configExcludes = setOf(
        "clion-dev-envs",
        "event-log-metadata",
        "plugins",
        "resharper-host",
        "log",
        "tmp",
    )

    fun root(project: Project): Path =
        Path.of(project.basePath).toAbsolutePath().normalize()

    fun profiles(): Path =
        PathManager.getConfigDir().resolve("clion-dev-envs").normalize()

    @Throws(IOException::class)
    fun profileDirs(profiles: Path): List<Path> {
        if (!Files.isDirectory(profiles)) {
            return emptyList()
        }

        val paths = Files.list(profiles)
        return try {
            paths
                .filter { Files.isDirectory(it) }
                .filter { !it.fileName.toString().startsWith(".") }
                .sorted(Comparator.comparing<Path, String> { it.fileName.toString() })
                .toList()
        } finally {
            paths.close()
        }
    }

    @Throws(IOException::class)
    fun migrateProjectProfiles(root: Path) {
        val oldProfiles = root.resolve("clion-dev-envs").normalize()
        if (!Files.isDirectory(oldProfiles) || oldProfiles == profiles()) {
            return
        }

        Files.createDirectories(profiles())
        for (oldProfile in profileDirs(oldProfiles)) {
            val target = profiles().resolve(oldProfile.fileName.toString()).normalize()
            if (!Files.exists(target)) {
                ProfileApplier.copy(oldProfile, target)
            }
        }
    }

    @Throws(IOException::class)
    fun saveProfile(root: Path, name: String) {
        val cleanName = cleanName(name)
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("Profile name is empty.")
        }

        val profile = profiles().resolve(cleanName).normalize()
        if (!profile.startsWith(profiles())) {
            throw IllegalArgumentException("Invalid profile name: $name")
        }

        val tempProfile = profiles().resolve(".$cleanName.tmp").normalize()
        if (!tempProfile.startsWith(profiles())) {
            throw IllegalArgumentException("Invalid profile name: $name")
        }

        ProfileApplier.deleteTree(tempProfile)
        Files.createDirectories(tempProfile)
        Files.createFile(tempProfile.resolve(ProfileApplier.FULL_SNAPSHOT_MARKER))

        var copied = 0
        try {
            for (file in projectFiles) {
                val source = root.resolve(file).normalize()
                if (Files.exists(source)) {
                    ProfileApplier.copy(
                        source,
                        tempProfile.resolve(ProfileApplier.PROJECT_PREFIX).resolve(file).normalize(),
                    )
                    copied++
                }
            }

            val config = PathManager.getConfigDir()
            if (Files.isDirectory(config)) {
                val paths = Files.walk(config)
                try {
                    for (source in paths.filter { Files.isRegularFile(it) }.toList()) {
                        val relative = config.relativize(source)
                        if (shouldSaveConfig(relative)) {
                            try {
                                ProfileApplier.copy(
                                    source,
                                    tempProfile.resolve(ProfileApplier.CONFIG_PREFIX).resolve(relative).normalize(),
                                )
                                copied++
                            } catch (_: IOException) {
                                // Live IDE runtime files can be locked; settings files still copy.
                            }
                        }
                    }
                } finally {
                    paths.close()
                }
            }

            if (copied == 0) {
                throw IllegalArgumentException("No CLion config files found to save.")
            }

            ProfileApplier.deleteTree(profile)
            Files.move(tempProfile, profile, StandardCopyOption.REPLACE_EXISTING)
            setActiveProfile(cleanName)
        } finally {
            ProfileApplier.deleteTree(tempProfile)
        }

        LocalFileSystem.getInstance().refreshIoFiles(listOf(profiles().toFile()))
    }

    @Throws(IOException::class)
    fun applyProfile(profile: Path, root: Path) {
        if (!Files.isDirectory(profile)) {
            throw IllegalArgumentException("Profile not found: ${profile.fileName}")
        }

        ProfileApplier.applySnapshot(profile, root, PathManager.getConfigDir())
        LocalFileSystem.getInstance().refreshIoFiles(listOf(root.toFile()))
    }

    @Throws(IOException::class)
    fun applyProfileAfterExit(profile: Path, root: Path) {
        if (!Files.isDirectory(profile)) {
            throw IllegalArgumentException("Profile not found: ${profile.fileName}")
        }

        setActiveProfile(profile.fileName.toString())
        Runtime.getRuntime().addShutdownHook(
            Thread(
                {
                    try {
                        ProfileApplier.applySnapshot(profile, root, PathManager.getConfigDir())
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                },
                "alfred-profile-switch",
            ),
        )
    }

    @Throws(IOException::class)
    fun activeProfileName(): String {
        val active = profiles().resolve(ACTIVE_PROFILE_FILE)
        if (!Files.isRegularFile(active)) {
            return ""
        }
        return Files.readString(active).trim()
    }

    @Throws(IOException::class)
    fun updateActiveProfile(root: Path) {
        val active = activeProfileName()
        if (active.isBlank()) {
            throw IllegalArgumentException("No active profile. Save or switch a profile first.")
        }

        val profile = profiles().resolve(cleanName(active)).normalize()
        if (!Files.isDirectory(profile) || !profile.startsWith(profiles())) {
            throw IllegalArgumentException("Active profile not found: $active")
        }

        saveProfile(root, active)
    }

    private fun cleanName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-")

    @Throws(IOException::class)
    private fun setActiveProfile(name: String) {
        Files.createDirectories(profiles())
        Files.writeString(profiles().resolve(ACTIVE_PROFILE_FILE), cleanName(name))
    }

    private fun shouldSaveConfig(relative: Path): Boolean {
        val top = relative.getName(0).toString()
        val file = relative.fileName.toString()
        return top !in configExcludes &&
            file != ".lock" &&
            !file.endsWith(".db") &&
            !file.endsWith(".db-shm") &&
            !file.endsWith(".db-wal")
    }
}

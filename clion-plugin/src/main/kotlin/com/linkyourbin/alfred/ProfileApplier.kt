package com.linkyourbin.alfred

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object ProfileApplier {
    const val CONFIG_PREFIX: String = ".clion-config"
    const val PROJECT_PREFIX: String = ".project"
    const val FULL_SNAPSHOT_MARKER: String = ".full-config-snapshot"

    @Throws(IOException::class)
    fun applySnapshot(profile: Path, root: Path, config: Path) {
        val fullSnapshot = Files.exists(profile.resolve(FULL_SNAPSHOT_MARKER))
        val projectSnapshot = profile.resolve(PROJECT_PREFIX)
        val configSnapshot = profile.resolve(CONFIG_PREFIX)

        if (fullSnapshot && Files.isDirectory(projectSnapshot)) {
            replaceMatchingChildren(projectSnapshot, root)
        }
        if (fullSnapshot && Files.isDirectory(configSnapshot)) {
            replaceChildren(
                configSnapshot,
                config,
                setOf(
                    "clion-dev-envs",
                    "event-log-metadata",
                    "plugins",
                    "resharper-host",
                    "log",
                    "tmp",
                    ".lock",
                    "app-internal-state.db",
                    "updatedBrokenPlugins.db",
                ),
            )
        }

        apply(profile, root, config)
    }

    @Throws(IOException::class)
    fun apply(profile: Path, root: Path, config: Path) {
        val paths = Files.walk(profile)
        try {
            for (source in paths.filter { Files.isRegularFile(it) }.toList()) {
                val relative = profile.relativize(source)
                if (relative.startsWith(FULL_SNAPSHOT_MARKER)) {
                    continue
                }
                val targetRoot = if (relative.startsWith(CONFIG_PREFIX)) config else root
                val target = targetRoot.resolve(stripProfilePrefix(relative)).normalize()
                if (!target.startsWith(targetRoot)) {
                    throw IllegalArgumentException("Profile file escapes target root: $source")
                }
                copy(source, target)
            }
        } finally {
            paths.close()
        }
    }

    @Throws(IOException::class)
    fun copy(source: Path, target: Path) {
        if (Files.isDirectory(source)) {
            val paths = Files.walk(source)
            try {
                for (file in paths.filter { Files.isRegularFile(it) }.toList()) {
                    copy(file, target.resolve(source.relativize(file)).normalize())
                }
            } finally {
                paths.close()
            }
            return
        }

        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    fun deleteTree(path: Path) {
        if (!Files.exists(path)) {
            return
        }

        val paths = Files.walk(path)
        try {
            for (target in paths.sorted { left, right -> right.compareTo(left) }.toList()) {
                Files.deleteIfExists(target)
            }
        } finally {
            paths.close()
        }
    }

    @Throws(IOException::class)
    private fun replaceChildren(sourceRoot: Path, targetRoot: Path, protectedNames: Set<String>) {
        Files.createDirectories(targetRoot)
        val targets = Files.list(targetRoot)
        try {
            for (target in targets.toList()) {
                if (target.fileName.toString() !in protectedNames) {
                    deleteTree(target)
                }
            }
        } finally {
            targets.close()
        }

        copy(sourceRoot, targetRoot)
    }

    @Throws(IOException::class)
    private fun replaceMatchingChildren(sourceRoot: Path, targetRoot: Path) {
        Files.createDirectories(targetRoot)
        val sources = Files.list(sourceRoot)
        try {
            for (source in sources.toList()) {
                val target = targetRoot.resolve(source.fileName.toString()).normalize()
                if (!target.startsWith(targetRoot)) {
                    throw IllegalArgumentException("Profile file escapes target root: $source")
                }
                deleteTree(target)
                copy(source, target)
            }
        } finally {
            sources.close()
        }
    }

    private fun stripProfilePrefix(relative: Path): Path =
        if (relative.startsWith(CONFIG_PREFIX) || relative.startsWith(PROJECT_PREFIX)) {
            relative.subpath(1, relative.nameCount)
        } else {
            relative
        }
}

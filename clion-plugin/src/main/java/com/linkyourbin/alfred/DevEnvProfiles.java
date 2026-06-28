package com.linkyourbin.alfred;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.application.PathManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;

final class DevEnvProfiles {
    static final String TITLE = "Alfred";
    private static final String ACTIVE_PROFILE_FILE = ".active-profile";

    private static final List<String> PROJECT_FILES = List.of(
            ".idea",
            "CMakePresets.json",
            "CMakeUserPresets.json"
    );
    private static final Set<String> CONFIG_EXCLUDES = Set.of(
            "clion-dev-envs",
            "event-log-metadata",
            "plugins",
            "resharper-host",
            "log",
            "tmp"
    );

    private DevEnvProfiles() {
    }

    static Path root(Project project) {
        return Path.of(project.getBasePath()).toAbsolutePath().normalize();
    }

    static Path profiles(Path root) {
        return profiles();
    }

    static Path profiles() {
        return PathManager.getConfigDir().resolve("clion-dev-envs").normalize();
    }

    static List<Path> profileDirs(Path profiles) throws IOException {
        if (!Files.isDirectory(profiles)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(profiles)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    static void migrateProjectProfiles(Path root) throws IOException {
        Path oldProfiles = root.resolve("clion-dev-envs").normalize();
        if (!Files.isDirectory(oldProfiles) || oldProfiles.equals(profiles())) {
            return;
        }

        Files.createDirectories(profiles());
        for (Path oldProfile : profileDirs(oldProfiles)) {
            Path target = profiles().resolve(oldProfile.getFileName().toString()).normalize();
            if (!Files.exists(target)) {
                ProfileApplier.copy(oldProfile, target);
            }
        }
    }

    static void saveProfile(Path root, String name) throws IOException {
        String cleanName = cleanName(name);
        if (cleanName.isBlank()) {
            throw new IllegalArgumentException("Profile name is empty.");
        }

        Path profile = profiles().resolve(cleanName).normalize();
        if (!profile.startsWith(profiles())) {
            throw new IllegalArgumentException("Invalid profile name: " + name);
        }

        Path tempProfile = profiles().resolve("." + cleanName + ".tmp").normalize();
        if (!tempProfile.startsWith(profiles())) {
            throw new IllegalArgumentException("Invalid profile name: " + name);
        }

        ProfileApplier.deleteTree(tempProfile);
        Files.createDirectories(tempProfile);
        Files.createFile(tempProfile.resolve(ProfileApplier.FULL_SNAPSHOT_MARKER));

        int copied = 0;
        try {
            for (String file : PROJECT_FILES) {
                Path source = root.resolve(file).normalize();
                if (Files.exists(source)) {
                    ProfileApplier.copy(source, tempProfile.resolve(ProfileApplier.PROJECT_PREFIX).resolve(file).normalize());
                    copied++;
                }
            }

            Path config = PathManager.getConfigDir();
            if (Files.isDirectory(config)) {
                try (Stream<Path> paths = Files.walk(config)) {
                    for (Path source : paths.filter(Files::isRegularFile).toList()) {
                        Path relative = config.relativize(source);
                        if (shouldSaveConfig(relative)) {
                            try {
                                ProfileApplier.copy(source, tempProfile.resolve(ProfileApplier.CONFIG_PREFIX).resolve(relative).normalize());
                                copied++;
                            } catch (IOException ignored) {
                                // ponytail: live IDE runtime files can be locked; settings files still copy.
                            }
                        }
                    }
                }
            }
            if (copied == 0) {
                throw new IllegalArgumentException("No CLion config files found to save.");
            }

            ProfileApplier.deleteTree(profile);
            Files.move(tempProfile, profile, StandardCopyOption.REPLACE_EXISTING);
            setActiveProfile(cleanName);
        } finally {
            ProfileApplier.deleteTree(tempProfile);
        }

        LocalFileSystem.getInstance().refreshIoFiles(List.of(profiles().toFile()));
    }

    static void applyProfile(Path profile, Path root) throws IOException {
        if (!Files.isDirectory(profile)) {
            throw new IllegalArgumentException("Profile not found: " + profile.getFileName());
        }

        ProfileApplier.applySnapshot(profile, root, PathManager.getConfigDir());
        LocalFileSystem.getInstance().refreshIoFiles(List.of(root.toFile()));
    }

    static void applyProfileAfterExit(Path profile, Path root) throws IOException {
        if (!Files.isDirectory(profile)) {
            throw new IllegalArgumentException("Profile not found: " + profile.getFileName());
        }

        setActiveProfile(profile.getFileName().toString());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ProfileApplier.applySnapshot(profile, root, PathManager.getConfigDir());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "alfred-profile-switch"));
    }

    static String activeProfileName() throws IOException {
        Path active = profiles().resolve(ACTIVE_PROFILE_FILE);
        if (!Files.isRegularFile(active)) {
            return "";
        }
        return Files.readString(active).trim();
    }

    static void updateActiveProfile(Path root) throws IOException {
        String active = activeProfileName();
        if (active.isBlank()) {
            throw new IllegalArgumentException("No active profile. Save or switch a profile first.");
        }

        Path profile = profiles().resolve(cleanName(active)).normalize();
        if (!Files.isDirectory(profile) || !profile.startsWith(profiles())) {
            throw new IllegalArgumentException("Active profile not found: " + active);
        }

        saveProfile(root, active);
    }

    private static String cleanName(String name) {
        return name.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    private static void setActiveProfile(String name) throws IOException {
        Files.createDirectories(profiles());
        Files.writeString(profiles().resolve(ACTIVE_PROFILE_FILE), cleanName(name));
    }

    private static boolean shouldSaveConfig(Path relative) {
        String top = relative.getName(0).toString();
        String file = relative.getFileName().toString();
        return !CONFIG_EXCLUDES.contains(top)
                && !file.equals(".lock")
                && !file.endsWith(".db")
                && !file.endsWith(".db-shm")
                && !file.endsWith(".db-wal");
    }
}

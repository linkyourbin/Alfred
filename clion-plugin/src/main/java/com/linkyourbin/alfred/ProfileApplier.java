package com.linkyourbin.alfred;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Stream;

final class ProfileApplier {
    static final String CONFIG_PREFIX = ".clion-config";
    static final String PROJECT_PREFIX = ".project";
    static final String FULL_SNAPSHOT_MARKER = ".full-config-snapshot";

    private ProfileApplier() {
    }

    static void applySnapshot(Path profile, Path root, Path config) throws IOException {
        boolean fullSnapshot = Files.exists(profile.resolve(FULL_SNAPSHOT_MARKER));
        Path projectSnapshot = profile.resolve(PROJECT_PREFIX);
        Path configSnapshot = profile.resolve(CONFIG_PREFIX);

        if (fullSnapshot && Files.isDirectory(projectSnapshot)) {
            replaceMatchingChildren(projectSnapshot, root);
        }
        if (fullSnapshot && Files.isDirectory(configSnapshot)) {
            replaceChildren(configSnapshot, config, Set.of(
                    "clion-dev-envs",
                    "event-log-metadata",
                    "plugins",
                    "resharper-host",
                    "log",
                    "tmp",
                    ".lock",
                    "app-internal-state.db",
                    "updatedBrokenPlugins.db"
            ));
        }

        apply(profile, root, config);
    }

    static void apply(Path profile, Path root, Path config) throws IOException {
        try (Stream<Path> paths = Files.walk(profile)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                Path relative = profile.relativize(source);
                if (relative.startsWith(FULL_SNAPSHOT_MARKER)) {
                    continue;
                }
                Path targetRoot = relative.startsWith(CONFIG_PREFIX) ? config : root;
                Path target = targetRoot.resolve(stripProfilePrefix(relative)).normalize();
                if (!target.startsWith(targetRoot)) {
                    throw new IllegalArgumentException("Profile file escapes target root: " + source);
                }
                copy(source, target);
            }
        }
    }

    static void copy(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            try (Stream<Path> paths = Files.walk(source)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    copy(file, target.resolve(source.relativize(file)).normalize());
                }
            }
            return;
        }

        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            for (Path target : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(target);
            }
        }
    }

    private static void replaceChildren(Path sourceRoot, Path targetRoot, Set<String> protectedNames) throws IOException {
        Files.createDirectories(targetRoot);
        try (Stream<Path> targets = Files.list(targetRoot)) {
            for (Path target : targets.toList()) {
                if (!protectedNames.contains(target.getFileName().toString())) {
                    deleteTree(target);
                }
            }
        }

        copy(sourceRoot, targetRoot);
    }

    private static void replaceMatchingChildren(Path sourceRoot, Path targetRoot) throws IOException {
        Files.createDirectories(targetRoot);
        try (Stream<Path> sources = Files.list(sourceRoot)) {
            for (Path source : sources.toList()) {
                Path target = targetRoot.resolve(source.getFileName().toString()).normalize();
                if (!target.startsWith(targetRoot)) {
                    throw new IllegalArgumentException("Profile file escapes target root: " + source);
                }
                deleteTree(target);
                copy(source, target);
            }
        }
    }

    private static Path stripProfilePrefix(Path relative) {
        if (relative.startsWith(CONFIG_PREFIX) || relative.startsWith(PROJECT_PREFIX)) {
            return relative.subpath(1, relative.getNameCount());
        }
        return relative;
    }
}

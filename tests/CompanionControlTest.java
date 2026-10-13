package io.github.zopulus.coogle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Runs production shell commands only against isolated module directories. */
public final class CompanionControlTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("coogle-control");
        Path active = root.resolve("active"), pending = root.resolve("pending");
        try {
            check(run(false, active, pending).equals("MISSING"), "Missing module not reported");
            Files.createDirectories(active);
            Files.writeString(active.resolve("module.prop"), "id=coogle_companion\n");
            check(run(true, active, pending).equals("UPDATE_REQUIRED"), "Old module accepted as controllable");
            check(!Files.exists(active.resolve("features.conf")), "Old module was modified");
            Files.writeString(active.resolve("module.prop"), "id=coogle_companion\ncontrolVersion=1\npayloadRevision=2\n");
            Files.writeString(active.resolve("features.sh"), "fixture\n");
            Files.writeString(active.resolve("mount-google.sh"), "fixture\n");
            Files.writeString(active.resolve("applied-features.conf"), "hey=0\n");
            check(run(false, active, pending).equals("READY"), "Matching state not ready");
            var timestamp = java.nio.file.attribute.FileTime.fromMillis(1_000_000);
            Files.setLastModifiedTime(active.resolve("features.conf"), timestamp);
            check(run(false, active, pending).equals("READY"), "Unchanged sync changed status");
            check(Files.getLastModifiedTime(active.resolve("features.conf")).equals(timestamp),
                    "Unchanged Hey state rewrote configuration");
            check(!Files.exists(active.resolve("features.conf.tmp")), "Unchanged sync left temporary file");
            check(run(true, active, pending).equals("PENDING"), "Changed state omitted reboot requirement");
            check(Files.readString(active.resolve("features.conf")).equals("hey=1\n"), "Hey switch not written");
            Files.createDirectories(pending);
            Files.copy(active.resolve("module.prop"), pending.resolve("module.prop"));
            Files.copy(active.resolve("features.sh"), pending.resolve("features.sh"));
            Files.copy(active.resolve("mount-google.sh"), pending.resolve("mount-google.sh"));
            check(run(false, active, pending).equals("PENDING"), "Staged module not reported");
            check(Files.readString(active.resolve("features.conf")).equals("hey=0\n")
                    && Files.readString(pending.resolve("features.conf")).equals("hey=0\n"), "Staged update lost switch state");
            Files.createFile(pending.resolve("disable"));
            check(run(false, active, pending).equals("DISABLED"), "Disabled module accepted");
            check(runCommand(CompanionInstaller.syncCommand(true), active, pending).equals("DISABLED"),
                    "Automatic sync ignored disabled state");
            check(Files.readString(pending.resolve("features.conf")).equals("hey=0\n"),
                    "Automatic sync modified disabled module");
            Files.delete(pending.resolve("disable"));
            check(runCommand(CompanionInstaller.syncCommand(true), active, pending).equals("PENDING"),
                    "Automatic sync omitted pending state");
            check(Files.readString(active.resolve("features.conf")).equals("hey=1\n")
                    && Files.readString(pending.resolve("features.conf")).equals("hey=1\n"),
                    "Automatic sync did not mirror both installed directories");
            for (String marker : new String[]{"skip_mount", "component-error", "remove"}) {
                Files.createFile(pending.resolve(marker));
                check(runCommand(CompanionInstaller.syncCommand(false), active, pending).equals("DISABLED"),
                        "Unavailable mounts accepted: " + marker);
                check(Files.readString(pending.resolve("features.conf")).equals("hey=1\n"),
                        "Automatic sync modified unavailable mounts");
                Files.delete(pending.resolve(marker));
            }
            System.out.println("PASS: Hey Google mirror, staged updates, reboot requirement and legacy detection");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static String run(boolean enabled, Path active, Path pending) throws Exception {
        return runCommand(CompanionInstaller.controlCommand(enabled), active, pending);
    }
    private static String runCommand(String command, Path active, Path pending) throws Exception {
        command = command
                .replace("/data/adb/modules/coogle_companion", unix(active))
                .replace("/data/adb/modules_update/coogle_companion", unix(pending));
        Process process = new ProcessBuilder("C:/Program Files/Git/bin/bash.exe", "-c", command).redirectErrorStream(true).start();
        String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new AssertionError(result);
        return result;
    }
    private static String unix(Path path) {
        String value = path.toString().replace('\\', '/');
        return "/" + Character.toLowerCase(value.charAt(0)) + value.substring(2);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

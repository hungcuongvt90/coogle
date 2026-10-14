package io.github.zopulus.coogle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Exercise production native-setting commands against isolated shell functions, never a device. */
public final class WalletNativeGestureCommandTest {
    public static void main(String[] args) throws Exception {
        String mocks = "enabled=0; action=null; mode=2; option=enabled; callbacks=enabled; "
                + "cmd() { [ \"$*\" = 'overlay lookup --user 10 android android:integer/config_doubleTapPowerGestureMode' ] || return 4; printf '%s\\n' \"$mode\"; }; "
                + "aflags() { [ \"$1\" = list ] || return 4; "
                + "printf 'android.service.quickaccesswallet.launch_wallet_option_on_power_double_tap %s - default read-only system\\n' \"$option\"; "
                + "printf 'android.service.quickaccesswallet.launch_wallet_via_sysui_callbacks %s - default read-only system\\n' \"$callbacks\"; }; "
                + "settings() { [ \"$1\" = --user ] && [ \"$2\" = 10 ] && [ \"$4\" = secure ] || return 7; "
                + "case \"$5\" in double_tap_power_button_gesture_enabled) "
                + "case \"$3\" in get) printf '%s\\n' \"$enabled\";; put) enabled=$6;; delete) enabled=null;; esac;; "
                + "double_tap_power_button_gesture) "
                + "case \"$3\" in get) printf '%s\\n' \"$action\";; put) action=$6;; delete) action=null;; esac;; *) return 8;; esac; }; ";
        String select = WalletNativeGesture.supportCommand(10) + WalletNativeGesture.writeCommand(10, new WalletNativeGesture.State("1", "1"));
        Result selected = run(mocks + select);
        check(selected.status == 0 && selected.output.equals("1\n1"), "Native selection or readback failed: " + selected.output);
        String largeFlags = "original_aflags=$(declare -f aflags); eval \"${original_aflags/aflags ()/small_aflags ()}\"; "
                + "aflags() { small_aflags \"$@\"; builtin printf '%180000s\\n' ''; }; "
                // Android's external printf is limited by the kernel's per-argument size.
                + "printf() { for arg in \"$@\"; do [ \"${#arg}\" -le 131072 ] || return 7; done; builtin printf \"$@\"; }; ";
        Result large = run(mocks + largeFlags + select);
        check(large.status == 0 && large.output.equals("1\n1"), "Large real-world flag list broke an authorized native setup: " + large.output);
        for (String unsupported : new String[]{"mode=0; ", "option=disabled; ", "callbacks=disabled; ",
                "cmd() { return 4; }; ", "aflags() { return 4; }; "}) {
            Result refused = run(mocks + unsupported + select);
            check(refused.status == 0 && refused.output.startsWith("COOGLE_WALLET_"), "Unsupported platform changed settings or was mislabeled as Root denial");
        }
        Result brokenFlags = run(mocks + "aflags() { builtin printf 'android.service.quickaccesswallet.launch_wallet_option_on_power_double_tap enabled - default read-only system\\n'; "
                + "builtin printf 'android.service.quickaccesswallet.launch_wallet_via_sysui_callbacks enabled - default read-only system\\n'; return 4; }; " + select);
        check(brokenFlags.output.equals("COOGLE_WALLET_FLAGS_UNAVAILABLE"), "Failed flag command accepted partial output");
        Result failed = run(mocks + "settings() { return 1; }; " + select);
        check(failed.status != 0, "Failed native setting write accepted");
        Result restored = run(mocks + WalletNativeGesture.writeCommand(10, new WalletNativeGesture.State("0", "null")));
        check(restored.status == 0 && restored.output.equals("0\nnull"), "Absent native target was not restored");
        String command = WalletNativeGesture.writeCommand(10, new WalletNativeGesture.State("1", "1"));
        check(command.indexOf("put secure double_tap_power_button_gesture 1") < command.indexOf("put secure double_tap_power_button_gesture_enabled 1"),
                "Gesture enabled before Wallet was selected");
        System.out.println("PASS: native capability flags, user-scoped selection, readback, unsupported routes and absent-value restore");
    }
    record Result(int status, String output) { }
    private static Result run(String command) throws Exception {
        var script = Files.createTempFile("coogle-native-wallet", ".sh");
        try {
            Files.writeString(script, command, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder("C:/Program Files/Git/bin/bash.exe", script.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new Result(process.waitFor(), output);
        } finally { Files.deleteIfExists(script); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

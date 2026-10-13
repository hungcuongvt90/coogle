package io.github.zopulus.coogle;

import io.github.zopulus.coogle.ui.*;
import java.util.Map;

public final class SettingsUiStateTest {
    public static void main(String[] args) {
        CoogleState initial = new CoogleState();
        check(initial.getConnected() == null && initial.getFeatures().isEmpty(), "Initial UI claimed inactive or displayed guessed switch values");
        for (CompanionState status : new CompanionState[]{CompanionState.MISSING, CompanionState.DISABLED, CompanionState.PENDING, CompanionState.READY}) {
            String normal = state(status, false, "").getCompanionSummary();
            check(normal.equals(status.getSummary()), "Normal installation summary altered");
            check(state(status, true, "").getCompanionSummary().equals(normal), "Background refresh changed the card summary");
            check(state(status, false, "denied").getCompanionSummary().startsWith(normal), "Detection failure reset installation state");
            check(state(status, false, "denied").getCompanionSummary().equals(normal + "（检测失败）"), "Detection failure concealed or obsolete confirmation label retained");
        }
        check(state(CompanionState.UNKNOWN, false, "denied").getCompanionSummary().equals("附属模块：检测失败"), "Unknown failure reported missing");
        check(CompanionState.DISABLED.getSummary().equals("附属模块：已安装未启用"), "Installed disabled module reported missing");
        check(!state(CompanionState.MISSING, false, "").getHeyAvailable(), "Missing support enabled Hey Google toggle");
        check(state(CompanionState.MISSING, false, "").getHeyDescription().equals("需点击左上角安装附属模块"), "Missing support install instruction changed");
        check(state(CompanionState.READY, false, "").getHeyAvailable() && state(CompanionState.PENDING, false, "").getHeyAvailable(), "Installed support cannot configure Hey Google");
        check(state(CompanionState.READY, false, "").getHeyDescription().equals("补全系统级 Voice Match 功能"), "Hey Google description changed");
        check(!state(CompanionState.DISABLED, false, "").getHeyAvailable(), "Disabled support enabled Hey Google toggle");
        System.out.println("PASS: neutral startup, four installation states, cache, background refresh and failure preservation");
    }
    private static CoogleState state(CompanionState status, boolean checking, String error) {
        return new CoogleState(true, Map.of(FeaturePolicy.HEY_GOOGLE, false), false, false, false,
                "", status, checking, error, false, DialogKind.NONE, "");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

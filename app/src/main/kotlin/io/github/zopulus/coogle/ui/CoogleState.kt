package io.github.zopulus.coogle.ui

import io.github.zopulus.coogle.FeaturePolicy

enum class CompanionState(val summary: String) {
    UNKNOWN("附属模块：尚未检测"),
    MISSING("附属模块：未安装"),
    DISABLED("附属模块：已安装未启用"),
    UPDATE_REQUIRED("附属模块：需要更新"),
    READY("附属模块：已安装并启用"),
    PENDING("附属模块：已安装请重启设备")
}

enum class DialogKind { NONE, STATUS, INSTALL, INSTALLED, RESTART, REBOOT, HIDE_ICON, ERROR }

data class CoogleState(
    val connected: Boolean? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val busy: Boolean = false,
    val restarting: Boolean = false,
    val scopePending: Boolean = false,
    val assistant: String = "当前数字助理：读取中…",
    val companion: CompanionState = CompanionState.UNKNOWN,
    val companionChecking: Boolean = false,
    val companionCached: Boolean = false,
    val companionError: String = "",
    val iconHidden: Boolean = false,
    val dialog: DialogKind = DialogKind.NONE,
    val dialogText: String = "",
) {
    val heyAvailable: Boolean get() = companion == CompanionState.READY || companion == CompanionState.PENDING
    val heyDescription: String get() = if (companion == CompanionState.UNKNOWN || companion == CompanionState.MISSING)
        "需点击左上角安装附属模块" else "补全系统级 Voice Match 唤醒功能"
    val companionSummary: String get() = when {
        companionError.isNotEmpty() -> if (companion == CompanionState.UNKNOWN) "附属模块：检测失败"
            else "${companion.summary}（上次确认，检测失败）"
        busy -> if (companion == CompanionState.UNKNOWN) "附属模块：正在安装…"
            else "${companion.summary}（正在安装…）"
        companionChecking -> if (companion == CompanionState.UNKNOWN) "附属模块：正在检测…"
            else "${companion.summary}（正在检测…）"
        companionCached -> "${companion.summary}（上次确认）"
        else -> companion.summary
    }
    companion object {
        val featureKeys = listOf(FeaturePolicy.HEY_GOOGLE, FeaturePolicy.GMS, FeaturePolicy.GOOGLE_ENTRY,
            FeaturePolicy.NAVIGATION, FeaturePolicy.SEARCH_BAR, FeaturePolicy.LENS,
            FeaturePolicy.WALLET, FeaturePolicy.QUICK_ENTRIES)
    }
}

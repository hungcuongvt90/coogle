package io.github.zopulus.coogle.ui

import io.github.zopulus.coogle.FeaturePolicy

enum class CompanionState(val summary: String) {
    UNKNOWN("附属模块：开启时自动检测"),
    CHECKING("附属模块：正在检测安装状态…"),
    MISSING("附属模块：未安装"),
    DISABLED("附属模块：已禁用"),
    UPDATE_REQUIRED("附属模块：需要更新"),
    READY("附属模块：已安装并启用"),
    PENDING("附属模块：已安装，待重启完成挂载"),
    INSTALLING("附属模块：正在安装…")
}

enum class DialogKind { NONE, STATUS, INSTALL, DISABLED, INSTALLED, HIDE_ICON, ERROR }

data class CoogleState(
    val connected: Boolean = false,
    val features: Map<String, Boolean> = featureKeys.associateWith(FeaturePolicy::defaultValue),
    val enableHeyOnInstall: Boolean = false,
    val busy: Boolean = false,
    val assistant: String = "当前数字助理：读取中…",
    val companion: CompanionState = CompanionState.UNKNOWN,
    val iconHidden: Boolean = false,
    val dialog: DialogKind = DialogKind.NONE,
    val dialogText: String = "",
) {
    companion object {
        val featureKeys = listOf(FeaturePolicy.HEY_GOOGLE, FeaturePolicy.GMS, FeaturePolicy.GOOGLE_ENTRY,
            FeaturePolicy.NAVIGATION, FeaturePolicy.SEARCH_BAR, FeaturePolicy.LENS,
            FeaturePolicy.WALLET, FeaturePolicy.QUICK_ENTRIES)
    }
}

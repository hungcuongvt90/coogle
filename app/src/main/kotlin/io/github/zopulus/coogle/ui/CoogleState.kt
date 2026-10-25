package io.github.zopulus.coogle.ui

import io.github.zopulus.coogle.FeaturePolicy

enum class CompanionState(val summary: String) {
    UNKNOWN("Companion module: not checked"),
    MISSING("Companion module: not installed"),
    DISABLED("Companion module: installed but disabled"),
    UPDATE_REQUIRED("Companion module: update required"),
    READY("Companion module: installed and enabled"),
    PENDING("Companion module: reboot required")
}

enum class DialogKind { NONE, STATUS, INSTALLED, RESTART, HIDE_ICON, ERROR, ROOT_REQUIRED }

data class CoogleState(
    val connected: Boolean? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val busy: Boolean = false,
    val restarting: Boolean = false,
    val scopePending: Boolean = false,
    val assistant: String = "Current assistant: reading…",
    val companion: CompanionState = CompanionState.UNKNOWN,
    val companionChecking: Boolean = false,
    val companionError: String = "",
    val iconHidden: Boolean = false,
    val dialog: DialogKind = DialogKind.NONE,
    val dialogText: String = "",
) {
    fun beginCompanionInstall(): CoogleState = copy(busy = true, dialog = DialogKind.NONE)
    val navigationDescription: String get() = if (companion == CompanionState.MISSING)
        "Restore navigation settings; install the companion module from the active status card to enable Circle to Search live translation" else "Restore navigation settings"
    val heyAvailable: Boolean get() = companion == CompanionState.READY || companion == CompanionState.PENDING
    val heyDescription: String get() = if (companion == CompanionState.UNKNOWN || companion == CompanionState.MISSING)
        "Restore system-level Voice Match; install the companion module from the active status card" else "Restore system-level Voice Match"
    val companionSummary: String get() = if (companionError.isNotEmpty())
        "Check failed: $companionError" else companion.summary
    companion object {
        val featureKeys = listOf(FeaturePolicy.HEY_GOOGLE, FeaturePolicy.GMS, FeaturePolicy.GOOGLE_ENTRY,
            FeaturePolicy.NAVIGATION, FeaturePolicy.SEARCH_BAR, FeaturePolicy.LENS,
            FeaturePolicy.ACTION_BUTTON_LENS, FeaturePolicy.WALLET, FeaturePolicy.QUICK_ENTRIES)
    }
}

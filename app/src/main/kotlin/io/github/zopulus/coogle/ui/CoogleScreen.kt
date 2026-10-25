// Material component conventions follow tiann/KernelSU (GPL-3.0); see NOTICE.txt.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.zopulus.coogle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.zopulus.coogle.BuildConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.R

@Composable
fun CoogleScreen(
    state: CoogleState,
    onSetting: (String, Boolean) -> Unit,
    onAssistant: () -> Unit,
    onIconVisibility: (Boolean) -> Unit,
    onStatus: () -> Unit,
    onAbout: () -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onRestart: () -> Unit
) {
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TwoRowsTopAppBar(
                // Match KernelSU's LargeFlexibleTopAppBar title tokens; only the expanded row has a subtitle.
                title = { expanded -> Text("Coogle", style = if (expanded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge) },
                subtitle = { expanded -> if (expanded) Text("Make Chinese ColorOS more Google-like", style = MaterialTheme.typography.titleMedium) },
                collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
                expandedHeight = TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight,
                actions = {
                    val enabled = state.connected == true && !state.busy && !state.restarting && !state.scopePending
                    IconButton(onClick = onRestart, enabled = enabled) {
                        ControlIcon(ControlIcons.Restart, "Restart scopes", enabled)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = bar
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            // Same Surface, ListItem and StatusTag geometry as KernelSU HomeMaterial.
            val statusColor = when (state.connected) {
                true -> MaterialTheme.colorScheme.secondaryContainer
                false -> MaterialTheme.colorScheme.errorContainer
                null -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val statusContentColor = MaterialTheme.colorScheme.contentColorFor(statusColor)
            Surface(modifier = Modifier.fillMaxWidth(), color = statusColor,
                contentColor = statusContentColor, shape = MaterialTheme.shapes.large,
                onClick = onStatus) {
                ListItem(
                    leadingContent = {
                        Icon(when (state.connected) {
                            true -> Icons.Rounded.CheckCircle
                            false -> Icons.Rounded.HighlightOff
                            null -> Icons.Rounded.Extension
                        },
                            contentDescription = null)
                    },
                    trailingContent = {
                        Box(Modifier.padding(end = 4.dp).background(
                            MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))) {
                            Text("API 102", Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.labelSmallEmphasized,
                                color = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    overlineContent = null,
                    supportingContent = {
                        Text(state.companionSummary, style = MaterialTheme.typography.bodyMedium)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent,
                        contentColor = statusContentColor, leadingContentColor = statusContentColor,
                        trailingContentColor = statusContentColor,
                        supportingContentColor = statusContentColor.copy(alpha = 0.7f)),
                    elevation = ListItemDefaults.elevation(),
                    content = {
                        Text(when (state.connected) { true -> "Active"; false -> "Inactive"; null -> "Connecting…" },
                            style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                )
            }
            val available = state.connected == true && !state.scopePending
            Group("Assistant") {
                SettingRow(0, 3, Icons.Rounded.RecordVoiceOver, "Default assistant", state.assistant, onClick = onAssistant)
                ToggleRow(1, 3, Icons.Rounded.Mic, "Hey Google", state.heyDescription,
                    checked = state.features[FeaturePolicy.HEY_GOOGLE], enabled = available && !state.busy && state.heyAvailable,
                    onChange = { onSetting(FeaturePolicy.HEY_GOOGLE, it) })
                ToggleRow(2, 3, ImageVector.vectorResource(R.drawable.action_lens_color), "Google Lens", "Complete the Google Lens integration",
                    state.features[FeaturePolicy.LENS], available, { onSetting(FeaturePolicy.LENS, it) })
            }
            Group("System settings") {
                ToggleRow(0, 7, Icons.Rounded.TouchApp, "System navigation", state.navigationDescription,
                    state.features[FeaturePolicy.NAVIGATION], available, { onSetting(FeaturePolicy.NAVIGATION, it) })
                ToggleRow(1, 7, Icons.Rounded.Search, "Google search bar", "Show the Google search bar at the bottom of the launcher",
                    state.features[FeaturePolicy.SEARCH_BAR], available, { onSetting(FeaturePolicy.SEARCH_BAR, it) })
                ToggleRow(2, 7, ControlIcons.ActionButton, "Shortcut Google Lens", "Replace the shortcut AI scanner with Google Lens",
                    state.features[FeaturePolicy.ACTION_BUTTON_LENS], available, { onSetting(FeaturePolicy.ACTION_BUTTON_LENS, it) })
                ToggleRow(3, 7, Icons.Rounded.AccountBalanceWallet, "Google Wallet", "Open Google Wallet by double-pressing the power button while the screen is off",
                    state.features[FeaturePolicy.WALLET], available, { onSetting(FeaturePolicy.WALLET, it) })
                ToggleRow(4, 7, Icons.Rounded.DashboardCustomize, "Shortcuts and Control Center", "Restore native switches for lock-screen shortcuts and Control Center",
                    state.features[FeaturePolicy.QUICK_ENTRIES], available, { onSetting(FeaturePolicy.QUICK_ENTRIES, it) })
                ToggleRow(5, 7, Icons.Rounded.LockOpen, "Remove GMS restrictions", "Remove the China-region GMS restriction marker",
                    state.features[FeaturePolicy.GMS], available, { onSetting(FeaturePolicy.GMS, it) })
                ToggleRow(6, 7, Icons.Rounded.SettingsSuggest, "Show Google in Settings", "Always show Google services at the bottom of Settings",
                    state.features[FeaturePolicy.GOOGLE_ENTRY], available, { onSetting(FeaturePolicy.GOOGLE_ENTRY, it) })
            }
            Group("Other settings") {
                ToggleRow(0, 2, Icons.Rounded.VisibilityOff, "Hide launcher icon", "After hiding it, open Coogle from LSPosed",
                    state.iconHidden, true, onIconVisibility)
                SettingRow(1, 2, Icons.Rounded.Info, "About Coogle", "zopulus · ${BuildConfig.VERSION_NAME}", onClick = onAbout)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    CoogleDialog(state, onDialogConfirm, onDialogDismiss)
}

@Composable
private fun ControlIcon(vector: ImageVector, description: String? = null, enabled: Boolean = true) {
    Icon(vector, contentDescription = description, modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.38f))
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = { content() })
    }
}

@Composable
private fun SettingRow(index: Int, count: Int, icon: ImageVector, title: String, summary: String,
    onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null) {
    SegmentedListItem(
        modifier = modifier.fillMaxWidth(),
        shapes = ListItemDefaults.segmentedShapes(index, count).let {
            if (count == 1) it.copy(shape = MaterialTheme.shapes.large) else it
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        enabled = enabled,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
        trailingContent = trailing ?: { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        supportingContent = { Text(summary) },
        content = { Text(title) }
    )
}

@Composable
private fun ToggleRow(index: Int, count: Int, icon: ImageVector, title: String, summary: String,
    checked: Boolean?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(index, count, icon, title, summary, { if (checked != null) onChange(!checked) }, enabled && checked != null,
        Modifier.semantics { role = Role.Switch; stateDescription = when (checked) { true -> "On"; false -> "Off"; null -> "Reading" } }) {
        if (checked == null) {
            Box(Modifier.size(width = 52.dp, height = 32.dp).background(
                MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp)))
            return@SettingRow
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = if (checked || enabled) {
                { Icon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
            colors = SwitchDefaults.colors(
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

@Composable
private fun CoogleDialog(state: CoogleState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (state.dialog == DialogKind.NONE) return
    val title = when (state.dialog) {
        DialogKind.STATUS -> "Module status"
        DialogKind.RESTART -> "Restart scopes"
        DialogKind.INSTALLED -> "Installation complete"
        DialogKind.HIDE_ICON -> "Hide launcher icon?"
        DialogKind.ROOT_REQUIRED -> "Root permission required"
        else -> "Operation incomplete"
    }
    val message = when (state.dialog) {
        DialogKind.STATUS -> "Enable Coogle in LSPosed and select the recommended scopes. Restart the affected scopes after changing settings.\n\nThe companion module provides the Google system app, system-level Voice Match components, and Circle to Search live-translation configuration.\n\n${state.companionSummary}"
        DialogKind.RESTART -> "Restarting the selected scopes will close the applications enabled in LSPosed, and System UI may reload briefly. Reopen the affected applications afterward.\n\nSystem framework and Hey Google configuration changes require a device reboot. Root permission is required."
        DialogKind.INSTALLED -> if (state.companion == CompanionState.DISABLED)
            "The companion module is installed but disabled. Enable it in KernelSU, then reboot the device."
            else if (state.features[FeaturePolicy.HEY_GOOGLE] == true)
            "The companion module is installed. Reboot the device to mount it, then enable Voice Match in Google settings."
            else "The companion module is installed. Reboot the device to complete mounting."
        DialogKind.HIDE_ICON -> "After hiding the icon, open the app from Coogle's module settings entry in LSPosed."
        else -> state.dialogText
    }
    val confirm = when (state.dialog) {
        DialogKind.STATUS -> "Install companion module"
        DialogKind.RESTART -> "Restart scopes"
        DialogKind.HIDE_ICON -> "Hide"
        else -> "OK"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            if (state.dialog == DialogKind.INSTALLED) Icon(Icons.Outlined.Extension, null)
            else if (state.dialog == DialogKind.RESTART) ControlIcon(ControlIcons.Restart)
            else Icon(if (state.dialog == DialogKind.HIDE_ICON) Icons.Rounded.VisibilityOff else Icons.Rounded.Info, null)
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.busy && !state.restarting &&
                (state.dialog != DialogKind.STATUS || (state.connected == true && !state.scopePending && !state.companionChecking))) { Text(confirm) }
        },
        dismissButton = if (state.dialog in setOf(DialogKind.STATUS, DialogKind.RESTART, DialogKind.HIDE_ICON)) {
            { TextButton(onClick = onDismiss) { Text(if (state.dialog == DialogKind.STATUS) "Close" else "Cancel") } }
        } else null
    )
}

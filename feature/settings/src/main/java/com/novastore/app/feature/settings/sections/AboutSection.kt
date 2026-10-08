package com.novastore.app.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novastore.app.core.ui.R as UiR

/**
 * About — the REAL installed version (read from the package, never a
 * hard-coded string that goes stale), sources, device, privacy, license,
 * the Google disclaimer and the factory-reset entry point.
 */
@Composable
internal fun AboutSection(
    show: (String) -> Boolean,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    val (versionName, versionCode) = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            (info.versionName ?: "?") to code
        }.getOrDefault("?" to 0L)
    }

    SettingsSection(title = stringResource(UiR.string.settings_about)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Nova Store",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(androidx.compose.ui.graphics.Color(0xFF6965F1), androidx.compose.ui.graphics.Color(0xFFA556F7)),
                        ),
                    ),
                )
                Text(
                    stringResource(UiR.string.settings_about_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (show("about_version")) {
            AboutRow(
                icon = Icons.Filled.Info,
                title = stringResource(UiR.string.settings_about_version_label),
                value = stringResource(UiR.string.settings_about_version_value, versionName, versionCode),
            )
        }
        if (show("about_sources")) {
            AboutRow(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(UiR.string.settings_about_sources),
                value = stringResource(UiR.string.settings_about_sources_value),
            )
        }
        if (show("about_device")) {
            AboutRow(
                icon = Icons.Filled.Android,
                title = stringResource(UiR.string.settings_about_device),
                value = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} " +
                    "(API ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}",
            )
        }
        if (show("about_privacy")) {
            AboutRow(
                icon = Icons.Filled.Lock,
                title = stringResource(UiR.string.settings_about_privacy),
                value = stringResource(UiR.string.settings_privacy_note),
            )
        }
        if (show("about_license")) {
            AboutRow(
                icon = Icons.Filled.Code,
                title = stringResource(UiR.string.settings_about_license),
                // The full text ships inside the app: works offline and in regions
                // where gnu.org answers "Forbidden".
                value = stringResource(UiR.string.settings_gplayapi_credit),
                onClick = { showLicense = true },
            )
        }
        if (show("about_disclaimer")) {
            RowDivider()
            InfoRow(
                icon = Icons.Filled.Info,
                title = stringResource(UiR.string.settings_about_disclaimer),
                description = stringResource(UiR.string.settings_about_disclaimer_value),
            )
        }
        if (show("factory_defaults")) {
            AboutRow(
                icon = Icons.Filled.Restore,
                title = stringResource(UiR.string.settings_factory_defaults),
                value = stringResource(UiR.string.settings_factory_defaults_value),
            )
        }
        if (show("reset")) {
            AboutRow(
                icon = Icons.Filled.Delete,
                title = stringResource(UiR.string.settings_reset_title),
                value = stringResource(UiR.string.settings_reset_desc),
                onClick = { showReset = true },
            )
        }
    }

    if (showLicense) LicenseDialog(onDismiss = { showLicense = false })
    if (showReset) {
        ConfirmActionDialog(
            title = stringResource(UiR.string.settings_reset_title),
            message = stringResource(UiR.string.settings_reset_confirm),
            confirmLabel = stringResource(UiR.string.settings_reset_title),
            onConfirm = {
                showReset = false
                onReset()
            },
            onDismiss = { showReset = false },
        )
    }
}

@Composable
internal fun AboutRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Full GPL-3.0 text bundled with the app, scrollable, full screen. */
@Composable
internal fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching {
            context.resources.openRawResource(UiR.raw.gpl3).bufferedReader().use { it.readText() }
        }.getOrDefault("GNU General Public License v3.0")
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.cd_back))
                    }
                    Text("GNU GPL v3.0", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}
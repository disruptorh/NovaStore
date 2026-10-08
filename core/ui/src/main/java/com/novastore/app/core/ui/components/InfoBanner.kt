package com.novastore.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.novastore.app.core.ui.theme.NovaSpacing
import com.novastore.app.core.ui.theme.NovaShapes

/** Banner flavor. ERROR carries the account lock glyph + error colors. */
enum class InfoBannerKind {
    INFO,
    ERROR,
}

/**
 * Single shared info/error banner (P07 design tokens). Fully clickable row
 * (>=48dp) with optional dismiss + optional secondary hint line.
 */
@Composable
fun InfoBanner(
    text: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    kind: InfoBannerKind = InfoBannerKind.INFO,
    hint: String? = null,
) {
    val container = if (kind == InfoBannerKind.ERROR) {
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon: ImageVector = if (kind == InfoBannerKind.ERROR) Icons.Filled.Lock else Icons.Filled.Info
    val clickableModifier = if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier
    Surface(
        color = container.first,
        shape = NovaShapes.Card,
        modifier = modifier
            .then(clickableModifier)
            .fillMaxWidth()
            .heightIn(min = NovaSpacing.XXXL),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NovaSpacing.LG, vertical = NovaSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NovaSpacing.MD),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = container.second,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = container.second,
                )
                if (hint != null) {
                    Spacer(Modifier.height(NovaSpacing.SM))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = container.second.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
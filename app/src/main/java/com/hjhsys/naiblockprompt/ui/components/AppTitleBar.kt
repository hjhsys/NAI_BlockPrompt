package com.hjhsys.naiblockprompt.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.hjhsys.naiblockprompt.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

data class AppTitleMenuItem(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val showLabel: Boolean = false,
)

@Composable
fun AppTitleBar(
    @StringRes title: Int,
    menuItems: List<AppTitleMenuItem> = emptyList(),
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    trailingSubtitle: String? = null,
    directAction: AppTitleMenuItem? = null,
    secondaryDirectAction: AppTitleMenuItem? = null,
    trailingOverline: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(shadowElevation = 4.dp) {
        Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (trailingOverline != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                            subtitle?.let {
                                Text(
                                    it,
                                    modifier = if (onSubtitleClick == null) Modifier else Modifier.clickable(onClick = onSubtitleClick),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Column(Modifier.weight(1.35f), horizontalAlignment = Alignment.End) {
                            Text(trailingOverline, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
                            trailingSubtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1) }
                        }
                    }
                } else {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                if (subtitle != null || trailingSubtitle != null) Row(Modifier.fillMaxWidth()) {
                    subtitle?.let {
                        Text(
                            it,
                            modifier = Modifier.weight(1f).then(if (onSubtitleClick == null) Modifier else Modifier.clickable(onClick = onSubtitleClick)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    trailingSubtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                }
                }
            }
            trailingContent?.invoke()
            directAction?.let { action ->
                if (action.showLabel) TextButton(onClick = action.onClick, enabled = action.enabled) {
                    Icon(action.icon, contentDescription = null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 2.dp))
                    Text(stringResource(action.label), maxLines = 1)
                } else IconButton(onClick = action.onClick, enabled = action.enabled) { Icon(action.icon, contentDescription = stringResource(action.label)) }
            }
            secondaryDirectAction?.let { action ->
                IconButton(onClick = action.onClick, enabled = action.enabled) {
                    Icon(action.icon, contentDescription = stringResource(action.label))
                }
            }
            if (menuItems.isNotEmpty()) {
                androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    menuItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(stringResource(item.label)) },
                            leadingIcon = { Icon(item.icon, contentDescription = null) },
                            enabled = item.enabled,
                            onClick = { menuExpanded = false; item.onClick() },
                        )
                    }
                }
                }
            }
        }
        bottomContent?.let { content ->
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) { content() }
        }
        }
    }
}

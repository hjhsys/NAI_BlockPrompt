package com.hjhsys.naiblockprompt.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hjhsys.naiblockprompt.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

data class AppTitleMenuItem(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun AppTitleBar(
    @StringRes title: Int,
    menuItems: List<AppTitleMenuItem> = emptyList(),
    onBack: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
    }
}

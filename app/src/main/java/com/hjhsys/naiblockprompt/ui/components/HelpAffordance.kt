package com.hjhsys.naiblockprompt.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hjhsys.naiblockprompt.R

@Composable
fun HelpAffordance(
    @StringRes title: Int,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable(title, body) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(title))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(body)) },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

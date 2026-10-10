package com.hjhsys.naiblockprompt.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hjhsys.naiblockprompt.R

data class FolderChoice(val id: String, val name: String, val itemCount: Int = 0)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderSelectionDialog(
    rootLabel: String,
    folders: List<FolderChoice>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
    showAll: Boolean = false,
    allSelected: Boolean = false,
    onSelectAll: () -> Unit = {},
    onDeleteFolder: ((FolderChoice) -> Unit)? = null,
    onRenameFolder: ((FolderChoice, String) -> Unit)? = null,
    noFolderSelected: Boolean = selectedId == null,
) {
    var addingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var managedFolder by remember { mutableStateOf<FolderChoice?>(null) }
    var renameName by remember { mutableStateOf("") }
    var confirmingDelete by remember { mutableStateOf<FolderChoice?>(null) }

    confirmingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = { Text(stringResource(R.string.delete_folder_message, folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder?.invoke(folder)
                    confirmingDelete = null
                    managedFolder = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }

    managedFolder?.let { folder ->
        val duplicateName = folders.any { it.id != folder.id && it.name.equals(renameName.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { managedFolder = null },
            title = { Text(stringResource(R.string.folder_management)) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.folder)) },
                    singleLine = true,
                    supportingText = if (duplicateName) ({ Text(stringResource(R.string.folder_name_exists)) }) else null,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameName.isNotBlank() && !duplicateName,
                    onClick = {
                        onRenameFolder?.invoke(folder, renameName.trim())
                        managedFolder = null
                    },
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                Row {
                    if (onDeleteFolder != null) {
                        TextButton(onClick = { confirmingDelete = folder }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { managedFolder = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_folder)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(rootLabel) },
                        leadingContent = { Icon(Icons.Default.Folder, null) },
                        trailingContent = { if (allSelected) Icon(Icons.Default.Check, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = if (showAll) Modifier.clickable(onClick = onSelectAll) else Modifier,
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.no_folder)) },
                        leadingContent = { Icon(Icons.Default.SubdirectoryArrowRight, null) },
                        trailingContent = { if (noFolderSelected) Icon(Icons.Default.Check, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(start = 24.dp).clickable { onSelect(null) },
                    )
                }
                items(folders, key = { it.id }) { folder ->
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Default.SubdirectoryArrowRight, null) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Text(
                                            folder.itemCount.toString(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                    if (selectedId == folder.id) Icon(Icons.Default.Check, null)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.weight(1f).combinedClickable(
                                onClick = { onSelect(folder.id) },
                                onLongClick = if (onDeleteFolder != null || onRenameFolder != null) ({
                                    renameName = folder.name
                                    managedFolder = folder
                                }) else null,
                            ),
                        )
                    }
                }
                item {
                    if (addingFolder) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newFolderName,
                                onValueChange = { newFolderName = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.new_folder)) },
                                singleLine = true,
                            )
                            IconButton(
                                enabled = newFolderName.isNotBlank(),
                                onClick = {
                                    onCreateFolder(newFolderName.trim())
                                    newFolderName = ""
                                    addingFolder = false
                                },
                            ) { Icon(Icons.Default.Check, stringResource(R.string.add)) }
                            IconButton(onClick = { newFolderName = ""; addingFolder = false }) {
                                Icon(Icons.Default.Close, stringResource(R.string.cancel))
                            }
                        }
                    } else {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.new_folder)) },
                            leadingContent = { Icon(Icons.Default.CreateNewFolder, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { addingFolder = true },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

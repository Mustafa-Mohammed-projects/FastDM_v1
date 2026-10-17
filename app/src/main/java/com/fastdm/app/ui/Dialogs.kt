package com.fastdm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.fastdm.app.R
import com.fastdm.app.core.DownloadItem
import com.fastdm.app.core.DownloadRequest
import com.fastdm.app.core.displayUrl
import com.fastdm.app.core.extractUrl
import com.fastdm.app.core.formatBytes

private fun cleanUrl(raw: String): String {
    val t = raw.trim()
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
}

@Composable
fun AddLinkDialog(prefill: String, onAdd: (url: String, name: String) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(prefill.ifBlank { extractUrl(clipboard.getText()?.text ?: "") ?: "" }) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.dlg_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.dlg_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { url = extractUrl(it) ?: it.trim() }
                        }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.dlg_paste)) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dlg_file_name)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = { onAdd(cleanUrl(url), name.trim()) }) {
                Text(stringResource(R.string.dlg_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_cancel)) } }
    )
}

@Composable
fun ConfirmDownloadDialog(req: DownloadRequest, onConfirm: (name: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(req.fileName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.dlg_confirm_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dlg_file_name)) },
                    maxLines = 3
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (req.size > 0) stringResource(R.string.dlg_size, formatBytes(req.size))
                    else stringResource(R.string.dlg_size_unknown),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    displayUrl(req.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) { Text(stringResource(R.string.dlg_download)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_cancel)) } }
    )
}

@Composable
fun RefreshLinkDialog(
    item: DownloadItem,
    onUpdate: (String) -> Unit,
    onCapture: () -> Unit,
    onDismiss: () -> Unit
) {
    var link by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.dlg_refresh_title)) },
        text = {
            Column {
                Text(item.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.dlg_refresh_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text(stringResource(R.string.dlg_new_link)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { link = extractUrl(it) ?: it.trim() }
                        }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.dlg_paste)) }
                    }
                )
                TextButton(onClick = onCapture, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.dlg_capture))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = link.isNotBlank(), onClick = { onUpdate(cleanUrl(link)) }) {
                Text(stringResource(R.string.dlg_update))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_cancel)) } }
    )
}

@Composable
fun DeleteDialog(item: DownloadItem, onConfirm: (deleteFile: Boolean) -> Unit, onDismiss: () -> Unit) {
    var alsoFile by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.dlg_delete_title)) },
        text = {
            Column {
                Text(item.fileName, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                if (item.savedUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = alsoFile, onCheckedChange = { alsoFile = it })
                        Text(stringResource(R.string.dlg_delete_file))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoFile) }) {
                Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_cancel)) } }
    )
}

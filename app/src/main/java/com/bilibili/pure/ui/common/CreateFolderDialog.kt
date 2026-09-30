package com.bilibili.pure.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateFolderDialog(
    creating: Boolean,
    onConfirm: (title: String, intro: String, privacy: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("新建收藏夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = intro,
                    onValueChange = { intro = it },
                    label = { Text("简介（可选）") },
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("私密收藏夹", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        enabled = !creating
                    )
                }
                if (creating) {
                    Text(
                        text = "创建中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, intro, if (isPrivate) 1 else 0) },
                enabled = title.isNotBlank() && !creating
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text("取消")
            }
        }
    )
}

package com.warvis.android.ui.backlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.warvis.android.data.model.BacklogBucket
import com.warvis.android.data.model.BacklogBucketId
import com.warvis.android.data.model.BacklogItem
import com.warvis.android.data.model.BacklogState
import com.warvis.android.ui.components.ScreenScaffold
import com.warvis.android.ui.components.SectionTitle
import com.warvis.android.ui.components.WarvisOutlinedCard

@Composable
fun BacklogScreen(
    state: BacklogState,
    onAddItem: (BacklogBucketId, String) -> Unit,
    onEditItem: (BacklogItem, String) -> Unit,
    onDeleteItem: (BacklogItem) -> Unit,
) {
    ScreenScaffold(
        title = "Backlog",
        subtitle = "Activities suggested by Doom Shield. Rotates Career → Fitness → Thagudu.",
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.buckets.forEachIndexed { index, bucket ->
                BacklogBucketCard(
                    bucket = bucket,
                    isCurrent = index == state.currentBucketIndex,
                    onAddItem = { title -> onAddItem(bucket.id, title) },
                    onEditItem = onEditItem,
                    onDeleteItem = onDeleteItem,
                )
            }
        }
    }
}

@Composable
private fun BacklogBucketCard(
    bucket: BacklogBucket,
    isCurrent: Boolean,
    onAddItem: (String) -> Unit,
    onEditItem: (BacklogItem, String) -> Unit,
    onDeleteItem: (BacklogItem) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var addText by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<BacklogItem?>(null) }
    var editText by remember { mutableStateOf("") }
    var deletingItem by remember { mutableStateOf<BacklogItem?>(null) }
    val focusManager = LocalFocusManager.current

    WarvisOutlinedCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle("${bucket.displayName} (${bucket.items.size})")
                if (isCurrent) {
                    Text(
                        text = "Next Doom Shield pick is from here",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "Show")
            }
        }

        if (expanded) {
            HorizontalDivider()

            if (bucket.items.isEmpty()) {
                Text(
                    text = "No activities yet — add one below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                bucket.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        )
                        TextButton(onClick = {
                            editingItem = item
                            editText = item.title
                        }) {
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { deletingItem = item }) {
                            Text(
                                text = "Delete",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = addText,
                    onValueChange = { addText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("New activity…") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        if (addText.isNotBlank()) {
                            onAddItem(addText.trim())
                            addText = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = addText.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
        }
    }

    editingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Edit activity") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editText.isNotBlank()) {
                            onEditItem(item, editText)
                            editingItem = null
                        }
                    },
                    enabled = editText.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingItem = null }) { Text("Cancel") }
            },
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete activity?") },
            text = {
                Text(
                    text = "\"${item.title}\" will be permanently removed from ${bucket.displayName}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem(item)
                        deletingItem = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingItem = null }) { Text("Cancel") }
            },
        )
    }
}

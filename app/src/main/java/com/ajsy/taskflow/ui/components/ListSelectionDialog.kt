@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ajsy.taskflow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ajsy.taskflow.db.SavedTaskList

@Composable
fun ListSelectionDialog(
    title: String,
    showInputField: Boolean = false,
    initialInputValue: String = "",
    savedLists: List<SavedTaskList>,
    onListSelected: (SavedTaskList) -> Unit,
    showActionButtons: Boolean = false,
    onLoadList: (SavedTaskList) -> Unit = {},
    onAppendList: (SavedTaskList) -> Unit = {},
    onDeleteList: (SavedTaskList) -> Unit = {},
    onSave: (String, Int?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    var listNameInput by remember { mutableStateOf(initialInputValue) }
    var selectedListId by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            when {
                showInputField -> {
                    Button(
                        onClick = {
                            if (listNameInput.isNotBlank()) {
                                onSave(listNameInput, selectedListId)
                            }
                        },
                    ) {
                        Text("Save")
                    }
                }
                showActionButtons -> {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        },
        dismissButton = {
            if (showInputField || !showActionButtons) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showInputField) {
                    TextField(
                        value = listNameInput,
                        onValueChange = { listNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                if (savedLists.isEmpty()) {
                    Text(
                        text = "No saved lists found",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    if (showInputField) {
                        Text(
                            text = "Or select a list to overwrite:",
                            modifier = Modifier.padding(start = 0.dp, top = 16.dp, end = 0.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    LazyColumn {
                        items(savedLists) { savedList ->
                            ListSelectionRow(
                                savedList = savedList,
                                isSelected = selectedListId == savedList.id,
                                showInputField = showInputField,
                                showActionButtons = showActionButtons,
                                onSelect = {
                                    selectedListId = savedList.id
                                    listNameInput = savedList.name
                                    onListSelected(savedList)
                                },
                                onLoad = { onLoadList(savedList) },
                                onAppend = { onAppendList(savedList) },
                                onDelete = { onDeleteList(savedList) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ListSelectionRow(
    savedList: SavedTaskList,
    isSelected: Boolean,
    showInputField: Boolean,
    showActionButtons: Boolean,
    onSelect: () -> Unit,
    onLoad: () -> Unit,
    onAppend: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = savedList.name,
                style = if (isSelected) {
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            )
        }
        if (showActionButtons) {
            TextButton(onClick = onLoad) { Text("Load") }
            TextButton(onClick = onAppend) { Text("Append") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

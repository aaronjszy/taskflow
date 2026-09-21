package com.ajsy.taskflow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ajsy.taskflow.db.SavedTaskList
import com.ajsy.taskflow.ui.TaskFlowViewModel
import com.ajsy.taskflow.ui.state.DialogState

@Composable
fun FloatingActionMenu(
    model: TaskFlowViewModel,
    uiState: DialogState,
    onUiStateChange: (DialogState) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val soundEnabled by model.soundEnabled.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    onUiStateChange(uiState.updateShowMenu(true))
                    model.loadSavedLists()
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.TwoTone.PlayArrow, contentDescription = "Menu")
            }

            DropdownMenu(
                expanded = uiState.showMenu,
                onDismissRequest = { onUiStateChange(uiState.updateShowMenu(false)) },
            ) {
                DropdownMenuItem(
                    text = { Text("Save as Template") },
                    onClick = { onUiStateChange(uiState.updateShowMenu(false).openSaveDialog()) },
                )
                DropdownMenuItem(
                    text = { Text("Load Template") },
                    onClick = { onUiStateChange(uiState.updateShowMenu(false).openLoadDialog()) },
                )
                DropdownMenuItem(
                    text = { Text("Organize by Schedule") },
                    onClick = {
                        onUiStateChange(uiState.updateShowMenu(false))
                        model.organizeTasksByScheduledTime()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Reset All Timers") },
                    onClick = {
                        onUiStateChange(uiState.updateShowMenu(false))
                        model.resetAllTimers()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy to Clipboard") },
                    onClick = {
                        onUiStateChange(uiState.updateShowMenu(false))
                        val text = model.formatTaskListForClipboard()
                        clipboardManager.setText(AnnotatedString(text))
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (soundEnabled) "Sound: On" else "Sound: Off") },
                    onClick = {
                        onUiStateChange(uiState.updateShowMenu(false))
                        model.toggleSound()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Clear List") },
                    onClick = {
                        onUiStateChange(uiState.updateShowMenu(false))
                        showClearConfirm = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Theme") },
                    onClick = { onUiStateChange(uiState.updateShowMenu(false).openThemeDialog()) },
                )
            }
        }

        if (uiState.showSaveDialog) {
            val savedLists by model.savedLists.collectAsState()
            ListSelectionDialog(
                title = "Save As Template",
                showInputField = true,
                initialInputValue = uiState.listNameInput,
                savedLists = savedLists,
                onListSelected = { savedList ->
                    onUiStateChange(uiState.updateListNameInput(savedList.name))
                },
                onSave = { listName, id ->
                    if (id != null) {
                        model.updateSavedList(id, listName)
                    } else {
                        model.saveCurrentList(listName)
                    }
                    onUiStateChange(uiState.closeAllDialogs().resetListInputs())
                    model.loadSavedLists()
                },
                onDismiss = {
                    onUiStateChange(uiState.closeAllDialogs().resetListInputs())
                },
            )
        }

        if (uiState.showLoadDialog) {
            val savedLists by model.savedLists.collectAsState()
            ListSelectionDialog(
                title = "Manage Templates",
                showActionButtons = true,
                savedLists = savedLists,
                onListSelected = {},
                onLoadList = { savedList ->
                    model.loadSavedList(savedList.id)
                    onUiStateChange(uiState.closeAllDialogs())
                },
                onAppendList = { savedList ->
                    model.appendSavedList(savedList.id)
                    onUiStateChange(uiState.closeAllDialogs())
                },
                onDeleteList = { savedList ->
                    onUiStateChange(uiState.openDeleteConfirmDialog(savedList))
                },
                onDismiss = {
                    onUiStateChange(uiState.closeAllDialogs())
                },
            )
        }

        if (uiState.showThemeDialog) {
            ThemeSelectionDialog(
                onDismiss = { onUiStateChange(uiState.closeAllDialogs()) },
            )
        }

        if (uiState.showDeleteConfirmDialog && uiState.listToDelete != null) {
            DeleteConfirmationDialog(
                listName = uiState.listToDelete.name,
                onConfirm = {
                    model.deleteSavedList(uiState.listToDelete.id)
                    onUiStateChange(uiState.closeAllDialogs().resetListInputs())
                },
                onDismiss = {
                    onUiStateChange(uiState.closeAllDialogs().resetListInputs())
                },
            )
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear List") },
                text = { Text("Remove all tasks? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = { model.clearAllTasks(); showClearConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }
}

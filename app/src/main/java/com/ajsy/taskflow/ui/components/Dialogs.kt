package com.ajsy.taskflow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ajsy.taskflow.ui.theme.AppTheme
import com.ajsy.taskflow.ui.theme.rememberThemeManager

@Composable
fun ThemeSelectionDialog(onDismiss: () -> Unit) {
    val themeManager = rememberThemeManager()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Select Theme") },
        text = {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                AppTheme.values().forEach { appTheme ->
                    val label = when (appTheme) {
                        AppTheme.LIGHT -> "Light Theme"
                        AppTheme.DARK -> "Dark Theme"
                        AppTheme.E_INK -> "E-Ink Theme (Grayscale)"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                themeManager.setTheme(appTheme)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (themeManager.currentTheme == appTheme) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun DeleteConfirmationDialog(
    listName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            Text(
                text = "Are you sure you want to delete the list \"$listName\"? This action cannot be undone.",
            )
        },
    )
}

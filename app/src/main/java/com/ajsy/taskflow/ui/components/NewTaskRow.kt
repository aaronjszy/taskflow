package com.ajsy.taskflow.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.ajsy.taskflow.ui.TaskFlowViewModel
import com.ajsy.taskflow.ui.theme.rememberThemeConfig
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewTaskRow(
    model: TaskFlowViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val newItemName by model.newItemName.collectAsState()
    val newItemTime by model.newItemTime.collectAsState()
    val addItemMode by model.addItemMode.collectAsState()
    val cursorPositionState by model.cursorPositionState.collectAsState()
    val editingTask by model.editingTask.collectAsState()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editingTask) {
        if (editingTask != null) {
            focusRequester.requestFocus()
        }
    }

    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        TextField(
            value = newItemName,
            onValueChange = { model.updateNewItemName(it) },
            label = { Text("task", maxLines = 1, fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier
                .weight(0.70f)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onDone = {
                    model.addItem()
                    if (addItemMode == "push") {
                        model.viewModelScope.launch { listState.scrollToItem(0) }
                    }
                    focusRequester.requestFocus()
                },
            ),
        )
        TextField(
            value = newItemTime,
            onValueChange = { model.updateNewItemTime(it) },
            label = {
                Text(
                    "minutes",
                    maxLines = 1,
                    fontSize = 14.sp,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            modifier = Modifier.weight(0.25f),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    model.addItem()
                    if (addItemMode == "push") {
                        model.viewModelScope.launch { listState.scrollToItem(0) }
                    }
                    focusRequester.requestFocus()
                },
            ),
        )
        val cursorIcon = when (cursorPositionState) {
            model.CURSOR_TOP -> "⬆"
            model.CURSOR_BOTTOM -> "⬇"
            else -> "↕" // CURSOR_CUSTOM
        }
        Text(
            text = cursorIcon,
            modifier = Modifier
                .weight(0.05f)
                .align(Alignment.CenterVertically)
                .clickable { model.toggleCursorPosition() },
        )
    }
}

@Composable
fun CursorRow(
    model: TaskFlowViewModel,
    modifier: Modifier = Modifier,
) {
    val addItemMode by model.addItemMode.collectAsState()
    val haptic = LocalHapticFeedback.current
    val themeConfig = rememberThemeConfig()
    var dragOffsetY by remember { mutableStateOf(0f) }

    val cursorColor = if (addItemMode == "below_cursor") {
        themeConfig.custom.aboveColor
    } else {
        themeConfig.custom.belowColor
    }

    val arrowText = if (addItemMode == "above_cursor") "▲" else "▼"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .drawBehind {
                drawRoundRect(
                    color = cursorColor,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
            .border(1.dp, Color.White, RoundedCornerShape(3.dp))
            .clickable { model.switchAddItemMode() }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { _, dragAmount ->
                        dragOffsetY += dragAmount.y
                        if (dragOffsetY > 130f) {
                            model.moveCursorDownAsync()
                            dragOffsetY = 0f
                        } else if (dragOffsetY < -130f) {
                            model.moveCursorUpAsync()
                            dragOffsetY = 0f
                        }
                    },
                )
            },
    ) {
        Text(
            text = arrowText,
            color = Color.Black,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

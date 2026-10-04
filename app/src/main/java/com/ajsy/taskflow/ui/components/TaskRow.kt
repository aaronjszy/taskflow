package com.ajsy.taskflow.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.ajsy.taskflow.ui.TaskFlowViewModel
import com.ajsy.taskflow.ui.TaskViewModel
import com.ajsy.taskflow.ui.theme.rememberThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun TimeDisplay(
    timeStr: String,
    textStyle: TextStyle,
    isRunning: Boolean,
    accentColor: Color,
) {
    if (timeStr.contains(" + ")) {
        val parts = timeStr.split(" + ")
        val bufferTime = parts[0]
        val taskTime = parts[1]
        Row(
            modifier = if (isRunning) Modifier.fillMaxWidth() else Modifier,
            horizontalArrangement = if (isRunning) Arrangement.Center else Arrangement.Start,
        ) {
            Text(text = bufferTime, style = textStyle.copy(color = accentColor))
            Text(text = " + ", style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface))
            Text(text = taskTime, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface))
        }
    } else {
        Text(
            text = timeStr,
            modifier = if (isRunning) Modifier.fillMaxWidth() else Modifier,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (isRunning) TextAlign.Center else null,
            style = textStyle,
        )
    }
}

@Composable
fun TaskRow(
    model: TaskFlowViewModel,
    task: TaskViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    if (task.name.startsWith(">>>")) {
        GroupHeaderRow(model = model, task = task, modifier = modifier)
        return
    }
    if (task.name.startsWith("<<<")) {
        GroupTerminatorRow(task = task, modifier = modifier)
        return
    }

    var showMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val timeRange = model.calcTimeRange(task)
    // When early (bufferSeconds > 0), timeRange.startTime == fixedStartTime, so subtract
    // buffer to get the natural sequence position. When late (bufferSeconds <= 0),
    // timeRange.startTime is already priorTaskEndTime — use it directly.
    val naturalStartTime = if (task.bufferSeconds > 0) {
        timeRange.startTime.minusSeconds(task.bufferSeconds)
    } else {
        timeRange.startTime
    }
    val fixedTimeDiff = task.formatFixedTimeDiff(naturalStartTime)
    val diffSeconds = task.fixedTimeDiffSeconds(naturalStartTime)
    val isLate = diffSeconds != null && diffSeconds < -60 && !task.taskTimerRunning
    val isVeryEarly = diffSeconds != null && diffSeconds > 900

    if (task.taskTimerRunning) {
        KeepScreenOn()
    }

    val themeConfig = rememberThemeConfig()
    val successColor = themeConfig.custom.success
    val lightSuccessColor = themeConfig.custom.lightSuccess
    val warningColor = themeConfig.custom.warning
    val errorColor = themeConfig.custom.error
    val lateErrorColor = themeConfig.custom.lateError
    val darkErrorColor = themeConfig.custom.darkError
    val darkSuccessColor = themeConfig.custom.darkSuccess

    val progress = task.calculateProgress()

    val progressBarColor = when {
        progress.taskProgress > 0.8f && progress.taskProgress < 1.0f -> warningColor
        progress.taskProgress >= 1.0f -> errorColor
        else -> successColor
    }

    val taskBackgroundColor = when {
        isLate -> lateErrorColor
        else -> MaterialTheme.colorScheme.surface
    }

    val (borderColor, borderWidth, textStyle, minHeight) = if (task.taskTimerRunning) {
        TaskRowStyle(
            borderColor = themeConfig.custom.border,
            borderWidth = 3.dp,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            ),
            minHeight = 100.dp,
        )
    } else {
        TaskRowStyle(
            borderColor = themeConfig.custom.border,
            borderWidth = 1.dp,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
            ),
            minHeight = 0.dp,
        )
    }

    val defer = SwipeAction(
        onSwipe = {
            focusManager.clearFocus()
            val wasTopTask = task.position == 0
            model.deferTaskAsync(task)
            if (wasTopTask) {
                model.viewModelScope.launch {
                    listState.scrollToItem(0)
                }
            }
        },
        icon = rememberVectorPainter(Icons.TwoTone.PlayArrow),
        background = Color.Yellow,
    )

    val delete = SwipeAction(
        onSwipe = { focusManager.clearFocus(); model.deleteTaskAsync(task) },
        icon = rememberVectorPainter(Icons.TwoTone.Delete),
        background = Color.Red,
    )

    val playPause = SwipeAction(
        onSwipe = {
            focusManager.clearFocus()
            model.toggleTimer(task)
            model.viewModelScope.launch {
                delay(50.milliseconds)
                listState.scrollToItem(0)
            }
        },
        icon = rememberVectorPainter(Icons.TwoTone.PlayArrow),
        background = successColor,
    )

    val startActions = if (task.name.startsWith("$")) listOf(defer) else listOf(delete)

    SwipeableActionsBox(
        modifier = modifier.padding(bottom = 0.dp),
        startActions = startActions,
        endActions = listOf(playPause),
        swipeThreshold = 40.dp,
    ) {
        val haptic = LocalHapticFeedback.current
        var dragOffsetY by remember { mutableStateOf(0f) }

        val rowModifier = Modifier
            .defaultMinSize(minHeight = minHeight)
            .drawBehind {
                drawRoundRect(
                    color = taskBackgroundColor,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                )
                if (progress.bufferProgress > 0f) {
                    drawRoundRect(
                        color = lightSuccessColor,
                        size = Size(size.width * progress.bufferProgress, size.height),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    )
                }
                if (progress.taskProgress > 0f) {
                    drawRoundRect(
                        color = progressBarColor,
                        size = Size(size.width * progress.taskProgress, size.height),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    )
                }
            }
            .border(borderWidth, borderColor, RoundedCornerShape(10))
            .padding(5.dp)
            .clickable {
                focusManager.clearFocus()
                if (task.name.startsWith(">>>")) {
                    if (task.name.contains("[folded]")) model.unfold(task) else model.fold(task)
                } else {
                    showMenu = true
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { _, dragAmount ->
                        dragOffsetY += dragAmount.y
                        if (dragOffsetY > 100f) {
                            model.moveTaskDownAsync(task)
                            dragOffsetY = 0f
                        }
                        if (dragOffsetY < -100f) {
                            model.moveTaskUpAsync(task, false)
                            dragOffsetY = 0f
                        }
                    },
                )
            }

        Row(modifier = rowModifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (task.taskTimerRunning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = task.getDisplayName(),
                                    style = textStyle,
                                    textAlign = TextAlign.Center,
                                )
                                TimeDisplay(
                                    timeStr = task.formatTimeRemaining(),
                                    textStyle = textStyle,
                                    isRunning = true,
                                    accentColor = darkSuccessColor,
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = task.getDisplayName(),
                                modifier = Modifier.weight(1f),
                                style = textStyle,
                            )
                            TimeDisplay(
                                timeStr = task.formatTimeRemaining(),
                                textStyle = textStyle,
                                isRunning = false,
                                accentColor = darkSuccessColor,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (fixedTimeDiff != null && (isLate || isVeryEarly)) {
                            val label = "scheduled $fixedTimeDiff"
                            val labelColor = if (isLate) darkErrorColor else darkSuccessColor
                            Text(
                                text = label,
                                color = labelColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    val menuItemHeight = 40.dp
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { showMenu = false; model.startEditing(task) },
                            modifier = Modifier.height(menuItemHeight),
                        )
                        DropdownMenuItem(
                            text = { Text("Reset Timer") },
                            onClick = { showMenu = false; model.resetTaskTimer(task) },
                            modifier = Modifier.height(menuItemHeight),
                        )
                        DropdownMenuItem(
                            text = { Text("+5 min") },
                            onClick = {
                                showMenu = false
                                model.addFiveMinutes(task)
                            },
                            modifier = Modifier.height(menuItemHeight),
                        )
                        DropdownMenuItem(
                            text = { Text("Move to List") },
                            onClick = {
                                showMenu = false
                                model.loadSavedLists()
                                showMoveDialog = true
                            },
                            modifier = Modifier.height(menuItemHeight),
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Top") },
                            onClick = {
                                showMenu = false
                                model.viewModelScope.launch {
                                    model.moveTaskToTopAsync(task)
                                }
                            },
                            modifier = Modifier.height(menuItemHeight),
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Bottom") },
                            onClick = {
                                showMenu = false
                                model.viewModelScope.launch {
                                    val wasTopTask = task.position == 0
                                    model.moveTaskToBottomAsync(task)
                                    if (wasTopTask) {
                                        listState.scrollToItem(0)
                                    }
                                }
                            },
                            modifier = Modifier.height(menuItemHeight),
                        )
                    }
                }
            }
        }
    }

    if (showMoveDialog) {
        val savedLists by model.savedLists.collectAsState()
        ListSelectionDialog(
            title = "Move to List",
            showInputField = true,
            savedLists = savedLists,
            onListSelected = { savedList ->
                model.moveTaskToSavedList(task, savedList.id)
                showMoveDialog = false
            },
            onSave = { listName, id ->
                if (id != null) {
                    model.moveTaskToSavedList(task, id)
                } else if (listName.isNotBlank()) {
                    model.viewModelScope.launch {
                        val newId = model.createNewList(listName)
                        model.moveTaskToSavedList(task, newId)
                    }
                }
                showMoveDialog = false
            },
            onDismiss = { showMoveDialog = false },
        )
    }
}

@Composable
private fun GroupHeaderRow(model: TaskFlowViewModel, task: TaskViewModel, modifier: Modifier = Modifier) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val themeConfig = rememberThemeConfig()
    val isFolded = task.name.contains("[folded]")
    val displayName = task.name
        .removePrefix(">>>")
        .removeSuffix("[folded]")
        .trim()
        .split("§").first()
        .trim()
    val chevron = if (isFolded) "▶" else "▼"

    var dragOffsetY by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    val delete = SwipeAction(
        onSwipe = { focusManager.clearFocus(); model.deleteGroupAsync(task) },
        icon = rememberVectorPainter(Icons.TwoTone.Delete),
        background = Color.Red,
    )

    SwipeableActionsBox(
        modifier = modifier,
        startActions = listOf(delete),
        swipeThreshold = 40.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                .border(1.dp, themeConfig.custom.border, RoundedCornerShape(6.dp))
                .clickable {
                    focusManager.clearFocus()
                    if (isFolded) model.unfold(task) else model.fold(task)
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDrag = { _, dragAmount ->
                            dragOffsetY += dragAmount.y
                            if (dragOffsetY > 100f) {
                                model.moveGroupDownAsync(task)
                                dragOffsetY = 0f
                            } else if (dragOffsetY < -100f) {
                                model.moveGroupUpAsync(task)
                                dragOffsetY = 0f
                            }
                        },
                    )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayName,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = chevron,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 14.sp,
                ),
            )
        }
    }
}

@Composable
private fun GroupTerminatorRow(task: TaskViewModel, modifier: Modifier = Modifier) {
    val displayName = task.name.removePrefix("<<<").trim()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = displayName,
            style = TextStyle(
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
            ),
        )
    }
}

private data class TaskRowStyle(
    val borderColor: Color,
    val borderWidth: androidx.compose.ui.unit.Dp,
    val textStyle: TextStyle,
    val minHeight: androidx.compose.ui.unit.Dp,
)

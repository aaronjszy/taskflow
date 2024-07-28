package com.ajsy.taskflow

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.ajsy.taskflow.db.TaskDatabase
import com.ajsy.taskflow.db.TaskRepository
import com.ajsy.taskflow.ui.TaskFlowViewModel
import com.ajsy.taskflow.ui.TaskState
import com.ajsy.taskflow.ui.theme.TaskflowTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import java.time.format.DateTimeFormatter

// TODO use room for persistence
//   https://www.youtube.com/watch?v=bOd3wO0uFr8
class MainActivity : ComponentActivity() {
    private val db by lazy {
        Room.databaseBuilder(applicationContext,
            TaskDatabase::class.java,
            "tasks.db"
        ).fallbackToDestructiveMigration()
         .build()
    }
    private val viewModel by viewModels<TaskFlowViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskFlowViewModel(TaskRepository(db.dao)) as T
                }
            }
        }
    )

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskflowTheme {
                val postNotificationPermission = rememberPermissionState(permission =  Manifest.permission.POST_NOTIFICATIONS)
                LaunchedEffect(key1 = true) {
                    if (!postNotificationPermission.status.isGranted) {
                         postNotificationPermission.launchPermissionRequest()
                    }
                }
                val notificationService = NotificationService(this)

                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    viewModel.initList()

                    TaskFlowView(viewModel, notificationService)
                }
            }
        }
    }
}

@Composable
fun TaskFlowView(model: TaskFlowViewModel, notificationService: NotificationService, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TaskRows(model, notificationService, listState)
        }
        Column{
            Text(text = "v0.11", fontSize = 10.sp)
        }
        Column{
            NewTaskRow(model, listState)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRows(model: TaskFlowViewModel, notificationService: NotificationService, listState: LazyListState, modifier: Modifier = Modifier) {
    val tasks = model.tasks // Directly use the list of tasks

    LazyColumn(state = listState, modifier = modifier) { // Use the modifier here
        val formatter = DateTimeFormatter.ofPattern("h:mm a")

        items(tasks, key = { it.id }) { task -> // Ensure each item has a unique key
            val timeRange = model.calcTimeRange(task)
            val formattedStartTime = formatter.format(timeRange.startTime)
            val formattedEndTime = formatter.format(timeRange.endTime)

            TaskRow(
                model,
                notificationService,
                task,
                formattedStartTime,
                formattedEndTime,
                listState,
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

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
fun TaskRow(model: TaskFlowViewModel, notificationService: NotificationService, task : TaskState, formattedStartTime : String, formattedEndTime : String, listState: LazyListState, modifier: Modifier = Modifier) {
    if(task.taskTimerRunning) {
        KeepScreenOn()
    }

    val myGreen = Color(android.graphics.Color.parseColor("#5aff54"))
    val myYellow = Color(android.graphics.Color.parseColor("#fcf758"))
    val myRed = Color(android.graphics.Color.parseColor("#fa5852"))
    val myBlue = Color(android.graphics.Color.parseColor("#3399ff"))

    var progressBarColor = myGreen
    if(task.percentageComplete() > 0.8 && task.percentageComplete() < 1)
        progressBarColor = myYellow
    else if(task.percentageComplete() >= 1)
        progressBarColor = myRed

    var taskBackgroundColor = MaterialTheme.colorScheme.primary
    var borderColor = Color.White
    var borderWidth = 0.dp
    var textStyle = TextStyle(
        fontSize = 15.sp,
        color=MaterialTheme.colorScheme.onPrimary,
    )
    if(task.taskTimerRunning) {
        borderColor = myGreen
        borderWidth = 3.dp
        textStyle = TextStyle(
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
        //notificationService.showBasicNotification(task.formatTimeRemaining())
    }

    val delete = SwipeAction(
        icon = rememberVectorPainter(Icons.TwoTone.Delete),
        background = Color.Red,
        onSwipe = {
            model.deleteTask(task)
        }
    )
    val playPause = SwipeAction(
        icon = rememberVectorPainter(Icons.TwoTone.PlayArrow),
        background = myGreen,
        isUndo = true,
        onSwipe = {
            model.toggleTimer(task)
            model.viewModelScope.launch {
                listState.scrollToItem(0)
            }
        },
    )

    SwipeableActionsBox(
        modifier = modifier.padding(bottom = 20.dp),
        startActions = listOf(delete),
        endActions = listOf(playPause),
        swipeThreshold = 40.dp
    ) {
        val dragOffsetY = remember { mutableStateOf(0f) }
        val haptic = LocalHapticFeedback.current

        Row(modifier = modifier
            .drawBehind {
                drawRoundRect(
                    cornerRadius = CornerRadius(x = 5.dp.toPx(), y = 5.dp.toPx()),
                    color = taskBackgroundColor,
                    size = Size(
                        drawContext.size.width,
                        drawContext.size.height
                    )
                )
                drawRoundRect(
                    cornerRadius = CornerRadius(x = 5.dp.toPx(), y = 5.dp.toPx()),
                    color = progressBarColor,
                    size = Size(
                        drawContext.size.width * (task.percentageComplete()),
                        drawContext.size.height
                    )
                )
            }
            .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(10))
            .padding(all = 5.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDrag = { _, dragAmount ->
                        dragOffsetY.value += dragAmount.y
                        if (dragOffsetY.value > 150f) {
                            model.moveTaskDown(task)
                            dragOffsetY.value = 0f
                        }
                        if (dragOffsetY.value < -150f) {
                            model.moveTaskUp(task, false)
                            dragOffsetY.value = 0f
                        }
                    },
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            }
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(task.name, style=textStyle, modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 5.dp, top = 5.dp))
                    Text(task.formatTimeRemaining(), style = textStyle, color=MaterialTheme.colorScheme.onPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formattedStartTime, fontSize = 12.sp,color=MaterialTheme.colorScheme.onPrimary)
                    Text(formattedEndTime, fontSize = 12.sp, color=MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun NewTaskRow(model: TaskFlowViewModel, listState: LazyListState, modifier: Modifier = Modifier) {
    val (minutesFocusRequester) = FocusRequester.createRefs()
    val (nameFocusRequester) = FocusRequester.createRefs()

    val newItemName by model.newItemName.collectAsState()
    val newItemTime by model.newItemTime.collectAsState()
    val addItemMode by model.addItemMode.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            value = newItemName,
            singleLine = true,
            onValueChange = { model.updateNewItemName(it) },
            label = { Text("task") },
            modifier = Modifier
                .weight(0.70f)
                .focusRequester(nameFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { minutesFocusRequester.requestFocus() }
            ),
        )
        TextField(
            value = newItemTime,
            singleLine = true,
            onValueChange = { model.updateNewItemTime(it) },
            label = { Text("minutes") },
            modifier = Modifier
                .weight(0.25f)
                .focusRequester(minutesFocusRequester),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    model.addItem()
                    nameFocusRequester.requestFocus()
                    if(addItemMode == "push") {
                        model.viewModelScope.launch {
                            listState.scrollToItem(0)
                        }
                    }
                }
            )
        )
        if(addItemMode == "append") {
            ClickableText(AnnotatedString("⬇️️"), modifier = Modifier.weight(0.05f), onClick={model.switchAddItemMode()})
        } else if(addItemMode == "push") {
            ClickableText(AnnotatedString("⬆️️️"), modifier = Modifier.weight(0.05f), onClick={model.switchAddItemMode()})
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TaskFlowViewPreview() {
//    //TODO this will break now since were not supplying the dao
//    val viewModel = viewModel<TaskFlowViewModel>()
//    viewModel.tasks.add(TaskState("test test test test test test test test test test test test test", "2").setRemainingDurationForTesting(1000*60*1))
//    viewModel.tasks.add(TaskState("test test test test test test test test test test test test test", "3"))
//    TaskflowTheme {
//        TaskFlowView(viewModel)
//    }
}

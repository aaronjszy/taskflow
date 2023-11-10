package com.ajsy.taskflow

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.ajsy.taskflow.db.Task
import com.ajsy.taskflow.db.TaskDatabase
import com.ajsy.taskflow.ui.TaskFlowViewModel
import com.ajsy.taskflow.ui.TaskState
import com.ajsy.taskflow.ui.theme.TaskflowTheme

// TODO use room for persistence
//   https://www.youtube.com/watch?v=bOd3wO0uFr8
class MainActivity : ComponentActivity() {
    private val db by lazy {
        Room.databaseBuilder(applicationContext,
            TaskDatabase::class.java,
            "tasks.db"
        ).build()
    }
    private val viewModel by viewModels<TaskFlowViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskFlowViewModel(db.dao) as T
                }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskflowTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    viewModel.initList()
                    TaskFlowView(viewModel)
                }
            }
        }
    }
}

@Composable
fun TaskFlowView(model: TaskFlowViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TaskRows(model)
        }
        Column{
            NewTaskRow(model)
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRows(model: TaskFlowViewModel, modifier: Modifier = Modifier) {
    LazyColumn() {
        var isFirstRow = true
        for(task in model.tasks) {
            if(isFirstRow) {
                item(key = task.getTaskId()) {
                    FirstTaskRow(model, task, modifier = Modifier
                        .animateItemPlacement()
                    )
                }
            } else {
                item(key = task.getTaskId()) {
                    TaskRow(model, task, modifier = Modifier
                        .animateItemPlacement()
                    )
                }
            }
            isFirstRow = false
        }
    }
}

fun Float.toDp(density: Density): Dp {
    return Dp(this * density.density)
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
fun FirstTaskRow(model: TaskFlowViewModel, task : TaskState, modifier: Modifier = Modifier) {
    if(task.taskTimerRunning) {
        KeepScreenOn()
    }

    var color = Color.Green
    if(task.percentageComplete() > 0.8 && task.percentageComplete() < 1)
        color = Color.Yellow
    else if(task.percentageComplete() >= 1)
        color = Color.Red

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().drawBehind {
                drawRect(
                    color = color,
                    size = Size(
                        drawContext.size.width * (task.percentageComplete()),
                        drawContext.size.height
                    )
                )
            },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(task.name, fontSize = 30.sp,  modifier = Modifier.weight(1f))
            Text(task.formatTimeRemaining(), fontSize = 30.sp)
        }

        TaskControls(model, task)
    }
}

@Composable
fun TaskRow(model: TaskFlowViewModel, task : TaskState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(task.name, modifier=Modifier.weight(1f))
            Text(task.formatTimeRemaining())
        }

        TaskControls(model, task)
    }
}

@Composable
fun TaskControls(model: TaskFlowViewModel, task : TaskState, modifier: Modifier = Modifier) {
    val offsetY = remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures { _, dragAmount ->
                offsetY.value += dragAmount
                if (offsetY.value > 300f) {
                    model.moveTaskDown(task)
                    offsetY.value = 0f
                }
                if (offsetY.value < -300f) {
                    model.moveTaskUp(task)
                    offsetY.value = 0f
                }
            }
        }) {
            Button(onClick = {}) {
                Text("\uD83D\uDD03")
            }
        }
        Button(onClick = {
            model.deleteTask(task)
        }) {
            Text("❌")
        }
        Button(onClick = {
            model.toggleTimer(task)
        }) {
            Text(
                if (task.taskTimerRunning) {
                    "⏸️"
                } else {
                    "▶️"
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun NewTaskRow(model: TaskFlowViewModel, modifier: Modifier = Modifier) {
    val (minutesFocusRequester) = FocusRequester.createRefs()
    val (nameFocusRequester) = FocusRequester.createRefs()

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            value = model.newItemName,
            singleLine = true,
            onValueChange = { model.updateNewItemName(it) },
            label = { Text("task") },
            modifier = Modifier
                .weight(0.75f)
                .focusRequester(nameFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { minutesFocusRequester.requestFocus() }
            ),
        )
        TextField(
            value = model.newItemTime,
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
                }
            ),
        )
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

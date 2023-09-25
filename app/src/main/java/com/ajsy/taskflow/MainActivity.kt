package com.ajsy.taskflow

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ajsy.taskflow.ui.Task
import com.ajsy.taskflow.ui.TaskFlowViewModel
import com.ajsy.taskflow.ui.theme.TaskflowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TaskflowTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TaskFlowView()
                }
            }
        }
    }
}

@Composable
fun TaskFlowView(modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TaskRows()
        }
        Column{
            NewTaskRow()
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRows(modifier: Modifier = Modifier) {
    val viewModel = viewModel<TaskFlowViewModel>()

    LazyColumn() {
        var isFirstRow = true
        for(task in viewModel.tasks) {
            if(isFirstRow) {
                item(key = task.getId()) {
                    FirstTaskRow(task, modifier = Modifier.animateItemPlacement())
                }
            } else {
                item(key = task.getId()) {
                    TaskRow(task, modifier = Modifier.animateItemPlacement())
                }
            }
            isFirstRow = false
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
fun FirstTaskRow(task :Task, modifier: Modifier = Modifier) {
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
            Text(task.name, fontSize = 30.sp)
            Text(task.formatTimeRemaining(), fontSize = 30.sp)
        }

        TaskControls(task)
    }
}

@Composable
fun TaskRow(task :Task, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(task.name)
            Text(task.formatTimeRemaining())
        }

        TaskControls(task)
    }
}

@Composable
fun TaskControls(task :Task, modifier: Modifier = Modifier) {
    val viewModel = viewModel<TaskFlowViewModel>()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Button(onClick = {
            viewModel.moveTaskUp(task)
        }) {
            Text("⬆️")
        }
        Button(onClick = {
            viewModel.moveTaskDown(task)
        }) {
            Text("⬇️")
        }
        Button(onClick = {
            viewModel.deleteTask(task)
        }) {
            Text("❌")
        }
        Button(onClick = {
            viewModel.toggleTimer(task)
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
fun NewTaskRow(modifier: Modifier = Modifier) {
    val viewModel = viewModel<TaskFlowViewModel>()

    val (minutesFocusRequester) = FocusRequester.createRefs()
    val (nameFocusRequester) = FocusRequester.createRefs()

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            value = viewModel.newItemName,
            singleLine = true,
            onValueChange = { viewModel.updateNewItemName(it) },
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
            value = viewModel.newItemTime,
            singleLine = true,
            onValueChange = { viewModel.updateNewItemTime(it) },
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
                    viewModel.addItem()
                    nameFocusRequester.requestFocus()
                }
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TaskFlowViewPreview() {
    val viewModel = viewModel<TaskFlowViewModel>()
    viewModel.tasks.add(Task("asdf", "2").setRemainingDurationForTesting(1000*60*1))
    viewModel.tasks.add(Task("asdfasdf", "3"))
    TaskflowTheme {
        TaskFlowView()
    }
}

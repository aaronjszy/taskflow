package com.ajsy.taskflow

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.ajsy.taskflow.db.TaskDatabase
import com.ajsy.taskflow.db.TaskRepository
import com.ajsy.taskflow.db.migrations.MIGRATION_8_9
import com.ajsy.taskflow.ui.TaskFlowViewModel
import com.ajsy.taskflow.ui.components.CursorRow
import com.ajsy.taskflow.ui.components.FloatingActionMenu
import com.ajsy.taskflow.ui.components.NewTaskRow
import com.ajsy.taskflow.ui.components.TaskRow
import com.ajsy.taskflow.ui.state.DialogState
import com.ajsy.taskflow.ui.theme.AppTheme
import com.ajsy.taskflow.ui.theme.rememberThemeConfig
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(applicationContext, TaskDatabase::class.java, "tasks.db")
            .addMigrations(MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()
    }

    private val viewModel by viewModels<TaskFlowViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TaskFlowViewModel(TaskRepository(db.dao, db.savedListDao)) as T
                }
            }
        }
    )

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val notificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
                LaunchedEffect(Unit) {
                    if (!notificationPermission.status.isGranted) {
                        notificationPermission.launchPermissionRequest()
                    }
                    viewModel.initList()
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TaskFlowScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun TaskFlowScreen(model: TaskFlowViewModel) {
    val listState = rememberLazyListState()
    val scrollTrigger by model.scrollToTopTrigger.collectAsState()
    var dialogState by remember { mutableStateOf(DialogState()) }

    LaunchedEffect(scrollTrigger) {
        if (scrollTrigger > 0) listState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Computed inside the content lambda so taskDepths and model.tasks reads
                // are always from the same snapshot, preventing index-out-of-bounds crashes.
                var depthCounter = 0
                val taskDepths = model.tasks.map { task ->
                    val depth = if (task.name.startsWith("<<<")) {
                        depthCounter = (depthCounter - 1).coerceAtLeast(0)
                        depthCounter
                    } else {
                        depthCounter
                    }
                    if (task.name.startsWith(">>>") && !task.name.contains("[folded]")) depthCounter++
                    depth
                }

                val cursorPos = model.cursor.position.coerceIn(0, model.tasks.size)
                model.tasks.take(cursorPos).forEachIndexed { index, task ->
                    val indent = (taskDepths[index] * 12).dp
                    item(key = task.id) {
                        TaskRow(model = model, task = task, listState = listState,
                            modifier = Modifier.padding(start = indent))
                    }
                    item(key = task.id + "time") {
                        Row(modifier = Modifier.padding(start = indent)) {
                            Text(
                                text = timeFormatter.format(model.calcTimeRange(task).endTime),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                item(key = "cursor") {
                    CursorRow(model = model)
                }
                model.tasks.drop(cursorPos).forEachIndexed { index, task ->
                    val indent = (taskDepths[cursorPos + index] * 12).dp
                    item(key = task.id) {
                        TaskRow(model = model, task = task, listState = listState,
                            modifier = Modifier.padding(start = indent))
                    }
                    item(key = task.id + "time") {
                        Row(modifier = Modifier.padding(start = indent)) {
                            Text(
                                text = timeFormatter.format(model.calcTimeRange(task).endTime),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            NewTaskRow(model = model, listState = listState)

            if (model.hasOverdueTasks()) {
                val nextOverdue = model.getNextOverdueTask()
                if (nextOverdue != null) {
                    val (taskName, lateness) = nextOverdue
                    val truncatedName = if (taskName.length > 25) taskName.take(22) + "..." else taskName
                    val themeConfig = rememberThemeConfig()
                    Text(
                        text = "⚠️ $truncatedName is $lateness",
                        color = themeConfig.custom.darkError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeConfig.custom.lightSuccess, RoundedCornerShape(4.dp))
                            .border(1.dp, themeConfig.custom.border, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        FloatingActionMenu(
            model = model,
            uiState = dialogState,
            onUiStateChange = { dialogState = it },
        )
    }
}

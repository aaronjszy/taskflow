package com.ajsy.taskflow

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

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
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                val cursorPos = model.cursor.position.coerceIn(0, model.tasks.size)
                items(model.tasks.take(cursorPos), key = { it.id }) { task ->
                    TaskRow(model = model, task = task, listState = listState)
                }
                item(key = "cursor") {
                    CursorRow(model = model, listState = listState)
                }
                items(model.tasks.drop(cursorPos), key = { it.id }) { task ->
                    TaskRow(model = model, task = task, listState = listState)
                }
            }
            NewTaskRow(model = model, listState = listState)
        }

        FloatingActionMenu(
            model = model,
            uiState = dialogState,
            onUiStateChange = { dialogState = it },
        )
    }
}

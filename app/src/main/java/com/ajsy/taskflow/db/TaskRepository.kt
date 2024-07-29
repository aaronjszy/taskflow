package com.ajsy.taskflow.db

import com.ajsy.taskflow.ui.TaskViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRepository(
    private val taskDao: TaskDao
) {
    val repoMutex = Mutex()

    companion object {
        private fun dtoToViewModel(taskDto: Task, taskRepository: TaskRepository, viewModelScope: CoroutineScope): TaskViewModel {
            val taskViewModel = TaskViewModel(taskDto.name, taskDto.durationStr, taskRepository, viewModelScope)
            taskViewModel.id = taskDto.id
            taskViewModel.position = taskDto.position
            taskViewModel.taskTimerRunning = taskDto.taskTimerRunning
            taskViewModel.remainingDuration = taskDto.remainingDuration
            taskViewModel.originalDuration = taskDto.originalDuration
            taskViewModel.taskTimerTickTime = taskDto.taskTimerTickTime
            if(taskViewModel.taskTimerRunning) {
                taskViewModel.start()
            } else {
                taskViewModel.stop()
            }
            return taskViewModel
        }

        fun viewModelToDto(taskViewModel: TaskViewModel): Task {
            return Task(
                id = taskViewModel.id,
                name = taskViewModel.name,
                position = taskViewModel.position,
                durationStr = taskViewModel.durationStr,
                taskTimerRunning = taskViewModel.taskTimerRunning,
                remainingDuration = taskViewModel.remainingDuration,
                originalDuration = taskViewModel.originalDuration,
                taskTimerTickTime = taskViewModel.taskTimerTickTime,
            )
        }

        fun dtoListToViewModelList(
            taskListDto: List<Task>,
            taskRepository: TaskRepository,
            viewModelScope: CoroutineScope
        ): List<TaskViewModel> {
            return taskListDto.map { dtoToViewModel(it, taskRepository, viewModelScope) }
        }

        fun viewModelListToDtoList(taskList: List<TaskViewModel>): List<Task> {
            return taskList.map { viewModelToDto(it) }
        }
    }

    suspend fun  deleteTask(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.deleteTask(viewModelToDto(task))
        }
    }

    suspend fun  updateTask(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.updateTask(viewModelToDto(task))
        }
    }

    suspend fun  upsertTask(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.upsertTask(viewModelToDto(task))
        }
    }

    suspend fun  getTasks(viewModelScope: CoroutineScope): List<TaskViewModel> {
        repoMutex.withLock {
            return dtoListToViewModelList(taskDao.getTasks(), this, viewModelScope)
        }
    }

    suspend fun  updateTasks(tasks: List<TaskViewModel>) {
        repoMutex.withLock {
            taskDao.updateTasks(viewModelListToDtoList(tasks))
        }
    }
}

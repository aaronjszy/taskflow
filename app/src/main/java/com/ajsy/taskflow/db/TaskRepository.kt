package com.ajsy.taskflow.db

import com.ajsy.taskflow.ui.TaskViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRepository(
    private val taskDao: TaskDao,
    private val savedListDao: SavedListDao
) {
    val repoMutex = Mutex()

    companion object {
        fun viewModelToDto(task: TaskViewModel): Task {
            return Task(
                id = task.id,
                position = task.position,
                name = task.name,
                durationStr = task.durationStr,
                originalDuration = task.originalDuration,
                remainingDurationAtResume = task.remainingDurationAtResume,
                lastResumedTime = 0L,
                taskTimerRunning = task.taskTimerRunning,
                fixedStartTime = null
            )
        }

        fun viewModelListToDtoList(taskList: List<TaskViewModel>): List<Task> {
            return taskList.map { viewModelToDto(it) }
        }
    }

    suspend fun deleteTask(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.deleteTask(viewModelToDto(task))
        }
    }

    suspend fun updateTaskTimerRunningState(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.updateTaskTimerRunningState(task.id, task.taskTimerRunning)
        }
    }

    suspend fun updateTaskTimerProgress(task: TaskViewModel) {
        // intentional no-op; use updateTaskTimerProgressDirect for direct updates
    }

    suspend fun updateTaskTimerProgressDirect(taskId: String, remainingDurationAtResume: Long, lastResumedTime: Long) {
        repoMutex.withLock {
            taskDao.updateTaskTimerProgress(taskId, remainingDurationAtResume, lastResumedTime)
        }
    }

    suspend fun upsertTask(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.upsertTask(viewModelToDto(task))
        }
    }

    suspend fun updateTask(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.updateTask(viewModelToDto(task))
        }
    }

    suspend fun getTasks(): List<Task> {
        return repoMutex.withLock {
            taskDao.getTasks()
        }
    }

    suspend fun getLastResumedTimes(): Map<String, Long> {
        return repoMutex.withLock {
            taskDao.getTasks().associate { it.id to it.lastResumedTime }
        }
    }

    suspend fun replaceAllTasks(tasks: List<TaskViewModel>) {
        repoMutex.withLock {
            taskDao.replaceAllTasks(viewModelListToDtoList(tasks))
        }
    }

    suspend fun updateTaskPositions(tasks: List<TaskViewModel>) {
        repoMutex.withLock {
            taskDao.updateTaskPositions(viewModelListToDtoList(tasks))
        }
    }

    suspend fun updateTaskFixedStartTime(task: TaskViewModel) {
        repoMutex.withLock {
            taskDao.updateTaskFixedStartTime(task.id, task.fixedStartTime)
        }
    }

    suspend fun saveNewList(name: String, tasks: List<Task>): Long {
        return repoMutex.withLock {
            savedListDao.saveNewList(name, tasks)
        }
    }

    suspend fun getSavedLists(): List<SavedTaskList> {
        return repoMutex.withLock {
            savedListDao.getAllLists()
        }
    }

    suspend fun getTasksForList(listId: Int): List<SavedTask> {
        return repoMutex.withLock {
            savedListDao.getTasksForList(listId)
        }
    }

    suspend fun getTasksFromSavedList(listId: Int): List<SavedTask> {
        return repoMutex.withLock {
            savedListDao.getTasksForList(listId)
        }
    }

    suspend fun deleteSavedList(listId: Int) {
        repoMutex.withLock {
            savedListDao.deleteList(listId)
        }
    }

    suspend fun updateSavedList(listId: Int, name: String, tasks: List<Task>) {
        repoMutex.withLock {
            savedListDao.updateListName(listId, name)
            savedListDao.deleteTasksForList(listId)
            tasks.forEachIndexed { index, task ->
                savedListDao.insertTask(
                    SavedTask(
                        listId = listId,
                        name = task.name,
                        durationStr = task.durationStr,
                        position = index,
                        fixedStartTime = task.fixedStartTime
                    )
                )
            }
        }
    }

    suspend fun addTaskToSavedList(task: TaskViewModel, listId: Int) {
        repoMutex.withLock {
            val existingTasks = savedListDao.getTasksForList(listId)
            savedListDao.insertTask(
                SavedTask(
                    listId = listId,
                    name = task.name,
                    durationStr = task.durationStr,
                    position = existingTasks.size,
                    fixedStartTime = null
                )
            )
        }
    }
}

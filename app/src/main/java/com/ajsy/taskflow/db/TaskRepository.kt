package com.ajsy.taskflow.db

class TaskRepository(
    private val taskDao: TaskDao
) {
    suspend fun  deleteTask(task: Task) {
        println("deleteTask: " + task)
        taskDao.deleteTask(task)
    }

    suspend fun  updateTask(task: Task) {
        println("updateTask: " + task)
        taskDao.updateTask(task)
    }

    suspend fun  upsertTask(task: Task) {
        println("upsertTask: " + task)
        taskDao.upsertTask(task)
    }

    suspend fun  getTasks(): List<Task> {
        return taskDao.getTasks()
    }
}

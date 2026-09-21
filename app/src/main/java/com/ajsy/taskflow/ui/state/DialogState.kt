package com.ajsy.taskflow.ui.state

import com.ajsy.taskflow.db.SavedTaskList

data class DialogState(
    val showMenu: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showLoadDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val listToDelete: SavedTaskList? = null,
    val listNameInput: String = ""
) {
    fun closeAllDialogs() = copy(
        showMenu = false, showSaveDialog = false, showLoadDialog = false,
        showThemeDialog = false, showDeleteConfirmDialog = false
    )

    fun resetListInputs() = copy(listToDelete = null, listNameInput = "")

    fun openSaveDialog() = copy(showSaveDialog = true, showMenu = false)
    fun openLoadDialog() = copy(showLoadDialog = true, showMenu = false)
    fun openThemeDialog() = copy(showThemeDialog = true, showMenu = false)
    fun openDeleteConfirmDialog(list: SavedTaskList) = copy(showDeleteConfirmDialog = true, listToDelete = list)

    fun updateShowMenu(show: Boolean) = copy(showMenu = show)
    fun updateListToDelete(list: SavedTaskList?) = copy(listToDelete = list)
    fun updateListNameInput(input: String) = copy(listNameInput = input)
}

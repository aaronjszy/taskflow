package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajsy.taskflow.db.SavedTaskList
import com.ajsy.taskflow.db.Task
import com.ajsy.taskflow.db.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class TaskFlowViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    val CURSOR_TOP = 0
    val CURSOR_BOTTOM = 1
    val CURSOR_CUSTOM = 2

    private val FOLD_SEP = "§"

    private val _tasks: SnapshotStateList<TaskViewModel> = mutableStateListOf()
    val tasks: List<TaskViewModel> get() = _tasks

    private val taskListMutex = Mutex()

    private val _newItemName = MutableStateFlow("")
    val newItemName: StateFlow<String> = _newItemName.asStateFlow()

    private val _newItemTime = MutableStateFlow("")
    val newItemTime: StateFlow<String> = _newItemTime.asStateFlow()

    private val _addItemMode = MutableStateFlow("above_cursor")
    val addItemMode: StateFlow<String> = _addItemMode.asStateFlow()

    private val _editingTask = MutableStateFlow<TaskViewModel?>(null)
    val editingTask: StateFlow<TaskViewModel?> = _editingTask.asStateFlow()

    private val _savedLists = MutableStateFlow<List<SavedTaskList>>(emptyList())
    val savedLists: StateFlow<List<SavedTaskList>> = _savedLists.asStateFlow()

    private val _scrollToTopTrigger = MutableStateFlow(0)
    val scrollToTopTrigger: StateFlow<Int> = _scrollToTopTrigger.asStateFlow()

    private val _cursor = mutableStateOf(CursorViewModel())
    val cursor: CursorViewModel get() = _cursor.value

    private val _cursorPositionState = MutableStateFlow(CURSOR_BOTTOM)
    val cursorPositionState: StateFlow<Int> = _cursorPositionState.asStateFlow()

    val cursorPosition: Int get() = cursor.position

    data class TimeRange(val startTime: LocalDateTime, val endTime: LocalDateTime)

    private data class TimeSlot(
        val startTime: Long,
        val endTime: Long,
        val tasks: List<TaskViewModel>
    )



    fun initList() {
        viewModelScope.launch {
            taskListMutex.withLock {
                val storedTasks = taskRepository.getTasks()
                val lastResumedTimes = taskRepository.getLastResumedTimes()
                _tasks.clear()
                storedTasks.forEach { task ->
                    val viewModel = TaskViewModel(task.name, task.durationStr, taskRepository, viewModelScope)
                    viewModel.id = task.id
                    viewModel.position = task.position
                    viewModel.originalDuration = task.originalDuration
                    viewModel.fixedStartTime = task.fixedStartTime
                    viewModel.remainingDurationAtResume = task.remainingDurationAtResume
                    viewModel.lastResumedTime = lastResumedTimes[task.id] ?: 0L
                    viewModel.taskTimerRunning = task.taskTimerRunning
                    viewModel.updateTimerDisplayTime(task.remainingDurationAtResume)
                    if (task.taskTimerRunning) {
                        viewModel.start()
                    }
                    _tasks.add(viewModel)
                }
                cursor.position = _tasks.size
                updateCursorPositionState()
            }
            updateTaskPositions()
        }
        loadSavedLists()
    }

    // Cursor navigation

    fun moveCursorUpAsync() {
        viewModelScope.launch {
            taskListMutex.withLock {
                if (cursor.position > 0) {
                    if (_tasks.isNotEmpty() && _tasks[0].taskTimerRunning && cursor.position <= 1) {
                        cursor.position = 1
                    } else {
                        cursor.position -= 1
                        updateCursorPositionState()
                    }
                }
            }
        }
    }

    fun moveCursorDownAsync() {
        viewModelScope.launch {
            taskListMutex.withLock {
                if (cursor.position < _tasks.size) {
                    cursor.position += 1
                    updateCursorPositionState()
                }
            }
        }
    }

    fun moveCursorToTop() {
        viewModelScope.launch {
            taskListMutex.withLock {
                cursor.position = if (_tasks.isNotEmpty() && _tasks[0].taskTimerRunning) 1 else 0
                _addItemMode.value = "below_cursor"
                _cursorPositionState.value = CURSOR_TOP
            }
        }
    }

    fun moveCursorToBottom() {
        viewModelScope.launch {
            taskListMutex.withLock {
                cursor.position = _tasks.size
                _addItemMode.value = "above_cursor"
                _cursorPositionState.value = CURSOR_BOTTOM
            }
        }
    }

    fun toggleCursorPosition() {
        viewModelScope.launch {
            taskListMutex.withLock {
                when (_cursorPositionState.value) {
                    CURSOR_TOP -> {
                        cursor.position = _tasks.size
                        _addItemMode.value = "above_cursor"
                        _cursorPositionState.value = CURSOR_BOTTOM
                    }
                    CURSOR_BOTTOM, CURSOR_CUSTOM -> {
                        cursor.position = if (_tasks.isNotEmpty() && _tasks[0].taskTimerRunning) 1 else 0
                        _addItemMode.value = "above_cursor"
                        _cursorPositionState.value = CURSOR_TOP
                    }
                }
            }
        }
    }

    private fun updateCursorPositionState() {
        _cursorPositionState.value = when {
            cursor.position == 0 ||
                (cursor.position == 1 && _tasks.isNotEmpty() && _tasks[0].taskTimerRunning) -> CURSOR_TOP
            cursor.position == _tasks.size -> CURSOR_BOTTOM
            else -> CURSOR_CUSTOM
        }
    }

    // Timer control

    fun toggleTimer(task: TaskViewModel) {
        if (task.taskTimerRunning) {
            task.stop()
        } else {
            startTimerForTaskAsync(task)
        }
    }

    private fun startTimerForTaskAsync(task: TaskViewModel) {
        viewModelScope.launch {
            val cursorAtBottom = cursor.position == _tasks.size
            val cursorTask = _tasks.getOrNull(cursor.position)
            val cursorBeforeTask = _tasks.indexOf(task) >= cursor.position

            // Stop any currently running timers before starting the new one
            _tasks.filter { it.taskTimerRunning && it.id != task.id }.forEach { it.stop() }

            moveTaskToTop(task, noSync = true, noStopTimer = true)
            // Calculate bufferSeconds before starting so the timer display includes buffer
            updateTaskPositions()
            task.start()

            taskListMutex.withLock {
                cursor.position = when {
                    cursorAtBottom -> _tasks.size
                    cursorTask != null -> _tasks.indexOf(cursorTask).coerceAtLeast(0)
                    cursorBeforeTask -> (cursor.position + 1).coerceAtMost(_tasks.size)
                    else -> cursor.position
                }
                updateCursorPositionState()
            }
        }
    }

    fun resetTaskTimer(task: TaskViewModel) {
        task.resetTimer()
    }

    fun addFiveMinutes(task: TaskViewModel) {
        val addMs = 300_000L
        task.originalDuration += addMs
        task.remainingDurationAtResume += addMs
        task.updateTimerDisplayTime(task.displayRemainingDuration + addMs)
        task.durationStr = (task.originalDuration / 60_000L).toString()
        updateTaskAsync(task)
    }

    fun resetAllTimers() {
        viewModelScope.launch {
            taskListMutex.withLock {
                _tasks.forEach { task ->
                    task.resetTimer()
                }
            }
        }
    }

    // Add / edit items

    fun updateNewItemName(name: String) {
        _newItemName.value = name
    }

    fun updateNewItemTime(time: String) {
        _newItemTime.value = time
    }

    fun switchAddItemMode() {
        _addItemMode.value = if (_addItemMode.value == "below_cursor") "above_cursor" else "below_cursor"
    }

    fun startEditing(task: TaskViewModel) {
        _editingTask.value = task
        _newItemName.value = task.getDisplayName()
        _newItemTime.value = task.durationStr
    }

    fun cancelEditing() {
        _editingTask.value = null
        _newItemName.value = ""
        _newItemTime.value = ""
    }

    fun addItem() {
        val editing = _editingTask.value
        if (editing != null) {
            val (cleanName, newFixedStartTime) = processTimePrefix(_newItemName.value)
            editing.name = cleanName
            val duration = _newItemTime.value.ifEmpty { "1" }
            editing.durationStr = duration
            editing.originalDuration = editing.stringMinutesToLongMillis(editing.durationStr)
            editing.updateTimerDisplayTime(editing.originalDuration)
            if (newFixedStartTime != null) {
                editing.fixedStartTime = newFixedStartTime
            }
            viewModelScope.launch {
                taskRepository.upsertTask(editing)
                updateTaskPositions()
            }
            cancelEditing()
            return
        }

        if (_newItemTime.value.isEmpty() && lineIncludesTime(_newItemName.value)) {
            parseMultilineString(_newItemName.value).forEach { (taskName, taskTime) ->
                addSingleItemAsync(taskName, taskTime)
            }
        } else {
            addSingleItemAsync(_newItemName.value, _newItemTime.value)
        }
        updateCursorPositionState()
        _newItemTime.value = ""
        _newItemName.value = ""
    }

    private fun addSingleItemAsync(taskName: String, taskTime: String) {
        viewModelScope.launch {
            val duration = taskTime.ifEmpty { "1" }
            val (cleanName, fixedStartTime) = processTimePrefix(taskName)
            val task = TaskViewModel(cleanName, duration, taskRepository, viewModelScope)
            task.fixedStartTime = fixedStartTime
            taskListMutex.withLock {
                val insertIndex = if (_addItemMode.value == "below_cursor") {
                    (cursor.position + 1).coerceAtMost(_tasks.size)
                } else {
                    cursor.position.coerceIn(0, _tasks.size)
                }
                _tasks.add(insertIndex, task)
                _tasks.forEachIndexed { index, t -> t.position = index }
                cursor.position = insertIndex + 1
                taskRepository.upsertTask(task)
            }
            updateTaskPositions()
        }
    }

    private suspend fun addItemAt(taskName: String, taskTime: String, index: Int) {
        val duration = taskTime.ifEmpty { "1" }
        val (cleanName, fixedStartTime) = processTimePrefix(taskName)
        val task = TaskViewModel(cleanName, duration, taskRepository, viewModelScope)
        task.fixedStartTime = fixedStartTime
        taskListMutex.withLock {
            val insertIndex = index.coerceIn(0, _tasks.size)
            _tasks.add(insertIndex, task)
            _tasks.forEachIndexed { i, t -> t.position = i }
        }
        taskRepository.upsertTask(task)
        updateTaskPositions()
    }

    fun updateTaskAsync(task: TaskViewModel) {
        viewModelScope.launch {
            taskRepository.upsertTask(task)
        }
    }

    // Move / reorder

    private suspend fun moveTaskUp(task: TaskViewModel, noStopTimer: Boolean) {
        taskListMutex.withLock {
            val index = _tasks.indexOf(task)
            if (index <= 0) return@withLock
            val above = index - 1
            _tasks[index] = _tasks[above]
            _tasks[index].position = index
            _tasks[above] = task
            _tasks[above].position = above
            if (!noStopTimer) task.stop()
        }
    }

    fun moveTaskUpAsync(task: TaskViewModel, noStopTimer: Boolean) {
        viewModelScope.launch {
            moveTaskUp(task, noStopTimer)
            updateTaskPositions()
        }
    }

    private suspend fun moveTaskDown(task: TaskViewModel) {
        taskListMutex.withLock {
            val index = _tasks.indexOf(task)
            if (index == -1 || index == _tasks.size - 1) return@withLock
            val below = index + 1
            _tasks[index] = _tasks[below]
            _tasks[index].position = index
            _tasks[below] = task
            _tasks[below].position = below
            task.stop()
        }
    }

    fun moveTaskDownAsync(task: TaskViewModel) {
        viewModelScope.launch {
            moveTaskDown(task)
            updateTaskPositions()
        }
    }

    private fun moveTaskToTopNoLock(
        task: TaskViewModel,
        noSync: Boolean,
        noStopTimer: Boolean,
        cursorPos: Int,
        taskIndex: Int
    ): Int {
        val adjustedCursorPos = if (taskIndex < cursorPos) cursorPos - 1 else cursorPos
        _tasks.removeAt(taskIndex)
        _tasks.add(0, task)
        _tasks.forEachIndexed { index, t -> t.position = index }
        if (!noStopTimer) task.stop()
        if (!noSync) updateTaskPositionsAsync()
        return (adjustedCursorPos + 1).coerceAtMost(_tasks.size)
    }

    private suspend fun moveTaskToTop(task: TaskViewModel, noSync: Boolean, noStopTimer: Boolean) {
        val taskIndex = _tasks.indexOf(task)
        if (taskIndex == 0) return
        val cursorPos = cursor.position
        val newCursorPos = moveTaskToTopNoLock(task, noSync, noStopTimer, cursorPos, taskIndex)
        taskListMutex.withLock {
            cursor.position = newCursorPos
        }
    }

    fun moveTaskToTopAsync(task: TaskViewModel) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val taskIndex = _tasks.indexOf(task)
                if (taskIndex <= 0) return@withLock
                task.stop()
                _tasks.removeAt(taskIndex)
                _tasks.add(0, task)
                _tasks.forEachIndexed { index, t -> t.position = index }
            }
            updateTaskPositions()
        }
    }

    fun moveTaskToBottomAsync(task: TaskViewModel) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val index = _tasks.indexOf(task)
                if (index == 0) task.stop()
                val removed = _tasks.removeAt(index)
                _tasks.add(_tasks.size, removed)
                _tasks.forEachIndexed { i, t -> t.position = i }
            }
            updateTaskPositions()
        }
    }

    fun deferTaskAsync(task: TaskViewModel) {
        viewModelScope.launch {
            val wasTimerRunning = task.taskTimerRunning
            moveTaskDown(task)
            resetTaskTimer(task)
            if (wasTimerRunning && _tasks.isNotEmpty()) {
                startTimerForTaskAsync(_tasks[0])
            }
            updateTaskPositions()
        }
    }

    fun deleteTaskAsync(task: TaskViewModel) {
        viewModelScope.launch {
            val wasTimerRunning = task.taskTimerRunning
            taskListMutex.withLock {
                task.cleanup()
                _tasks.remove(task)
                _tasks.forEachIndexed { index, t -> t.position = index }
                taskRepository.deleteTask(task)
                if (wasTimerRunning && _tasks.isNotEmpty()) {
                    startTimerForTaskAsync(_tasks[0])
                }
            }
            updateTaskPositions()
        }
    }

    private suspend fun updateTaskPositions() {
        taskRepository.updateTaskPositions(_tasks.toList())
        var previousEndTime = LocalDateTime.now()
        for (task in _tasks) {
            val timeRange = calcTimeRange(task)
            if (task.fixedStartTime != null) {
                task.updateBufferTime(previousEndTime)
            } else {
                task.bufferSeconds = 0L
            }
            previousEndTime = timeRange.endTime
        }
    }

    private fun updateTaskPositionsAsync() {
        viewModelScope.launch {
            updateTaskPositions()
        }
    }

    private suspend fun replaceAllTasksInDb() {
        taskRepository.replaceAllTasks(_tasks.toList())
    }

    // Fold / unfold

    fun fold(headerTask: TaskViewModel) {
        if (!headerTask.name.startsWith(">>>") || headerTask.name.contains("[folded]")) return

        val tasksInGroup = mutableListOf<TaskViewModel>()
        var insideTaskGroup = false
        var depth = 1
        for (task in _tasks) {
            if (insideTaskGroup) {
                headerTask.name = headerTask.name + FOLD_SEP + task.serializeForFold()
                headerTask.originalDuration += task.originalDuration
                headerTask.updateTimerDisplayTime(headerTask.originalDuration)
                tasksInGroup.add(task)
                if (task.name.startsWith(">>>") && !task.name.contains("[folded]")) {
                    depth++
                } else if (task.name.startsWith("<<<")) {
                    depth--
                    if (depth == 0) break
                }
            } else if (task.id == headerTask.id) {
                insideTaskGroup = true
            }
        }
        tasksInGroup.forEach { deleteTaskAsync(it) }
        headerTask.name = headerTask.name + "[folded]"
        updateTaskAsync(headerTask)
    }

    fun unfold(headerTask: TaskViewModel) {
        if (!headerTask.name.startsWith(">>>") || !headerTask.name.contains("[folded]")) return

        var insertionPosition = headerTask.position + 1
        headerTask.name = headerTask.name.removeSuffix("[folded]")
        val serializedTasksInGroup = if (headerTask.name.contains(FOLD_SEP))
            splitFoldEncoded(headerTask.name)
        else
            headerTask.name.split("#") // legacy format
        serializedTasksInGroup.forEachIndexed { index, serialized ->
            if (index != 0) {
                val parsedSerializedTasks = parseMultilineString(serialized)
                val position = insertionPosition
                viewModelScope.launch {
                    parsedSerializedTasks.forEach { (name, duration) ->
                        addItemAt(name, duration, position)
                    }
                }
                insertionPosition++
            }
        }
        headerTask.name = serializedTasksInGroup.first()
        updateTaskAsync(headerTask)
        headerTask.originalDuration = 0L
        headerTask.updateTimerDisplayTime(headerTask.originalDuration)
    }

    // Time-prefix parsing

    private fun processTimePrefix(taskName: String): Pair<String, Long?> {
        val timeRegex = Regex("^(\\d+)(?::(\\d+))?(am?|pm?)?\\s+(.*)", RegexOption.IGNORE_CASE)
        val matchResult = timeRegex.find(taskName) ?: return Pair(taskName, null)

        val groups = matchResult.groupValues
        val hourStr = groups[1]
        val minuteStr = groups[2]
        val meridianStr = groups[3]
        val restOfName = groups[4]

        val hour = hourStr.toInt()
        val minute = if (minuteStr != "") minuteStr.toInt() else 0

        val currentTime = LocalTime.now()
        var fixedStartTimeHour = hour

        if (meridianStr.isNotEmpty()) {
            val meridian = meridianStr.lowercase(Locale.ROOT)
            if (meridian.startsWith("p") && fixedStartTimeHour != 12) {
                fixedStartTimeHour += 12
            } else if (meridian.startsWith("a") && fixedStartTimeHour == 12) {
                fixedStartTimeHour = 0
            }
        } else {
            val amTime = LocalTime.of(hour % 12, minute)
            val pmTime = LocalTime.of((hour % 12) + 12, minute)
            val amIsFuture = amTime.isAfter(currentTime)
            val pmIsFuture = pmTime.isAfter(currentTime)
            if (!amIsFuture && pmIsFuture) {
                fixedStartTimeHour += 12
            } else if (amIsFuture && pmIsFuture) {
                val amDiff = Duration.between(currentTime, amTime).toSeconds()
                val pmDiff = Duration.between(currentTime, pmTime).toSeconds()
                if (pmDiff < amDiff) fixedStartTimeHour += 12
            }
        }

        var fixedStartDateTime = LocalDateTime.now()
            .withHour(fixedStartTimeHour % 24)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (fixedStartDateTime.toLocalTime().isBefore(currentTime)) {
            fixedStartDateTime = fixedStartDateTime.plusDays(1L)
        }
        val fixedStartTimeEpoch = fixedStartDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Pair(restOfName, fixedStartTimeEpoch)
    }

    private fun splitFoldEncoded(str: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < str.length) {
            if (str[i] == '§' && i + 1 < str.length && str[i + 1] == '§') {
                current.append('§')
                i += 2
            } else if (str[i] == '§') {
                result.add(current.toString())
                current.clear()
                i++
            } else {
                current.append(str[i])
                i++
            }
        }
        result.add(current.toString())
        return result
    }

    private fun lineIncludesTime(line: String): Boolean {
        return Regex("\\d+$").containsMatchIn(line)
    }

    private fun parseMultilineString(input: String): List<Array<String>> {
        return input.lines().mapNotNull { line ->
            if (line.trim().isEmpty()) {
                null
            } else {
                val durationMatch = Regex("(.*)\\s+(\\d+)$").find(line.trim())
                val (content, duration) = if (durationMatch != null) {
                    Pair(durationMatch.groupValues[1].trim(), durationMatch.groupValues[2])
                } else {
                    Pair(line.trim(), "1")
                }
                arrayOf(content, duration)
            }
        }
    }

    // Time range calculations

    fun calcTimeRangeStartingAt(priorTaskEndTime: LocalDateTime, targetTask: TaskViewModel): TimeRange {
        val fixedStartTime = targetTask.fixedStartTime
        if (fixedStartTime != null) {
            val fixedStartDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(fixedStartTime), ZoneId.systemDefault())
            return if (priorTaskEndTime.isAfter(fixedStartDateTime)) {
                TimeRange(priorTaskEndTime, priorTaskEndTime.plusNanos(targetTask.displayRemainingDuration * 1_000_000L))
            } else {
                TimeRange(fixedStartDateTime, fixedStartDateTime.plusNanos(targetTask.originalDuration * 1_000_000L))
            }
        }
        return TimeRange(priorTaskEndTime, priorTaskEndTime.plusNanos(targetTask.displayRemainingDuration * 1_000_000L))
    }

    fun calcTimeRange(targetTask: TaskViewModel): TimeRange {
        var priorTaskEndTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
        for (task in _tasks) {
            val timeRange = calcTimeRangeStartingAt(priorTaskEndTime, task)
            priorTaskEndTime = timeRange.endTime
            if (task.id == targetTask.id) return timeRange
        }
        throw Exception("target task not found, cant calculate time range")
    }

    // Overdue detection

    fun hasOverdueTasks(): Boolean {
        return _tasks.any { task ->
            val timeRange = calcTimeRange(task)
            val diffSeconds = task.fixedTimeDiffSeconds(timeRange.startTime)
            diffSeconds != null && diffSeconds < -60 && !task.taskTimerRunning
        }
    }

    fun getNextOverdueTask(): Pair<String, String>? {
        val overdueTasks = _tasks.filter { task ->
            val timeRange = calcTimeRange(task)
            val diffSeconds = task.fixedTimeDiffSeconds(timeRange.startTime)
            diffSeconds != null && diffSeconds < -60
        }
        val nextOverdueTask = overdueTasks.firstOrNull() ?: return null
        val timeRange = calcTimeRange(nextOverdueTask)
        val diffSeconds = nextOverdueTask.fixedTimeDiffSeconds(timeRange.startTime) ?: 0L
        return Pair(nextOverdueTask.getDisplayName(), formatLateness(diffSeconds))
    }

    private fun formatLateness(diffSeconds: Long): String {
        val absDiffSeconds = Math.abs(diffSeconds)
        val hours = absDiffSeconds / 3600
        val minutes = (absDiffSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m late"
            minutes > 0 -> "${minutes}m late"
            else -> "late"
        }
    }

    // Scheduled-time organization

    fun organizeTasksByScheduledTime() {
        viewModelScope.launch {
            taskListMutex.withLock {
                if (_tasks.isEmpty()) return@withLock
                val currentTime = System.currentTimeMillis()

                val tasksWithFixedTime = _tasks
                    .filter { it.fixedStartTime != null }
                    .sortedBy { it.fixedStartTime!! }
                if (tasksWithFixedTime.isEmpty()) return@withLock

                val tasksWithoutFixedTime = _tasks
                    .filter { it.fixedStartTime == null }
                    .sortedBy { _tasks.indexOf(it) }

                val timeSlots = mutableListOf<TimeSlot>()
                val firstScheduledTask = tasksWithFixedTime.first()
                timeSlots.add(TimeSlot(currentTime, firstScheduledTask.fixedStartTime!!, emptyList()))
                for (i in 0 until tasksWithFixedTime.size - 1) {
                    val startTask = tasksWithFixedTime[i]
                    val endTask = tasksWithFixedTime[i + 1]
                    val slotStartTime = startTask.fixedStartTime!! + startTask.originalDuration
                    val slotEndTime = endTask.fixedStartTime!!
                    if (slotEndTime > slotStartTime) {
                        timeSlots.add(TimeSlot(slotStartTime, slotEndTime, emptyList()))
                    }
                }
                val lastScheduledTask = tasksWithFixedTime.last()
                timeSlots.add(
                    TimeSlot(lastScheduledTask.fixedStartTime!! + lastScheduledTask.originalDuration, Long.MAX_VALUE, emptyList())
                )

                val assignedSlots = timeSlots.map { it.copy(tasks = mutableListOf()) }
                for (task in tasksWithoutFixedTime) {
                    var assigned = false
                    for (slot in assignedSlots) {
                        val totalTimeNeededForSlot = slot.tasks.sumOf { it.originalDuration }
                        val remainingTime = (slot.endTime - slot.startTime) - totalTimeNeededForSlot
                        if (remainingTime >= task.originalDuration) {
                            (slot.tasks as MutableList).add(task)
                            assigned = true
                            break
                        }
                    }
                    if (!assigned) {
                        (assignedSlots.last().tasks as MutableList).add(task)
                    }
                }

                val newTaskOrder = mutableListOf<TaskViewModel>()
                newTaskOrder.addAll(assignedSlots[0].tasks)
                for (i in tasksWithFixedTime.indices) {
                    newTaskOrder.add(tasksWithFixedTime[i])
                    if (i + 1 < assignedSlots.size - 1) {
                        newTaskOrder.addAll(assignedSlots[i + 1].tasks)
                    }
                }
                newTaskOrder.addAll(assignedSlots.last().tasks)

                _tasks.filter { it.taskTimerRunning }.forEach { it.stop() }
                _tasks.clear()
                _tasks.addAll(newTaskOrder)
                updateTaskPositionsAsync()
            }
        }
    }

    // Saved lists

    fun saveCurrentList(name: String) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val tasks = _tasks.map { task ->
                    Task(
                        id = task.id,
                        position = task.position,
                        name = task.getDisplayName(),
                        durationStr = task.durationStr,
                        originalDuration = task.originalDuration,
                        remainingDurationAtResume = task.remainingDurationAtResume,
                        lastResumedTime = 0L,
                        taskTimerRunning = false,
                        fixedStartTime = null
                    )
                }
                taskRepository.saveNewList(name, tasks)
                loadSavedLists()
            }
        }
    }

    fun loadSavedLists() {
        viewModelScope.launch {
            taskListMutex.withLock {
                _savedLists.value = taskRepository.getSavedLists()
            }
        }
    }

    fun loadSavedList(listId: Int) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val savedTasks = taskRepository.getTasksFromSavedList(listId)
                _tasks.forEach { it.cleanup() }
                _tasks.clear()
                savedTasks.sortedBy { it.position }.forEach { savedTask ->
                    val (cleanName, fixedStartTime) = processTimePrefix(savedTask.name)
                    val task = TaskViewModel(cleanName, savedTask.durationStr, taskRepository, viewModelScope)
                    task.position = _tasks.size
                    task.fixedStartTime = fixedStartTime ?: savedTask.fixedStartTime
                    _tasks.add(task)
                }
                _tasks.forEachIndexed { index, t -> t.position = index }
                cursor.position = _tasks.size
                updateCursorPositionState()
                replaceAllTasksInDb()
                _scrollToTopTrigger.value += 1
            }
            updateTaskPositions()
        }
    }

    fun appendSavedList(listId: Int) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val savedTasks = taskRepository.getTasksFromSavedList(listId)
                savedTasks.sortedBy { it.position }.forEach { savedTask ->
                    val (cleanName, fixedStartTime) = processTimePrefix(savedTask.name)
                    val task = TaskViewModel(cleanName, savedTask.durationStr, taskRepository, viewModelScope)
                    task.position = _tasks.size
                    task.fixedStartTime = fixedStartTime ?: savedTask.fixedStartTime
                    _tasks.add(task)
                }
                _tasks.forEachIndexed { index, t -> t.position = index }
                replaceAllTasksInDb()
            }
            updateTaskPositions()
        }
    }

    fun deleteSavedList(listId: Int) {
        viewModelScope.launch {
            taskListMutex.withLock {
                taskRepository.deleteSavedList(listId)
                loadSavedLists()
            }
        }
    }

    fun updateSavedList(listId: Int, name: String) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val tasks = _tasks.map { task ->
                    Task(
                        id = task.id,
                        position = task.position,
                        name = task.getDisplayName(),
                        durationStr = task.durationStr,
                        originalDuration = task.originalDuration,
                        remainingDurationAtResume = task.remainingDurationAtResume,
                        lastResumedTime = 0L,
                        taskTimerRunning = false,
                        fixedStartTime = null
                    )
                }
                taskRepository.updateSavedList(listId, name, tasks)
                loadSavedLists()
            }
        }
    }

    suspend fun createNewList(name: String): Int {
        return taskListMutex.withLock {
            val newListId = taskRepository.saveNewList(name, emptyList())
            loadSavedLists()
            newListId.toInt()
        }
    }

    fun moveTaskToSavedList(task: TaskViewModel, savedListId: Int) {
        viewModelScope.launch {
            taskListMutex.withLock {
                taskRepository.addTaskToSavedList(task, savedListId)
                task.cleanup()
                _tasks.remove(task)
                _tasks.forEachIndexed { index, t -> t.position = index }
                taskRepository.deleteTask(task)
                loadSavedLists()
            }
            updateTaskPositions()
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            taskListMutex.withLock {
                _tasks.forEach { it.cleanup() }
                _tasks.clear()
                replaceAllTasksInDb()
                cursor.position = 0
                updateCursorPositionState()
            }
        }
    }

    fun formatTaskListForClipboard(): String {
        if (_tasks.isEmpty()) return ""
        return _tasks.joinToString("\n") { task ->
            val minutes = (task.originalDuration / 60000L).toInt()
            "${task.getDisplayName()} $minutes"
        }
    }

    fun exportAllTemplatesForClipboard(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val lists = taskRepository.getSavedLists()
            if (lists.isEmpty()) {
                onResult("")
                return@launch
            }
            val builder = StringBuilder()
            lists.forEachIndexed { index, list ->
                if (index > 0) builder.append("\n\n")
                builder.append("=== ").append(list.name).append(" ===")
                val tasks = taskRepository.getTasksFromSavedList(list.id).sortedBy { it.position }
                tasks.forEach { task ->
                    val (cleanName, fixedStartTime) = processTimePrefix(task.name)
                    val effectiveFixedStartTime = fixedStartTime ?: task.fixedStartTime
                    val taskNameWithTimePrefix = if (effectiveFixedStartTime != null && !task.name.matches(Regex("^\\d+.*"))) {
                        val scheduledDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(effectiveFixedStartTime), ZoneId.systemDefault())
                        val hour = scheduledDateTime.hour
                        val minute = scheduledDateTime.minute
                        val amPm = if (hour >= 12) "p" else "a"
                        val hour12 = if (hour % 12 != 0) hour % 12 else 12
                        val formattedTime = "$hour12:${String.format("%02d", minute)}$amPm"
                        "$formattedTime $cleanName"
                    } else {
                        task.name
                    }
                    builder.append("\n").append(taskNameWithTimePrefix)
                    if (task.durationStr.isNotEmpty()) {
                        builder.append(" ").append(task.durationStr)
                    }
                }
            }
            onResult(builder.toString())
        }
    }

    override fun onCleared() {
        super.onCleared()
        _tasks.forEach { it.cleanup() }
        _tasks.clear()
    }
}

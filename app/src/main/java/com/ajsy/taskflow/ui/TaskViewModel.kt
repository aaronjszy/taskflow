package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ajsy.taskflow.db.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class TaskViewModel(
    name: String,
    durationStr: String,
    private val taskRepository: TaskRepository,
    private val viewModelScope: CoroutineScope
) {
    var id: String = UUID.randomUUID().toString()
    var position: Int = 0

    var name: String by mutableStateOf(name)
    var durationStr: String by mutableStateOf(durationStr)
    var originalDuration: Long by mutableStateOf(stringMinutesToLongMillis(durationStr))
    var taskTimerRunning: Boolean by mutableStateOf(false)
    var hasTimedOut: Boolean by mutableStateOf(false)
    var fixedStartTime: Long? by mutableStateOf(null)
    var bufferSeconds: Long = 0L

    // Snapshot of remaining time at last resume/pause — persisted to DB
    var remainingDurationAtResume: Long = stringMinutesToLongMillis(durationStr)
    // Epoch millis when timer was last resumed — persisted to DB
    var lastResumedTime: Long = 0L

    // Live display value updated by the tick loop
    private var _timerDisplayTime: Long by mutableStateOf(stringMinutesToLongMillis(durationStr))

    val displayRemainingDuration: Long
        get() = _timerDisplayTime

    private var timerJob: Job? = null

    data class Progress(val bufferProgress: Float = 0f, val taskProgress: Float = 0f)

    fun start() {
        timerJob?.cancel()
        taskTimerRunning = true
        hasTimedOut = false
        if (lastResumedTime <= 0L) {
            lastResumedTime = System.currentTimeMillis()
        }
        _timerDisplayTime = if (bufferSeconds > 0) {
            bufferSeconds * 1000L + remainingDurationAtResume
        } else {
            remainingDurationAtResume
        }

        viewModelScope.launch {
            taskRepository.updateTaskTimerRunningState(this@TaskViewModel)
        }

        timerJob = viewModelScope.launch {
            while (isActive && taskTimerRunning && lastResumedTime > 0L) {
                delay(500L)
                if (!isActive || !taskTimerRunning || lastResumedTime <= 0L) break
                val elapsed = System.currentTimeMillis() - lastResumedTime
                val current = bufferSeconds * 1000L + remainingDurationAtResume - elapsed
                _timerDisplayTime = current
                if (current <= 0L && !hasTimedOut) {
                    hasTimedOut = true
                }
                taskRepository.updateTaskTimerProgressDirect(
                    id,
                    remainingDurationAtResume,
                    lastResumedTime
                )
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        if (lastResumedTime > 0L) {
            val elapsed = System.currentTimeMillis() - lastResumedTime
            val totalRemaining = (bufferSeconds * 1000L + remainingDurationAtResume - elapsed).coerceAtLeast(0L)
            remainingDurationAtResume = totalRemaining.coerceAtMost(originalDuration)
        }
        lastResumedTime = 0L
        taskTimerRunning = false
        hasTimedOut = false
        _timerDisplayTime = remainingDurationAtResume

        viewModelScope.launch {
            taskRepository.updateTaskTimerRunningState(this@TaskViewModel)
            taskRepository.updateTaskTimerProgressDirect(id, remainingDurationAtResume, lastResumedTime)
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        taskTimerRunning = false
        hasTimedOut = false
        remainingDurationAtResume = originalDuration
        lastResumedTime = 0L
        _timerDisplayTime = originalDuration

        viewModelScope.launch {
            taskRepository.updateTaskTimerRunningState(this@TaskViewModel)
            taskRepository.updateTaskTimerProgressDirect(id, remainingDurationAtResume, lastResumedTime)
        }
    }

    fun cancelTimerAsync() {
        resetTimer()
    }

    fun cleanup() {
        timerJob?.cancel()
        timerJob = null
        taskTimerRunning = false
        hasTimedOut = false
        _timerDisplayTime = 0L
    }

    fun resetTimeoutFlag() {
        hasTimedOut = false
    }

    internal fun updateTimerDisplayTime(time: Long) {
        _timerDisplayTime = time
    }

    fun updateBufferTime(priorEndTime: LocalDateTime) {
        val fst = fixedStartTime
        if (fst != null) {
            val fixedDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(fst), ZoneId.systemDefault())
            bufferSeconds = ChronoUnit.SECONDS.between(priorEndTime, fixedDateTime)
        } else {
            bufferSeconds = 0L
        }
    }

    fun calculateProgress(): Progress {
        return if (displayRemainingDuration > originalDuration) {
            val bufferTotal = bufferSeconds * 1000L
            val bufferRemaining = displayRemainingDuration - originalDuration
            Progress(bufferProgress = 1f - (bufferRemaining.toFloat() / bufferTotal.toFloat()))
        } else {
            val taskProgress = (1f - (displayRemainingDuration.toFloat() / originalDuration.toFloat())).coerceAtMost(1f)
            Progress(taskProgress = taskProgress)
        }
    }

    fun formatTimeRemaining(): String {
        val seconds = (displayRemainingDuration / 1000L).toInt()
        val originalSeconds = (originalDuration / 1000L).toInt()
        if (bufferSeconds > 0 && seconds > originalSeconds) {
            val bufferRemaining = seconds - originalSeconds
            return formatSeconds(bufferRemaining) + " + " + formatSeconds(originalSeconds)
        }
        return formatSeconds(seconds)
    }

    fun fixedTimeDiffSeconds(comparisonDateTime: LocalDateTime): Long? {
        val fst = fixedStartTime ?: return null
        val fixedDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(fst), ZoneId.systemDefault())
        return ChronoUnit.SECONDS.between(comparisonDateTime, fixedDateTime)
    }

    fun formatFixedTimeDiff(comparisonDateTime: LocalDateTime): String? {
        val diffSeconds = fixedTimeDiffSeconds(comparisonDateTime) ?: return null
        val absDiffSeconds = Math.abs(diffSeconds)
        val hours = absDiffSeconds / 3600
        val minutes = (absDiffSeconds % 3600) / 60
        val seconds = absDiffSeconds % 60
        return when {
            diffSeconds < 0 -> "${hours}h ${minutes}m late"
            hours > 0 -> "in ${hours}h ${minutes}m"
            minutes > 0 -> "in ${minutes}m"
            else -> "in ${seconds}s"
        }
    }

    fun getDisplayName(): String {
        val fst = fixedStartTime ?: return name
        val scheduledDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(fst), ZoneId.systemDefault())
        val hour = scheduledDateTime.hour
        val minute = scheduledDateTime.minute
        val amPm = if (hour >= 12) "p" else "a"
        val hour12 = if (hour % 12 != 0) hour % 12 else 12
        val formattedTime = "$hour12:${String.format("%02d", minute)}$amPm"
        return "$formattedTime $name"
    }

    fun serializeForTaskGroup(): String = "$name $durationStr"

    fun stringMinutesToLongMillis(durationStr: String): Long {
        return try {
            durationStr.toLong() * 60L * 1000L
        } catch (e: NumberFormatException) {
            0L
        }
    }

    private fun formatSeconds(seconds: Int): String {
        val sign = if (seconds < 0) "-" else ""
        val absSeconds = Math.abs(seconds)
        val hours = (absSeconds / 60) / 60
        val minutes = (absSeconds / 60) - (hours * 60)
        val secs = absSeconds % 60
        return if (hours == 0) {
            "$sign$minutes:${String.format("%02d", secs)}"
        } else {
            "$sign$hours:${String.format("%02d", minutes)}:${String.format("%02d", secs)}"
        }
    }
}

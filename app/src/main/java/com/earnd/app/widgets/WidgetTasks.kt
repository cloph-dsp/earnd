package com.earnd.app.widgets

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.DateTimeFormats
import com.earnd.app.domain.TaskBucket
import com.earnd.app.domain.today
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Pending top-level tasks for the widget: overdue, today, undated, then upcoming with their date. */
internal object WidgetTasks {
    /** [whenLabel] is empty for today/undated tasks, otherwise a short date. */
    data class Row(val id: Long, val title: String, val overdue: Boolean, val whenLabel: String)

    private const val MAX_ROWS = 30
    private val ORDER = listOf(TaskBucket.OVERDUE, TaskBucket.TODAY, TaskBucket.NO_DATE, TaskBucket.UPCOMING)

    /** Live rows; Room re-emits whenever the task table changes. */
    fun observe(): Flow<List<Row>> {
        val svc = ServiceLocator.taskService
        return combine(ORDER.map { bucket -> svc.observeBucket(bucket) }) { lists ->
            val today = ServiceLocator.clock.today().toEpochDay()
            lists.flatMapIndexed { i, list ->
                val bucket = ORDER[i]
                list.filter { it.parentId == null && it.completedAt == null }
                    .sortedWith(compareBy({ it.dueEpochDay ?: Long.MAX_VALUE }, { it.dueMinuteOfDay ?: -1 }))
                    .map { t ->
                        val label = when (bucket) {
                            TaskBucket.OVERDUE, TaskBucket.UPCOMING -> t.dueEpochDay?.let { shortDate(it, today) } ?: ""
                            else -> ""
                        }
                        Row(t.id, t.title, bucket == TaskBucket.OVERDUE, label)
                    }
            }.distinctBy { it.id }.take(MAX_ROWS)
        }
    }

    suspend fun load(): List<Row> = observe().first()

    /** Weekday within the coming week, otherwise the date in the user's format. */
    private fun shortDate(epochDay: Long, today: Long): String {
        val fmt = DateTimeFormats.current
        return if (epochDay in (today + 1)..(today + 6)) {
            LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        } else {
            fmt.formatDate(epochDay)
        }
    }
}

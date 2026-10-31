package com.earnd.app.widgets

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.TaskBucket
import kotlinx.coroutines.flow.first

/** Pending top-level tasks for the widget: overdue, today, then upcoming and undated. */
internal object WidgetTasks {
    data class Row(val id: Long, val title: String, val overdue: Boolean)

    private const val MAX_ROWS = 20
    private val ORDER = listOf(TaskBucket.OVERDUE, TaskBucket.TODAY, TaskBucket.UPCOMING, TaskBucket.NO_DATE)

    suspend fun load(): List<Row> {
        val svc = ServiceLocator.taskService
        return ORDER.flatMap { bucket ->
            svc.observeBucket(bucket).first()
                .filter { it.parentId == null && it.completedAt == null }
                .map { Row(it.id, it.title, bucket == TaskBucket.OVERDUE) }
        }.distinctBy { it.id }.take(MAX_ROWS)
    }
}

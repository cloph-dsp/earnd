package com.earnd.app.widgets

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.TaskBucket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Pending top-level tasks for the widget: overdue, today, then undated. Upcoming tasks are left
 * out, so a completed recurring task does not reappear as its next occurrence.
 */
internal object WidgetTasks {
    data class Row(val id: Long, val title: String, val overdue: Boolean)

    private const val MAX_ROWS = 20
    private val ORDER = listOf(TaskBucket.OVERDUE, TaskBucket.TODAY, TaskBucket.NO_DATE)

    /** Live rows; Room re-emits whenever the task table changes. */
    fun observe(): Flow<List<Row>> {
        val svc = ServiceLocator.taskService
        return combine(ORDER.map { bucket -> svc.observeBucket(bucket) }) { lists ->
            lists.flatMapIndexed { i, list ->
                list.filter { it.parentId == null && it.completedAt == null }
                    .map { Row(it.id, it.title, ORDER[i] == TaskBucket.OVERDUE) }
            }.distinctBy { it.id }.take(MAX_ROWS)
        }
    }

    suspend fun load(): List<Row> = observe().first()
}

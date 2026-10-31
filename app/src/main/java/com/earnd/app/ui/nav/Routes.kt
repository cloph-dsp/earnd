package com.earnd.app.ui.nav

/** Navigation routes. Optional ids use -1 for "new". */
object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val TASKS = "tasks"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val APPS = "apps"
    const val PERMISSIONS = "permissions"
    const val HABITS_MANAGE = "habits/manage"

    const val HABIT_EDIT = "habit/edit?id={id}"
    const val HABIT_DETAIL = "habit/{id}"
    const val TASK_EDIT = "task/edit?id={id}"
    const val CHECKIN = "checkin/{habitId}"
    const val PHOTO = "photo/{habitId}"

    fun habitEdit(id: Long = -1) = "habit/edit?id=$id"
    fun habitDetail(id: Long) = "habit/$id"
    fun taskEdit(id: Long = -1) = "task/edit?id=$id"
    fun photo(habitId: Long) = "photo/$habitId"

    const val DEEP_TASK = "earnd://task?id={id}"
    const val DEEP_ADD_TASK = "earnd://add-task"
    const val DEEP_CHECKIN = "earnd://checkin?habitId={habitId}"
}

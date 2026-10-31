package com.earnd.app.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Deep links into the app, handled by MainActivity. */
internal object WidgetLinks {
    fun task(context: Context, id: Long): Intent = view(context, "earnd://task?id=$id")
    fun addTask(context: Context): Intent = view(context, "earnd://add-task")

    fun home(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: view(context, "earnd://home")

    private fun view(context: Context, uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

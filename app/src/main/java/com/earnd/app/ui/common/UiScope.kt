package com.earnd.app.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide scope for UI-initiated writes, so a save is not cancelled when the user
 * navigates away from the screen that started it. Room moves its own work off the main thread.
 */
object UiScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

fun launchUi(block: suspend CoroutineScope.() -> Unit): Job = UiScope.launch(block = block)

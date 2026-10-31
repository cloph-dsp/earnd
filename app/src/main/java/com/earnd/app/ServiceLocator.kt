package com.earnd.app

import android.content.Context
import com.earnd.app.domain.ChangeBus

/**
 * Minimal manual DI. Feature areas add lazily-built singletons here
 * (database, DAOs, TimeBank, HabitService, TaskService, BlockPolicy, AlarmPlanner, NagEngine).
 */
object ServiceLocator {
    lateinit var appContext: Context
        private set

    val changeBus: ChangeBus by lazy { ChangeBus() }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

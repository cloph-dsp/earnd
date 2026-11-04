package com.earnd.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** [key] is a package name, or a host (e.g. "example.com") when [isWebsite]. */
@Entity(tableName = "app_rule")
data class AppRuleEntity(
    @PrimaryKey val key: String,
    val rule: AppRule,
    val isWebsite: Boolean = false,
    val label: String,
    /** Charging rate, percent of foreground time: 0/25/50/100. Every leisure rule is metered; null (legacy FULL) means 100. */
    val meterPct: Int? = 100,
)

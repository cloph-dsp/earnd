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
    /** Charging: null = FULL (needs an unlock session); 0/25/50/100 = METERED at that percent of foreground time. */
    val meterPct: Int? = null,
)

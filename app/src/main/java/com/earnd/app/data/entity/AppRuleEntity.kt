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
)

package com.earnd.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "setting")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

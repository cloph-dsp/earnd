package com.earnd.app.data.entity

import androidx.room.Entity

@Entity(tableName = "nag_state", primaryKeys = ["ownerKind", "ownerId"])
data class NagStateEntity(
    val ownerKind: OwnerKind,
    val ownerId: Long,
    val dueAt: Long,
    val nagCount: Int = 0,
    val lastNagAt: Long? = null,
    val nextNagAt: Long? = null,
    val snoozeCount: Int = 0,
)

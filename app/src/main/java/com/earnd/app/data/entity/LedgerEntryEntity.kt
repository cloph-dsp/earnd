package com.earnd.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ledger", indices = [Index("at"), Index("periodKey")])
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: LedgerKind,
    /** Signed: + earned, - spent/reset/undo. */
    val minutes: Int,
    val sourceKind: OwnerKind? = null,
    /** Check-in id or task id. */
    val sourceId: Long? = null,
    /** epochDay of the start of the period this entry belongs to. */
    val periodKey: Long,
    /** Rolling mode: when the minutes of this earned entry expire. Null in daily/weekly mode and for non-earn rows. */
    val expiresAt: Long? = null,
    /** Rolling mode: unspent part of an earned entry. */
    val remaining: Int = 0,
)

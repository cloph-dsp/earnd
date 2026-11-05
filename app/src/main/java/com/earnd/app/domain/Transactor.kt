package com.earnd.app.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Runs a read-then-write block atomically. Nested calls on the same transactor join the outer one. */
interface Transactor {
    suspend fun <T> run(block: suspend () -> T): T
}

/**
 * In-process serialization with reentrancy. Used directly in tests, and around the Room
 * transaction in production so nested calls never wait on themselves.
 */
class MutexTransactor(private val inner: Transactor? = null) : Transactor {
    private val mutex = Mutex()

    private class Held(val owner: MutexTransactor) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Held>
    }

    override suspend fun <T> run(block: suspend () -> T): T {
        if (coroutineContext[Held]?.owner === this) return block()
        return mutex.withLock {
            withContext(Held(this)) { if (inner != null) inner.run(block) else block() } // not `?:`: a block that returns null would run twice
        }
    }
}

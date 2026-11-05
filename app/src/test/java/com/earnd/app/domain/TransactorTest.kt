package com.earnd.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactorTest {
    @Test fun aBlockThatReturnsNullRunsOnce() = runTest {
        var runs = 0
        val result: String? = MutexTransactor(MutexTransactor()).run { runs++; null }
        assertNull(result)
        assertEquals(1, runs)
    }
}

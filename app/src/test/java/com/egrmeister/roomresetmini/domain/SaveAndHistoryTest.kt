package com.egrmeister.roomresetmini.domain

import com.egrmeister.roomresetmini.domain.sessions.HistoryPolicy
import com.egrmeister.roomresetmini.domain.sessions.ResultKind
import com.egrmeister.roomresetmini.domain.sessions.SaveCoordinator
import com.egrmeister.roomresetmini.domain.sessions.SaveOutcome
import com.egrmeister.roomresetmini.domain.sessions.SessionSaveGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory gateway with the same semantics as the Room DAO: conditional ACTIVE → SAVED, then retention. */
private class FakeStore : SessionSaveGateway {
    data class Row(val id: Long, var saved: Boolean, var savedAt: Long?, val completed: Int, val total: Int)

    val rows = mutableListOf<Row>()
    var markCalls = 0

    fun addActive(id: Long, completed: Int = 0, total: Int = 3) {
        rows += Row(id, saved = false, savedAt = null, completed = completed, total = total)
    }

    override suspend fun markSavedAndPrune(sessionId: Long, savedAtMillis: Long, keep: Int): Boolean {
        markCalls++
        val row = rows.firstOrNull { it.id == sessionId && !it.saved } ?: return false
        row.saved = true
        row.savedAt = savedAtMillis
        val toDelete = HistoryPolicy.idsToDelete(
            rows.filter { it.saved }.map { HistoryPolicy.SavedRef(it.id, it.savedAt ?: 0L) },
            keep,
        )
        rows.removeAll { it.id in toDelete }
        return true
    }

    val history get() = rows.filter { it.saved }
}

class SaveAndHistoryTest {
    private val clocks = FakeClocks()

    @Test
    fun duplicateSavesProduceOneHistoryEntry() = runTest {
        val store = FakeStore().apply { addActive(1, completed = 2) }
        val saver = SaveCoordinator(store, clocks)

        val outcomes = List(5) { async { saver.save(1) } }.awaitAll()

        assertEquals(1, outcomes.count { it == SaveOutcome.SAVED })
        assertEquals(4, outcomes.count { it == SaveOutcome.ALREADY_SAVED })
        assertEquals(1, store.history.size)
        // A later retry is still idempotent.
        assertEquals(SaveOutcome.ALREADY_SAVED, saver.save(1))
        assertEquals(1, store.history.size)
    }

    @Test
    fun partialAndEmptySessionsCanBeSavedWithoutFabricatedCompletion() = runTest {
        val store = FakeStore().apply {
            addActive(1, completed = 1, total = 4)
            addActive(2, completed = 0, total = 4)
            addActive(3, completed = 4, total = 4)
        }
        val saver = SaveCoordinator(store, clocks)
        listOf(1L, 2L, 3L).forEach {
            clocks.advance(1_000L)
            assertEquals(SaveOutcome.SAVED, saver.save(it))
        }
        val byId = store.history.associateBy { it.id }
        assertEquals(ResultKind.PARTIAL, ResultKind.of(byId.getValue(1).completed, byId.getValue(1).total))
        assertEquals(ResultKind.NOTHING_CHECKED, ResultKind.of(byId.getValue(2).completed, byId.getValue(2).total))
        assertEquals(ResultKind.ALL_CHECKED, ResultKind.of(byId.getValue(3).completed, byId.getValue(3).total))
        assertEquals(0, byId.getValue(2).completed)
    }

    @Test
    fun historyKeepsOnlyTheLatest100() = runTest {
        val store = FakeStore()
        val saver = SaveCoordinator(store, clocks)
        for (id in 1L..105L) {
            store.addActive(id, completed = 1)
            clocks.advance(1_000L)
            saver.save(id)
        }
        assertEquals(HistoryPolicy.LIMIT, store.history.size)
        assertEquals((6L..105L).toSet(), store.history.map { it.id }.toSet())
    }

    @Test
    fun retentionOrdersBySavedAtThenId() {
        val refs = listOf(
            HistoryPolicy.SavedRef(1, 100),
            HistoryPolicy.SavedRef(2, 300),
            HistoryPolicy.SavedRef(3, 300),
            HistoryPolicy.SavedRef(4, 200),
        )
        assertEquals(setOf(1L, 4L), HistoryPolicy.idsToDelete(refs, keep = 2))
        assertTrue(HistoryPolicy.idsToDelete(refs, keep = 10).isEmpty())
    }
}

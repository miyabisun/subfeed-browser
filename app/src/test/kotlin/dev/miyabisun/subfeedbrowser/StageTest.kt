package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTest {
    @Test
    fun readsWhetherAVideoTakesTheWholeScreen() {
        assertEquals(true, stageRequest("stage", true))
        assertEquals(false, stageRequest("stage", false))
    }

    @Test
    fun ignoresMessagesThatAreNoSuchRequest() {
        assertNull(stageRequest("back", true))
        assertNull(stageRequest(null, true))
        assertNull(stageRequest("stage", "true"))
        assertNull(stageRequest("stage", 1))
        assertNull(stageRequest("stage", null))
    }

    @Test
    fun goesBackWhenThePageAsks() {
        assertTrue(asksToGoBack("back"))
        assertFalse(asksToGoBack("stage"))
        assertFalse(asksToGoBack(null))
        assertFalse(asksToGoBack(listOf("back")))
    }
}

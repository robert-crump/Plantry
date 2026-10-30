package com.example.plantry.ui.recipe

import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.StepsCondenser
import com.example.plantry.data.claude.StepsReader
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StepsAssistantTest {

    private class FakeCondenser : StepsCondenser {
        val requests = mutableListOf<Triple<String, ScanModel, String>>()
        val answer = CompletableDeferred<ClaudeResult<List<String>>>()

        override suspend fun condense(apiKey: String, model: ScanModel, text: String): ClaudeResult<List<String>> {
            requests += Triple(apiKey, model, text)
            return answer.await()
        }
    }

    private class FakeReader : StepsReader {
        val requests = mutableListOf<List<ByteArray>>()
        val answer = CompletableDeferred<ClaudeResult<List<String>>>()

        override suspend fun read(apiKey: String, model: ScanModel, pages: List<ByteArray>): ClaudeResult<List<String>> {
            requests += pages
            return answer.await()
        }
    }

    private val long = "1. Den Ofen auf 200 °C vorheizen, damit er richtig heiß ist.\n2. Die Zwiebel schälen.\n3. Die Zwiebel würfeln."

    private var field = long
    private val condenser = FakeCondenser()
    private val reader = FakeReader()
    private val page1 = byteArrayOf(1)
    private val page2 = byteArrayOf(2)
    private val page3 = byteArrayOf(3)
    private var pages = listOf(page1, page2, page3)

    private fun TestScope.assistant(apiKey: String? = "key") = StepsAssistant(
        scope = backgroundScope,
        condenser = condenser,
        reader = reader,
        apiKey = { apiKey },
        model = { ScanModel.SONNET },
        text = { field },
        setText = { field = it },
        pages = { pages },
    )

    @Test
    fun condense_sendsTheFieldWithTheScanModel_andLocksWhileWorking() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()

        steps.condense()

        assertEquals(listOf(Triple("key", ScanModel.SONNET, long)), condenser.requests)
        assertTrue(steps.state.value.running)
        // A second tap while working sends nothing.
        steps.condense()
        assertEquals(1, condenser.requests.size)
    }

    @Test
    fun result_replacesTheText_andUndoRestoresIt() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()
        steps.condense()

        condenser.answer.complete(ClaudeResult.Success(listOf("Ofen auf 200 °C vorheizen.", "Zwiebel würfeln.")))

        assertEquals("Ofen auf 200 °C vorheizen.\nZwiebel würfeln.", field)
        assertEquals(StepsWork(undoText = long), steps.state.value)

        steps.undo()

        assertEquals(long, field)
        assertNull(steps.state.value.undoText)
    }

    @Test
    fun undoExpired_keepsTheNewText() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()
        steps.condense()
        condenser.answer.complete(ClaudeResult.Success(listOf("Zwiebel würfeln.")))

        steps.undoExpired()
        steps.undo()

        assertEquals("Zwiebel würfeln.", field)
    }

    @Test
    fun failure_keepsTheText_andShowsTheReason() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()
        steps.condense()

        condenser.answer.complete(ClaudeResult.Failure(ClaudeFailure.OVERLOADED))

        assertEquals(long, field)
        assertEquals(StepsWork(failure = ClaudeFailure.OVERLOADED), steps.state.value)
        steps.failureShown()
        assertEquals(StepsWork(), steps.state.value)
    }

    @Test
    fun withoutKey_failsWithoutACall() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant(apiKey = null)

        steps.condense()

        assertEquals(StepsWork(failure = ClaudeFailure.NO_API_KEY), steps.state.value)
        assertTrue(condenser.requests.isEmpty())
    }

    @Test
    fun blankField_sendsNothing() = runTest(UnconfinedTestDispatcher()) {
        field = "  \n "
        val steps = assistant()

        steps.condense()

        assertTrue(condenser.requests.isEmpty())
        assertFalse(steps.state.value.running)
    }

    @Test
    fun cancel_dropsALateAnswer() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()
        steps.condense()

        steps.cancel()
        condenser.answer.complete(ClaudeResult.Success(listOf("Zwiebel würfeln.")))

        assertEquals(long, field)
        assertEquals(StepsWork(), steps.state.value)
    }

    @Test
    fun readPhotos_sendsAllPagesInOrder_intoAnEmptyField() = runTest(UnconfinedTestDispatcher()) {
        field = ""
        val steps = assistant()

        steps.readPhotos()

        assertEquals(listOf(listOf(page1, page2, page3)), reader.requests)
        assertEquals(StepsWork(running = true, task = StepsTask.READ_PHOTOS), steps.state.value)
        reader.answer.complete(ClaudeResult.Success(listOf("Ofen auf 200 °C vorheizen.", "Gemüse rösten.")))

        assertEquals("Ofen auf 200 °C vorheizen.\nGemüse rösten.", field)
        assertEquals(StepsWork(task = StepsTask.READ_PHOTOS, undoText = ""), steps.state.value)
    }

    @Test
    fun readPhotos_asksBeforeReplacingText_andUndoRestoresIt() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()

        steps.readPhotos()

        assertTrue(reader.requests.isEmpty())
        assertTrue(steps.state.value.confirmReplace)

        steps.readPhotos(confirmed = true)
        reader.answer.complete(ClaudeResult.Success(listOf("Gemüse rösten.")))

        assertEquals(1, reader.requests.size)
        assertEquals("Gemüse rösten.", field)
        assertFalse(steps.state.value.confirmReplace)
        steps.undo()
        assertEquals(long, field)
    }

    @Test
    fun readPhotos_cancelledConfirmation_sendsNothing() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()
        steps.readPhotos()

        steps.dismissReplace()

        assertEquals(StepsWork(), steps.state.value)
        assertTrue(reader.requests.isEmpty())
        assertEquals(long, field)
    }

    @Test
    fun readPhotos_noMethod_leavesTheText() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()
        steps.readPhotos(confirmed = true)

        reader.answer.complete(ClaudeResult.Success(emptyList()))

        assertEquals(long, field)
        assertEquals(StepsWork(task = StepsTask.READ_PHOTOS, noneFound = true), steps.state.value)
        steps.failureShown()
        assertFalse(steps.state.value.noneFound)
    }

    @Test
    fun readPhotos_failure_keepsTheText() = runTest(UnconfinedTestDispatcher()) {
        val steps = assistant()
        steps.readPhotos(confirmed = true)

        reader.answer.complete(ClaudeResult.Failure(ClaudeFailure.TRUNCATED))

        assertEquals(long, field)
        assertEquals(StepsWork(task = StepsTask.READ_PHOTOS, failure = ClaudeFailure.TRUNCATED), steps.state.value)
    }

    @Test
    fun readPhotos_withoutPhotos_sendsNothing() = runTest(UnconfinedTestDispatcher()) {
        pages = emptyList()
        field = ""
        val steps = assistant()

        steps.readPhotos()

        assertTrue(reader.requests.isEmpty())
        assertEquals(StepsWork(), steps.state.value)
    }

    @Test
    fun readPhotos_cancel_dropsALateAnswer() = runTest(UnconfinedTestDispatcher()) {
        field = ""
        val steps = assistant()
        steps.readPhotos()

        steps.cancel()
        reader.answer.complete(ClaudeResult.Success(listOf("Gemüse rösten.")))

        assertEquals("", field)
        assertEquals(StepsWork(), steps.state.value)
    }
}

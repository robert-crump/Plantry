package com.example.plantry.ui.recipe

import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.StepsCondenser
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

    private val long = "1. Den Ofen auf 200 °C vorheizen, damit er richtig heiß ist.\n2. Die Zwiebel schälen.\n3. Die Zwiebel würfeln."

    private var field = long
    private val condenser = FakeCondenser()

    private fun TestScope.assistant(apiKey: String? = "key") = StepsAssistant(
        scope = backgroundScope,
        condenser = condenser,
        apiKey = { apiKey },
        model = { ScanModel.SONNET },
        text = { field },
        setText = { field = it },
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
}

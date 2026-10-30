package com.example.plantry.ui.recipe

import com.example.plantry.data.CookingSteps
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.StepsCondenser
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Claude working on the Zubereitung field, see [StepsAssistant]. */
data class StepsWork(
    /** Claude is working; the field is locked. */
    val running: Boolean = false,
    /** The last call failed; the text was left as it was. */
    val failure: ClaudeFailure? = null,
    /** The text from before Claude replaced it, while "Rückgängig" is offered. */
    val undoText: String? = null,
)

/**
 * Kürzen: has Claude condense the Zubereitung field ([text], [setText]). The answer replaces the
 * text right away, so the unsaved editor is the preview; [undo] puts the text from before back.
 */
class StepsAssistant(
    private val scope: CoroutineScope,
    private val condenser: StepsCondenser,
    private val apiKey: () -> String?,
    private val model: () -> ScanModel,
    private val text: () -> String,
    private val setText: (String) -> Unit,
) {
    private val _state = MutableStateFlow(StepsWork())
    val state: StateFlow<StepsWork> = _state.asStateFlow()

    /** Cancelled when the editor is left, so a late answer can't fill the form. */
    private var job: Job? = null

    fun condense() {
        val before = text()
        if (before.isBlank() || _state.value.running) return
        val key = apiKey()
        if (key == null) {
            _state.value = StepsWork(failure = ClaudeFailure.NO_API_KEY)
            return
        }
        _state.value = StepsWork(running = true)
        job = scope.launch {
            when (val result = condenser.condense(key, model(), before)) {
                is ClaudeResult.Success -> {
                    setText(CookingSteps.text(result.value))
                    _state.value = StepsWork(undoText = before)
                }
                is ClaudeResult.Failure -> _state.value = StepsWork(failure = result.reason)
            }
        }
    }

    fun undo() {
        val before = _state.value.undoText ?: return
        setText(before)
        _state.update { it.copy(undoText = null) }
    }

    /** "Rückgängig" was not tapped in time; the new text stays. */
    fun undoExpired() {
        _state.update { it.copy(undoText = null) }
    }

    fun failureShown() {
        _state.update { it.copy(failure = null) }
    }

    fun cancel() {
        job?.cancel()
        _state.value = StepsWork()
    }
}

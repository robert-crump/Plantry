package com.example.plantry.ui.recipe

import com.example.plantry.data.CookingSteps
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.StepsCondenser
import com.example.plantry.data.claude.StepsReader
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Claude does with the Zubereitung field. */
enum class StepsTask { CONDENSE, READ_PHOTOS }

/** Claude working on the Zubereitung field, see [StepsAssistant]. */
data class StepsWork(
    /** Claude is working; the field is locked. */
    val running: Boolean = false,
    /** What runs, or ran last. */
    val task: StepsTask = StepsTask.CONDENSE,
    /** Aus Fotos lesen waits for "Ersetzen", because the field has text. */
    val confirmReplace: Boolean = false,
    /** Aus Fotos lesen found no method on the photos; the text was left as it was. */
    val noneFound: Boolean = false,
    /** The last call failed; the text was left as it was. */
    val failure: ClaudeFailure? = null,
    /** The text from before Claude replaced it, while "Rückgängig" is offered. */
    val undoText: String? = null,
)

/**
 * Has Claude fill the Zubereitung field ([text], [setText]): Kürzen condenses its text, Aus Fotos
 * lesen reads the method from the recipe's page photos ([pages]). The answer replaces the text
 * right away, so the unsaved editor is the preview; [undo] puts the text from before back.
 */
class StepsAssistant(
    private val scope: CoroutineScope,
    private val condenser: StepsCondenser,
    private val reader: StepsReader,
    private val apiKey: () -> String?,
    private val model: () -> ScanModel,
    private val text: () -> String,
    private val setText: (String) -> Unit,
    private val pages: () -> List<ByteArray>,
) {
    private val _state = MutableStateFlow(StepsWork())
    val state: StateFlow<StepsWork> = _state.asStateFlow()

    /** Cancelled when the editor is left, so a late answer can't fill the form. */
    private var job: Job? = null

    fun condense() {
        val before = text()
        if (before.isBlank()) return
        run(StepsTask.CONDENSE) { key, model -> condenser.condense(key, model, before) }
    }

    /** Aus Fotos lesen over all [pages] in order; text in the field is only replaced once [confirmed]. */
    fun readPhotos(confirmed: Boolean = false) {
        val photos = pages()
        if (photos.isEmpty()) return
        if (!confirmed && text().isNotBlank()) {
            _state.update { it.copy(confirmReplace = true) }
            return
        }
        run(StepsTask.READ_PHOTOS) { key, model -> reader.read(key, model, photos) }
    }

    fun dismissReplace() {
        _state.update { it.copy(confirmReplace = false) }
    }

    private fun run(task: StepsTask, call: suspend (String, ScanModel) -> ClaudeResult<List<String>>) {
        if (_state.value.running) return
        val key = apiKey()
        if (key == null) {
            _state.value = StepsWork(task = task, failure = ClaudeFailure.NO_API_KEY)
            return
        }
        val before = text()
        _state.value = StepsWork(running = true, task = task)
        job = scope.launch {
            val result = call(key, model())
            _state.value = when (result) {
                is ClaudeResult.Failure -> StepsWork(task = task, failure = result.reason)
                // No method on the photos is no reason to empty the field.
                is ClaudeResult.Success -> if (result.value.isEmpty() && task == StepsTask.READ_PHOTOS) {
                    StepsWork(task = task, noneFound = true)
                } else {
                    setText(CookingSteps.text(result.value))
                    StepsWork(task = task, undoText = before)
                }
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
        _state.update { it.copy(failure = null, noneFound = false) }
    }

    fun cancel() {
        job?.cancel()
        _state.value = StepsWork()
    }
}

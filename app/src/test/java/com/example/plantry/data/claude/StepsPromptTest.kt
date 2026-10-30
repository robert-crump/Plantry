package com.example.plantry.data.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class StepsPromptTest {

    @Test
    fun `condense prompt carries the shared step rules`() {
        assertTrue(StepsPrompt.CONDENSE_SYSTEM.endsWith(StepRules.RULES))
        listOf("never translate", "Usually 3 to 5 steps", "At most two sentences", "bis goldbraun", "never pad", "Never invent steps")
            .forEach { assertTrue(it, StepRules.RULES.contains(it)) }
    }

    @Test
    fun `read prompt carries the shared step rules`() {
        assertTrue(StepsPrompt.READ_SYSTEM.endsWith(StepRules.RULES))
        assertTrue(StepsPrompt.READ_SYSTEM.contains("The pages come in order"))
    }

    @Test
    fun `read message sends every page in order, then the request`() {
        val pages = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))

        val blocks = StepsPrompt.readMessage(pages)

        assertEquals(4, blocks.size)
        val images = blocks.take(3).map { it.asImage().source().asBase64().data() }
        assertEquals(pages.map { Base64.getEncoder().encodeToString(it) }, images)
        assertEquals("Lies die Zubereitung von diesen 3 Seiten, in dieser Reihenfolge.", blocks.last().asText().text())
    }

    @Test
    fun `code and seed rules point at each other`() {
        val seed = listOf(File("../tools/seed/step-rules.md"), File("tools/seed/step-rules.md")).first { it.exists() }
        assertTrue(seed.readText(Charsets.UTF_8).contains("data/claude/StepRules.kt"))
        val code = listOf(
            File("src/main/java/com/example/plantry/data/claude/StepRules.kt"),
            File("app/src/main/java/com/example/plantry/data/claude/StepRules.kt"),
        ).first { it.exists() }
        assertTrue(code.readText(Charsets.UTF_8).contains("tools/seed/step-rules.md"))
    }

    @Test
    fun `message holds the field's text`() {
        assertEquals("Zubereitung:\nZwiebel anbraten.\nReis kochen.", StepsPrompt.condenseMessage("  Zwiebel anbraten.\nReis kochen.\n"))
    }

    @Test
    fun `steps are cleaned up like typed ones`() {
        val result = StepsParser.steps("""{"steps": [" 1. Ofen auf 200 °C vorheizen. ", "", "Gemüse rösten.\nDressing anrühren."]}""")

        assertEquals(
            ClaudeResult.Success(listOf("Ofen auf 200 °C vorheizen.", "Gemüse rösten.", "Dressing anrühren.")),
            result,
        )
    }

    @Test
    fun `no method gives no steps`() {
        assertEquals(ClaudeResult.Success(emptyList<String>()), StepsParser.steps("""{"steps": []}"""))
    }

    @Test
    fun `unreadable answers are bad responses`() {
        listOf(null, "", "nope", """{"other": 1}""").forEach {
            assertEquals(it, ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE), StepsParser.steps(it))
        }
    }
}

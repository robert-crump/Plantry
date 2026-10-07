package com.example.plantry.data.claude

import com.example.plantry.data.settings.ScanModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ModelCatalogTest {

    private var now = Instant.parse("2026-10-07T10:00:00Z")
    private var fetches = 0
    private var listing = listOf(
        ListedModel("claude-opus-5-5", "Claude Opus 5.5", Instant.parse("2026-05-01T00:00:00Z")),
        ListedModel("claude-sonnet-5-5", "Claude Sonnet 5.5", Instant.parse("2026-05-01T00:00:00Z")),
        ListedModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5", Instant.parse("2025-10-01T00:00:00Z")),
    )
    private var failing = false
    private val catalog = ModelCatalog(
        fetch = {
            fetches++
            if (failing) error("offline")
            listing
        },
        now = { now },
    )

    @Test
    fun resolve_takesTheNewestModelOfTheTier() {
        listing += ListedModel("claude-opus-5-6", "Claude Opus 5.6", Instant.parse("2026-09-01T00:00:00Z"))

        assertEquals(ResolvedModel("claude-opus-5-6", "Opus 5.6"), catalog.resolve("key", ScanModel.OPUS))
        assertEquals(ResolvedModel("claude-sonnet-5-5", "Sonnet 5.5"), catalog.resolve("key", ScanModel.SONNET))
    }

    @Test
    fun resolve_fetchesOncePerDay() {
        catalog.resolve("key", ScanModel.OPUS)
        catalog.resolve("key", ScanModel.SONNET)
        assertEquals(1, fetches)

        now += Duration.ofHours(25)
        catalog.resolve("key", ScanModel.OPUS)
        assertEquals(2, fetches)
    }

    @Test
    fun resolve_fallsBackWhenTheListCannotBeFetched() {
        failing = true

        assertEquals(ScanModel.OPUS.fallback, catalog.resolve("key", ScanModel.OPUS))
        assertEquals(ScanModel.SONNET.fallback, catalog.peek(ScanModel.SONNET))
    }

    @Test
    fun resolve_fallsBackWhenTheListHasNoModelOfTheTier() {
        listing = listing.filter { "opus" !in it.id }

        assertEquals(ScanModel.OPUS.fallback, catalog.resolve("key", ScanModel.OPUS))
    }

    @Test
    fun peek_returnsTheLastResolution() {
        listing += ListedModel("claude-sonnet-5-6", "Claude Sonnet 5.6", Instant.parse("2026-09-01T00:00:00Z"))
        catalog.resolve("key", ScanModel.OPUS)

        assertEquals("Sonnet 5.6", catalog.peek(ScanModel.SONNET).name)
    }

    @Test
    fun scanModel_readsTheTierAndTheIdOlderVersionsStored() {
        assertEquals(ScanModel.SONNET, ScanModel.fromStored("sonnet"))
        assertEquals(ScanModel.SONNET, ScanModel.fromStored("claude-sonnet-5-5"))
        assertEquals(ScanModel.OPUS, ScanModel.fromStored("claude-opus-5-5"))
        assertEquals(ScanModel.DEFAULT, ScanModel.fromStored("gibberish"))
        assertEquals(ScanModel.DEFAULT, ScanModel.fromStored(null))
    }
}

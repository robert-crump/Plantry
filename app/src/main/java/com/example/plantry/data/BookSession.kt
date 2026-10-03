package com.example.plantry.data

/** Where a recipe is printed: the book (source) and the page, if known. */
data class BookPage(val source: String, val page: Int?)

/**
 * Remembers the book of the last scanned recipe, so scanning several recipes of one book in a row
 * needs no retyping. Kept in memory only; a restart starts a new session.
 */
class BookSession {

    private var last: BookPage? = null

    /** Called when a scanned recipe is saved. A recipe without source ends the session. */
    fun remember(source: String, page: Int?) {
        last = source.trim().takeIf { it.isNotEmpty() }?.let { BookPage(it, page) }
    }

    /**
     * Defaults for the next scan: what Claude read wins; a missing source is carried over from the
     * last scan, and a missing page is the one after the last scanned page of that book.
     */
    fun defaults(scanned: BookPage): BookPage {
        val previous = last
        val source = scanned.source.trim().ifEmpty { previous?.source.orEmpty() }
        val sameBook = previous != null && source.equals(previous.source, ignoreCase = true)
        val page = scanned.page ?: previous?.page?.takeIf { sameBook }?.plus(1)
        return BookPage(source, page)
    }
}

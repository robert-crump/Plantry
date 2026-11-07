package com.example.plantry.data

/**
 * Runs a block as one database transaction: on failure none of its writes is kept. The app passes
 * Room's withTransaction; JVM tests on fake DAOs use [None].
 */
class Transaction(private val run: suspend (block: suspend () -> Unit) -> Unit) {

    suspend operator fun <T> invoke(block: suspend () -> T): T {
        var result: Any? = null
        run { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    companion object {
        /** Just runs the block, for fakes that can't roll back. */
        val None = Transaction { it() }
    }
}

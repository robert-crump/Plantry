package com.example.plantry.data

import com.example.plantry.data.backup.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The photos of a recipe's cookbook pages, in order, as compressed JPEG bytes. */
class RecipePhotoRepository(private val store: PhotoStore) {

    /** Bumped on every change, so observers re-read their photos. */
    private val version = MutableStateFlow(0)

    fun observe(recipeId: Long): Flow<List<ByteArray>> =
        version.map { store.list(recipeId) }.flowOn(Dispatchers.IO)

    suspend fun get(recipeId: Long): List<ByteArray> = withContext(Dispatchers.IO) { store.list(recipeId) }

    /** Appends [bytes] as the last page. */
    suspend fun add(recipeId: Long, bytes: ByteArray) {
        withContext(Dispatchers.IO) { store.add(recipeId, bytes) }
        version.update { it + 1 }
    }

    /** Removes the pages at [indexes] of [get]. */
    suspend fun delete(recipeId: Long, indexes: Set<Int>) {
        withContext(Dispatchers.IO) { indexes.sortedDescending().forEach { store.delete(recipeId, it) } }
        version.update { it + 1 }
    }

    suspend fun deleteAll(recipeId: Long) {
        withContext(Dispatchers.IO) { store.deleteAll(recipeId) }
        version.update { it + 1 }
    }
}

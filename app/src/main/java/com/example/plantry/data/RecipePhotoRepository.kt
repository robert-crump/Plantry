package com.example.plantry.data

import com.example.plantry.data.backup.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The photo of a recipe's cookbook page, as compressed JPEG bytes. */
class RecipePhotoRepository(private val store: PhotoStore) {

    /** Bumped on every change, so observers re-read their photo. */
    private val version = MutableStateFlow(0)

    fun observe(recipeId: Long): Flow<ByteArray?> =
        version.map { store.get(recipeId) }.flowOn(Dispatchers.IO)

    suspend fun get(recipeId: Long): ByteArray? = withContext(Dispatchers.IO) { store.get(recipeId) }

    suspend fun save(recipeId: Long, bytes: ByteArray) {
        withContext(Dispatchers.IO) { store.put(recipeId, bytes) }
        version.update { it + 1 }
    }

    suspend fun delete(recipeId: Long) {
        withContext(Dispatchers.IO) { store.delete(recipeId) }
        version.update { it + 1 }
    }
}

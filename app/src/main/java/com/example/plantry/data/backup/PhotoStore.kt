package com.example.plantry.data.backup

import java.io.File

/** Recipe photos, the pages of each recipe in order; blocking, so call off the main thread. */
interface PhotoStore {
    fun all(): Photos
    fun replaceAll(photos: Photos)
    fun list(recipeId: Long): List<ByteArray>

    /** Appends [bytes] as the recipe's last page. */
    fun add(recipeId: Long, bytes: ByteArray)

    /** Removes the page at [index] of [list]. */
    fun delete(recipeId: Long, index: Int)
    fun deleteAll(recipeId: Long)
}

/**
 * JPEGs in [dir] named by recipe id and a number giving their order, e.g. "12-0.jpg", "12-3.jpg";
 * gaps left by deleted pages don't matter. A "12.jpg" from before several pages is the first page
 * and is renamed to "12-0.jpg" when the recipe's pages next change.
 */
class FilePhotoStore(private val dir: File) : PhotoStore {

    override fun all(): Photos =
        dir.listFiles().orEmpty()
            .mapNotNull { file -> page(file)?.let { (recipeId, number) -> Triple(recipeId, number, file) } }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, pages) -> pages.sortedBy { it.first }.map { it.second.readBytes() } }

    override fun replaceAll(photos: Photos) {
        dir.listFiles().orEmpty().forEach { it.delete() }
        dir.mkdirs()
        photos.forEach { (recipeId, pages) -> pages.forEachIndexed { number, bytes -> file(recipeId, number).writeBytes(bytes) } }
    }

    override fun list(recipeId: Long): List<ByteArray> = pages(recipeId).map { it.second.readBytes() }

    override fun add(recipeId: Long, bytes: ByteArray) {
        dir.mkdirs()
        val next = pages(recipeId).maxOfOrNull { it.first }?.plus(1) ?: 0
        file(recipeId, next).writeBytes(bytes)
    }

    override fun delete(recipeId: Long, index: Int) {
        pages(recipeId).getOrNull(index)?.second?.delete()
    }

    override fun deleteAll(recipeId: Long) {
        pages(recipeId).forEach { it.second.delete() }
    }

    /** The recipe's pages by number, in order; renames a legacy file first. */
    private fun pages(recipeId: Long): List<Pair<Int, File>> {
        val legacy = File(dir, "$recipeId$SUFFIX")
        if (legacy.exists()) legacy.renameTo(file(recipeId, 0))
        return dir.listFiles().orEmpty()
            .mapNotNull { file -> page(file)?.takeIf { it.first == recipeId }?.let { it.second to file } }
            .sortedBy { it.first }
    }

    /** Recipe id and page number of [file]; null for anything else. */
    private fun page(file: File): Pair<Long, Int>? {
        if (!file.name.endsWith(SUFFIX)) return null
        val parts = file.name.removeSuffix(SUFFIX).split('-')
        val recipeId = parts[0].toLongOrNull() ?: return null
        return when (parts.size) {
            1 -> recipeId to 0
            2 -> parts[1].toIntOrNull()?.let { recipeId to it }
            else -> null
        }
    }

    private fun file(recipeId: Long, number: Int) = File(dir, "$recipeId-$number$SUFFIX")

    private companion object {
        const val SUFFIX = ".jpg"
    }
}

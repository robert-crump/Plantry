package com.example.plantry.data.backup

import java.io.File

/** Recipe photos, one per recipe; blocking, so call off the main thread. */
interface PhotoStore {
    fun all(): Photos
    fun replaceAll(photos: Photos)
}

/** One JPEG per recipe in [dir], named by recipe id, e.g. "12.jpg". */
class FilePhotoStore(private val dir: File) : PhotoStore {

    override fun all(): Photos =
        dir.listFiles().orEmpty().mapNotNull { file ->
            file.name.removeSuffix(SUFFIX).toLongOrNull()?.takeIf { file.name.endsWith(SUFFIX) }?.let { it to file.readBytes() }
        }.toMap()

    override fun replaceAll(photos: Photos) {
        dir.listFiles().orEmpty().forEach { it.delete() }
        dir.mkdirs()
        photos.forEach { (recipeId, bytes) -> File(dir, "$recipeId$SUFFIX").writeBytes(bytes) }
    }

    private companion object {
        const val SUFFIX = ".jpg"
    }
}

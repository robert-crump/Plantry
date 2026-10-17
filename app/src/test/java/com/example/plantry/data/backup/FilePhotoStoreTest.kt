package com.example.plantry.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilePhotoStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir by lazy { File(folder.root, "recipe_photos") }
    private val store by lazy { FilePhotoStore(dir) }

    @Test
    fun add_appendsPagesInOrder() {
        store.add(12, byteArrayOf(1))
        store.add(12, byteArrayOf(2))
        store.add(13, byteArrayOf(3))

        assertEquals(listOf(listOf<Byte>(1), listOf<Byte>(2)), store.list(12).map { it.toList() })
        assertEquals(setOf("12-0.jpg", "12-1.jpg", "13-0.jpg"), names())
    }

    @Test
    fun order_isNumeric() {
        dir.mkdirs()
        File(dir, "12-10.jpg").writeBytes(byteArrayOf(10))
        File(dir, "12-9.jpg").writeBytes(byteArrayOf(9))

        assertEquals(listOf<Byte>(9, 10), store.list(12).map { it.single() })
    }

    @Test
    fun legacyFile_isTheFirstPage_andRenamedOnTheNextChange() {
        dir.mkdirs()
        File(dir, "12.jpg").writeBytes(byteArrayOf(1))

        assertEquals(mapOf(12L to listOf<Byte>(1)), store.all().mapValues { (_, pages) -> pages.map { it.single() } })

        store.add(12, byteArrayOf(2))

        assertEquals(listOf<Byte>(1, 2), store.list(12).map { it.single() })
        assertEquals(setOf("12-0.jpg", "12-1.jpg"), names())
    }

    @Test
    fun add_afterADeletedPage_goesLast() {
        listOf<Byte>(1, 2, 3).forEach { store.add(12, byteArrayOf(it)) }

        store.delete(12, 2)
        store.delete(12, 0)
        store.add(12, byteArrayOf(4))

        assertEquals(listOf<Byte>(2, 4), store.list(12).map { it.single() })
    }

    @Test
    fun deleteAll_keepsOtherRecipes() {
        store.add(12, byteArrayOf(1))
        store.add(12, byteArrayOf(2))
        store.add(120, byteArrayOf(3))

        store.deleteAll(12)

        assertTrue(store.list(12).isEmpty())
        assertEquals(setOf("120-0.jpg"), names())
    }

    @Test
    fun replaceAll_writesThePagesInOrder() {
        store.add(99, byteArrayOf(9))

        store.replaceAll(mapOf(12L to listOf(byteArrayOf(1), byteArrayOf(2))))

        assertEquals(listOf<Byte>(1, 2), store.list(12).map { it.single() })
        assertFalse(store.all().containsKey(99))
    }

    private fun names() = dir.list().orEmpty().toSet()
}

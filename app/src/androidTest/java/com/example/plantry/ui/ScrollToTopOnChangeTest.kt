package com.example.plantry.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The list leaves composition and comes back as it does around a detail screen; its saved state stays. */
@RunWith(AndroidJUnit4::class)
class ScrollToTopOnChangeTest {
    @get:Rule
    val compose = createComposeRule()

    private var showList by mutableStateOf(true)
    private var key by mutableStateOf("filter A")
    private lateinit var listState: LazyListState

    private fun setUpList() {
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            if (showList) {
                holder.SaveableStateProvider("list") {
                    listState = rememberLazyListState()
                    ScrollToTopOnChange(listState, key)
                    LazyColumn(Modifier.height(300.dp).testTag("list"), state = listState) {
                        items(100) { Text("Zeile $it", Modifier.height(50.dp)) }
                    }
                }
            } else {
                Text("Detail")
            }
        }
        compose.onNodeWithTag("list").performScrollToIndex(40)
        compose.runOnIdle { assertEquals(40, listState.firstVisibleItemIndex) }
    }

    @Test
    fun backFromDetail_keepsPosition() {
        setUpList()

        showList = false
        compose.waitForIdle()
        showList = true

        compose.runOnIdle { assertEquals(40, listState.firstVisibleItemIndex) }
    }

    @Test
    fun changedKey_scrollsToTop() {
        setUpList()

        key = "filter B"

        compose.runOnIdle { assertEquals(0, listState.firstVisibleItemIndex) }
    }
}

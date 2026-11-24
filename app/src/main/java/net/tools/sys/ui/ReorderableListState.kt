package net.tools.sys.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class ReorderableListState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: State<(fromKey: Any, toKey: Any) -> Unit>,
    private val onDragFinished: State<() -> Unit>,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    // A swap is only visible after the next measure pass; evaluating again before it would swap straight back.
    private var layoutAtLastSwap: LazyListLayoutInfo? = null

    private val draggedItemInfo: LazyListItemInfo?
        get() = draggedKey?.let { key -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } }

    val draggedItemOffset: Float
        get() = draggedItemInfo?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    fun onDragStart(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggedKey = key
        initialOffset = info.offset
        draggedDelta = 0f
        layoutAtLastSwap = null
    }

    fun onDrag(deltaY: Float) {
        draggedDelta += deltaY
        val layoutInfo = listState.layoutInfo
        val dragged = draggedItemInfo ?: return
        val top = dragged.offset + draggedItemOffset
        val bottom = top + dragged.size

        if (layoutInfo !== layoutAtLastSwap) {
            val middle = (top + dragged.size / 2f).toInt()
            val target = layoutInfo.visibleItemsInfo.firstOrNull {
                it.key != dragged.key && middle in it.offset..(it.offset + it.size)
            }
            if (target != null) {
                // Otherwise the list keeps the first visible key in place and jumps along with the moved item.
                if (dragged.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
                    listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                }
                onMove.value(dragged.key, target.key)
                layoutAtLastSwap = layoutInfo
            }
        }

        val overscroll = when {
            top < layoutInfo.viewportStartOffset -> top - layoutInfo.viewportStartOffset
            bottom > layoutInfo.viewportEndOffset -> bottom - layoutInfo.viewportEndOffset
            else -> 0f
        }
        if (overscroll != 0f) {
            scope.launch { listState.scrollBy(overscroll) }
        }
    }

    fun onDragEnd() {
        if (draggedKey == null) return
        draggedKey = null
        draggedDelta = 0f
        layoutAtLastSwap = null
        onDragFinished.value()
    }
}

@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    onDragFinished: () -> Unit,
): ReorderableListState {
    val scope = rememberCoroutineScope()
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnDragFinished = rememberUpdatedState(onDragFinished)
    return remember(listState, scope) {
        ReorderableListState(listState, scope, currentOnMove, currentOnDragFinished)
    }
}

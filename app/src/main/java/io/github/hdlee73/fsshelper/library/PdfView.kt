package io.github.hdlee73.fsshelper.library

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

/**
 * 화면에 띄운 PDF 한 개(안드로이드 기본 PdfRenderer). 쪽 그림은 보일 때만 그리고,
 * 최근 몇 쪽만 기억해 메모리를 아낀다. PdfRenderer는 한 번에 한 쪽만 열 수 있어 잠금으로 줄을 세운다.
 */
class PdfDoc private constructor(val file: File, private val fd: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    private val lock = Mutex()
    private val ratios = HashMap<Int, Float>()
    private val cache = object : LinkedHashMap<Pair<Int, Int>, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, ImageBitmap>?) = size > 10
    }
    @Volatile private var closed = false

    val pageCount: Int = renderer.pageCount

    /** 쪽의 가로/세로 비율 */
    fun ratio(page: Int): Float = synchronized(ratios) {
        ratios.getOrPut(page) {
            if (closed) 0.707f else runCatching { synchronized(renderer) { renderer.openPage(page).use { it.width.toFloat() / it.height } } }.getOrDefault(0.707f)
        }
    }

    /** [widthPx] 너비에 맞춰 쪽을 그린다(같은 크기는 기억해 둔 그림을 쓴다). */
    suspend fun render(page: Int, widthPx: Int): ImageBitmap? {
        val bucket = ((widthPx / 120 + 1) * 120).coerceAtMost(2600)
        synchronized(cache) { cache[page to bucket] }?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                synchronized(cache) { cache[page to bucket] }?.let { return@withLock it }
                if (closed) return@withLock null
                runCatching {
                    synchronized(renderer) {
                        renderer.openPage(page).use { p ->
                            val h = (bucket.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(bucket, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp.asImageBitmap()
                        }
                    }
                }.getOrNull()?.also { img -> synchronized(cache) { cache[page to bucket] = img } }
            }
        }
    }

    fun close() {
        closed = true
        runCatching { synchronized(renderer) { renderer.close() } }
        runCatching { fd.close() }
    }

    companion object {
        suspend fun open(file: File): PdfDoc? = withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfDoc(file, fd, PdfRenderer(fd))
            }.getOrNull()
        }
    }
}

/** PDF 파일을 열어 두고 화면을 떠나면 닫는다. 파일이 바뀌면 다시 연다. */
@Composable
fun rememberPdf(path: String, version: Long): PdfDoc? {
    val state = produceState<PdfDoc?>(null, path, version) {
        value = if (path.isBlank()) null else PdfDoc.open(File(path))
    }
    val doc = state.value
    DisposableEffect(doc) { onDispose { doc?.close() } }
    return doc
}

@Stable
class PdfViewerState {
    val list = LazyListState()
    /** 1 = 화면 너비에 맞춤 */
    var zoom by mutableFloatStateOf(1f)
}

/** 두 손가락으로 벌리고 오므리면 [onZoom]에 배율 변화를 넘긴다(한 손가락 스크롤은 그대로 둔다). */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var last = 0f
        while (true) {
            val ev = awaitPointerEvent()
            val pressed = ev.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val d = hypot(a.x - b.x, a.y - b.y)
                if (last > 0f && d > 0f) onZoom(d / last)
                last = d
                ev.changes.forEach { it.consume() }
            } else last = 0f
        }
    }
}

/**
 * 쪽을 위에서 아래로 이어 보여 주는 PDF 보기. [pages]가 비면 모든 쪽.
 * 두 손가락으로 확대·축소하고, 확대하면 옆으로도 밀어 볼 수 있다.
 */
@Composable
fun PdfPages(
    doc: PdfDoc,
    pages: List<Int>,
    state: PdfViewerState,
    modifier: Modifier = Modifier,
    toolbarExtra: @Composable () -> Unit = {},
) {
    val shown = remember(doc, pages) { pages.ifEmpty { (0 until doc.pageCount).toList() } }
    val current by remember(shown) { derivedStateOf { shown.getOrNull(state.list.firstVisibleItemIndex) ?: 0 } }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${current + 1} / ${doc.pageCount}쪽" + if (pages.isNotEmpty()) " (${shown.size}쪽)" else "",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            toolbarExtra()
            IconButton(onClick = { state.zoom = (state.zoom - 0.25f).coerceAtLeast(1f) }) { Icon(Icons.Outlined.Remove, "축소", Modifier.size(18.dp)) }
            Text("${(state.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { state.zoom = (state.zoom + 0.25f).coerceAtMost(3f) }) { Icon(Icons.Outlined.Add, "확대", Modifier.size(18.dp)) }
            IconButton(onClick = { state.zoom = 1f }) { Icon(Icons.Outlined.FitScreen, "너비에 맞춤", Modifier.size(18.dp)) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().pinchZoom { f -> state.zoom = (state.zoom * f).coerceIn(1f, 3f) }) {
            val pageWidth = (maxWidth - 16.dp) * state.zoom
            val px = with(LocalDensity.current) { pageWidth.toPx().toInt() }
            val hScroll = rememberScrollState()
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = state.zoom > 1f)) {
                LazyColumn(
                    state = state.list,
                    modifier = Modifier.width(pageWidth + 16.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(shown, key = { it }) { p -> PdfPage(doc, p, px, Modifier.width(pageWidth)) }
                }
            }
        }
    }
    // 보여 줄 쪽이 바뀌면 맨 위로
    LaunchedEffect(shown) { runCatching { state.list.scrollToItem(0) } }
}

@Composable
private fun PdfPage(doc: PdfDoc, page: Int, widthPx: Int, modifier: Modifier) {
    val ratio = remember(doc, page) { doc.ratio(page) }
    val img by produceState<ImageBitmap?>(null, doc, page, widthPx) { value = doc.render(page, widthPx) }
    Surface(modifier.aspectRatio(ratio).shadow(1.dp), color = Color.White) {
        val bmp = img
        if (bmp != null) Image(bmp, "${page + 1}쪽", Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
    }
}

/** 보기 안에서 [page]쪽으로 옮긴다(보여 주는 쪽 목록 기준). */
suspend fun PdfViewerState.goTo(page: Int, shown: List<Int>) {
    val i = shown.indexOf(page).takeIf { it >= 0 } ?: page
    runCatching { list.scrollToItem(i) }
}

@Composable
fun rememberPdfViewerState(): PdfViewerState = remember { PdfViewerState() }

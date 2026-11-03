package io.github.hdlee73.fsshelper.library

import io.github.hdlee73.fsshelper.BuildConfig
import io.github.hdlee73.fsshelper.Platform
import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 서식 문서 안의 "< 제2-4호 서식 >" 표시 한 개(문서 순서대로) */
@Serializable
data class FormMark(val no: String, val text: String)

/** 매뉴얼 목차 한 줄: 절 제목과 PDF 쪽(0부터) */
@Serializable
data class TocEntry(val title: String, val page: Int)

/** 서식 하나가 PDF에서 차지하는 쪽(0부터, 끝 포함) */
@Serializable
data class PageSpan(val start: Int, val end: Int) {
    val pages: List<Int> get() = (start..end).toList()
}

/**
 * 앱에 들어 있는 검사품질관리 매뉴얼·검사관련 서식. 저장소와 APK에는 암호화한 묶음(assets/library.bin)만 있고,
 * 처음 열 때 빌드에 넣은 열쇠(GitHub Secret LIBRARY_KEY)로 풀어 앱 전용 저장소에 둔다.
 * 묶음 안 파일 경로는 상대 이름이며, 풀 때 이 기기의 실제 경로로 바꾼다.
 */
@Serializable
data class LibraryData(
    val manualName: String = "",
    /** 한글 원본 사본(없으면 PDF만 가져온 경우) */
    val manualSrc: String = "",
    val manualPdf: String = "",
    /** 한글 원본에서 찾은 절 제목(문서 순서) */
    val manualTitles: List<String> = emptyList(),
    val manualToc: List<TocEntry> = emptyList(),
    val formsName: String = "",
    val formsSrc: String = "",
    val formsPdf: String = "",
    val formMarks: List<FormMark> = emptyList(),
    val formPages: Map<String, PageSpan> = emptyMap(),
    val importedAt: Long = 0,
) {
    val hasManual: Boolean get() = manualPdf.isNotBlank() && File(manualPdf).isFile
    val hasForms: Boolean get() = formsPdf.isNotBlank() && File(formsPdf).isFile
    val canWriteForms: Boolean get() = formsSrc.isNotBlank() && File(formsSrc).isFile && formMarks.isNotEmpty()

    /** 가져온 서식 번호(문서 순서, 중복 없이) */
    val formNos: List<String> get() = (formMarks.map { it.no } + formPages.keys).distinct()
}

sealed interface LibraryStatus {
    data object Loading : LibraryStatus
    data object Ready : LibraryStatus
    data class Missing(val message: String) : LibraryStatus
}

class LibraryStore(private val dir: File = File(AppDirs.root, "library")) {
    private val _data = MutableStateFlow(LibraryData())
    val data: StateFlow<LibraryData> = _data.asStateFlow()
    private val _status = MutableStateFlow<LibraryStatus>(LibraryStatus.Loading)
    val status: StateFlow<LibraryStatus> = _status.asStateFlow()
    private val pageCache = HashMap<String, List<String>>()

    /** 앱 버전이 바뀌었거나 아직 안 풀었으면 묶음을 푼다. */
    suspend fun ensure() = withContext(Dispatchers.IO) {
        if (_status.value == LibraryStatus.Ready) return@withContext
        val stamp = File(dir, ".stamp")
        val want = "${BuildConfig.VERSION_CODE}:${BuildConfig.VERSION_NAME}"
        val result = runCatching {
            if (!(stamp.isFile && stamp.readText() == want && File(dir, "library.json").isFile)) {
                unpack()
                stamp.writeText(want)
            }
            val raw = AppJson.decodeFromString(LibraryData.serializer(), File(dir, "library.json").readText(Charsets.UTF_8))
            fun abs(name: String) = if (name.isBlank()) "" else File(dir, name).absolutePath
            raw.copy(manualPdf = abs(raw.manualPdf), manualSrc = abs(raw.manualSrc), formsPdf = abs(raw.formsPdf), formsSrc = abs(raw.formsSrc))
        }
        result.onSuccess { _data.value = it; _status.value = LibraryStatus.Ready }
            .onFailure { _status.value = LibraryStatus.Missing(it.message ?: "자료를 열지 못했습니다.") }
    }

    private fun unpack() {
        val key = BuildConfig.LIBRARY_KEY
        if (key.isBlank()) throw IllegalStateException("이 빌드에는 매뉴얼·서식 열쇠(LIBRARY_KEY)가 들어 있지 않습니다. 저장소 주인이 GitHub Secrets에 LIBRARY_KEY를 넣고 다시 빌드해야 합니다.")
        val blob = runCatching { Platform.app.assets.open("library.bin").use { it.readBytes() } }
            .getOrElse { throw IllegalStateException("앱에 매뉴얼·서식 묶음(library.bin)이 없습니다.") }
        require(blob.size > 17 && String(blob, 0, 5, Charsets.US_ASCII) == "FSSL1") { "매뉴얼·서식 묶음 형식이 맞지 않습니다." }
        val plain = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(android.util.Base64.decode(key, android.util.Base64.DEFAULT), "AES"), GCMParameterSpec(128, blob, 5, 12))
            c.doFinal(blob, 17, blob.size - 17)
        }.getOrElse { throw IllegalStateException("매뉴얼·서식 묶음을 풀지 못했습니다(열쇠가 맞지 않음).") }
        dir.deleteRecursively()
        dir.mkdirs()
        ZipInputStream(plain.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val out = File(dir, e.name)
                require(out.canonicalPath.startsWith(dir.canonicalPath)) { "잘못된 묶음입니다." }
                if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); FileOutputStream(out).use { zip.copyTo(it) } }
            }
        }
        synchronized(pageCache) { pageCache.clear() }
    }

    /** PDF 쪽별 글자(찾기용) */
    fun pageTexts(kind: String): List<String> = synchronized(pageCache) {
        pageCache.getOrPut(kind) { runCatching { File(dir, "$kind.pages.txt").readText(Charsets.UTF_8).split('\u000C') }.getOrDefault(emptyList()) }
    }

    /** 서식 한글 원본(한글 앱으로 열기) */
    fun formsHwp(): File? = _data.value.formsSrc.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }

    data class Hit(val page: Int, val snippet: String)

    /** PDF 글자에서 [query]가 들어 있는 쪽과 앞뒤 글 */
    fun search(kind: String, query: String, limit: Int = 200): List<Hit> {
        val q = squash(query)
        if (q.length < 2) return emptyList()
        return pageTexts(kind).withIndex().mapNotNull { (i, t) ->
            val flat = t.replace(Regex("""\s+"""), " ")
            val idx = indexIgnoringSpace(flat, q)
            if (idx < 0) null else Hit(i, "…" + flat.substring((idx - 30).coerceAtLeast(0), (idx + 60).coerceAtMost(flat.length)).trim() + "…")
        }.take(limit)
    }

    companion object {
        fun squash(s: String) = s.filter { it.isLetterOrDigit() }

        /** 띄어쓰기를 무시하고 [needle](이미 squash한 글자)을 찾아 원문 위치를 돌려준다. */
        fun indexIgnoringSpace(text: String, needle: String): Int {
            if (needle.isEmpty()) return -1
            val map = ArrayList<Int>(text.length)
            val sb = StringBuilder()
            text.forEachIndexed { i, c -> if (c.isLetterOrDigit()) { sb.append(c.lowercaseChar()); map += i } }
            val k = sb.indexOf(needle.lowercase())
            return if (k < 0) -1 else map[k]
        }

        /**
         * 절 제목을 PDF 쪽에 맞춘다. 제목이 여러 개 한꺼번에 나오는 쪽(차례)은 건너뛰고,
         * 문서 순서대로 앞 절보다 뒤에서 처음 나오는 쪽을 그 절의 시작으로 본다.
         */
        fun mapToc(titles: List<String>, pages: List<String>): List<TocEntry> {
            if (titles.isEmpty() || pages.isEmpty()) return emptyList()
            val flat = pages.map { squash(it) }
            val keys = titles.map { squash(it) }.filter { it.length >= 2 }
            val tocPages = flat.indices.filter { p -> keys.count { flat[p].contains(it) } >= 5 }.toSet()
            var cursor = 0
            return titles.mapNotNull { t ->
                val k = squash(t)
                if (k.length < 2) return@mapNotNull null
                val p = (cursor until flat.size).firstOrNull { it !in tocPages && flat[it].contains(k) } ?: return@mapNotNull null
                cursor = p
                TocEntry(t, p)
            }
        }

        private val MARK_FLAT = Regex("""<제(\d+)-(\d+)호서식>""")

        /** PDF 쪽 글자에서 서식 표시를 순서대로 찾는다(한글 원본이 없을 때). */
        fun marksFromPages(pages: List<String>): List<FormMark> = pages.flatMap { t ->
            MARK_FLAT.findAll(t.filterNot { it.isWhitespace() }).map { m -> FormMark("${m.groupValues[1]}-${m.groupValues[2]}", "< 제${m.groupValues[1]}-${m.groupValues[2]}호 서식 >") }.toList()
        }

        /**
         * 서식마다 PDF 쪽 범위를 정한다. 다음 서식 표시가 쪽 맨 위가 아니라 중간에 있으면 그 쪽까지 이 서식에 넣는다.
         * 같은 번호가 다시 나오면(이어지는 부분) 범위를 넓힌다.
         */
        fun formSpans(pages: List<String>): Map<String, PageSpan> {
            data class Hit(val no: String, val page: Int, val top: Boolean)
            val hits = pages.flatMapIndexed { p, t ->
                val flat = t.filterNot { it.isWhitespace() }
                MARK_FLAT.findAll(flat).map { m -> Hit("${m.groupValues[1]}-${m.groupValues[2]}", p, m.range.first < 40) }.toList()
            }
            val out = LinkedHashMap<String, PageSpan>()
            hits.forEachIndexed { i, h ->
                val next = hits.drop(i + 1).firstOrNull { it.no != h.no }
                val end = when {
                    next == null -> pages.size - 1
                    next.page == h.page -> h.page
                    next.top -> next.page - 1
                    else -> next.page
                }.coerceAtLeast(h.page)
                val old = out[h.no]
                out[h.no] = if (old == null) PageSpan(h.page, end) else PageSpan(minOf(old.start, h.page), maxOf(old.end, end))
            }
            return out
        }

    }
}

/** 작성한 문서를 두는 곳: 문서\FSS_Helper\서식 */
object DocFiles {
    fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80).ifBlank { "문서" }

    val formsDir: File get() = File(AppDirs.exports, "서식").apply { mkdirs() }

    /** 같은 이름이 있으면 " (2)"를 붙인 새 이름 */
    fun unique(dir: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var f = File(dir, name)
        var n = 2
        while (f.exists()) { f = File(dir, "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""); n++ }
        return f
    }
}

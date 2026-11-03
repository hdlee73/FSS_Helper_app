package io.github.hdlee73.fsshelper.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File

/** 최근 사용 항목의 종류. 홈 화면 탭으로 나눠 보여 준다. */
enum class RecentKind(val id: String, val label: String) {
    FORM("form", "서식"), MANUAL("manual", "매뉴얼"), LAW("law", "법령"), CORP("corp", "기업"), FILE("file", "작성한 파일");

    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: FILE }
}

/**
 * 최근에 연 것 하나. [ref]는 다시 열 때 쓰는 값(서식 번호, 매뉴얼 쪽, 법령 검색어, 기업 코드, 파일 경로).
 */
@Serializable
data class Recent(
    val kind: String,
    val title: String,
    val ref: String,
    val where: String = "",
    val at: Long = System.currentTimeMillis(),
    val starred: Boolean = false,
) {
    val type: RecentKind get() = RecentKind.of(kind)
    val key: String get() = "$kind|$ref"
}

@Serializable
data class RecentData(val items: List<Recent> = emptyList())

/** 홈 화면의 '최근 사용'·'즐겨찾기' 목록(이 PC에만 저장) */
class RecentStore(file: File = File(AppDirs.root, "recent.json")) {
    private val store = JsonFile(file, RecentData.serializer()) { RecentData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<RecentData> = _data.asStateFlow()

    private fun save(block: (RecentData) -> RecentData) {
        _data.update(block)
        runCatching { store.write(_data.value) }
    }

    /** 맨 앞에 올린다. 즐겨찾기 표시는 그대로 둔다. 즐겨찾기가 아닌 것은 60개까지만 남긴다. */
    fun touch(kind: RecentKind, title: String, ref: String, where: String = "") = save { d ->
        val key = "${kind.id}|$ref"
        val old = d.items.firstOrNull { it.key == key }
        val item = Recent(kind.id, title, ref, where, starred = old?.starred == true)
        val rest = d.items.filter { it.key != key }
        val kept = rest.filter { it.starred } + rest.filter { !it.starred }.take(59)
        d.copy(items = listOf(item) + kept.sortedByDescending { it.at })
    }

    fun toggleStar(key: String) = save { d -> d.copy(items = d.items.map { if (it.key == key) it.copy(starred = !it.starred) else it }) }
    fun remove(key: String) = save { d -> d.copy(items = d.items.filter { it.key != key }) }
    fun clear() = save { d -> d.copy(items = d.items.filter { it.starred }) }
}

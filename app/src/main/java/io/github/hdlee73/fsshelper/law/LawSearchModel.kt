package io.github.hdlee73.fsshelper.law

import com.hdlee73.everylaw.core.domain.DelegationProgress
import com.hdlee73.everylaw.core.domain.LawDomainException
import com.hdlee73.everylaw.core.domain.withParagraphPieces
import com.hdlee73.everylaw.core.model.AdminRuleSummary
import com.hdlee73.everylaw.core.model.Article
import com.hdlee73.everylaw.core.model.DelegationTree
import com.hdlee73.everylaw.core.model.KeywordSearchResult
import com.hdlee73.everylaw.core.model.LawLevel
import com.hdlee73.everylaw.core.model.LawSummary
import com.hdlee73.everylaw.core.model.LawVersion
import com.hdlee73.everylaw.core.model.PrecedentDetail
import com.hdlee73.everylaw.core.model.PrecedentSummary
import com.hdlee73.everylaw.core.parser.DrfFormatException
import com.hdlee73.everylaw.core.parser.LawQueryParser
import io.github.hdlee73.fsshelper.data.SettingsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface LawUiState {
    data object Idle : LawUiState
    data class Loading(val message: String) : LawUiState
    data class Tree(val tree: DelegationTree, val expanded: Set<String>, val selectedId: String) : LawUiState
    data class Keyword(val result: KeywordSearchResult) : LawUiState
    data class Error(val message: String, val canRetry: Boolean) : LawUiState
}

/** 오른쪽 상세 영역에 띄우는 것 */
sealed interface LawDetail {
    data object None : LawDetail
    data class Loading(val title: String) : LawDetail
    data class Document(val title: String, val subtitle: String, val articles: List<Article>, val focus: String? = null) : LawDetail
    data class Precedent(val detail: PrecedentDetail) : LawDetail
    data class Failed(val message: String) : LawDetail
}

sealed interface VersionsState {
    data object Hidden : VersionsState
    data object Loading : VersionsState
    data class Shown(val lawName: String, val versions: List<LawVersion>) : VersionsState
    data class Failed(val message: String) : VersionsState
}

/** 법률 도우미에서 위임 법령을 보여 주는 방식 */
enum class LawView(val label: String, val hint: String) {
    INSIGHTS("위임 펼쳐 보기", "항·호·목 바로 아래에 위임한 하위 규정을, 인용만 한 법규는 맨 아래에 따로 보여 줍니다"),
    COMPARE("단계 비교", "위임 내용이 있는 단계만 열로 나란히 비교합니다(1~5단)"),
}

/** 법률 도우미 화면 상태. FSS Insights 의 SearchViewModel 을 데스크톱용으로 옮긴 것. */
class LawSearchModel(private val law: LawModule, private val settings: SettingsRepo, private val scope: CoroutineScope) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 조회 기준 시행일(yyyyMMdd). null = 현행 */
    private val _asOf = MutableStateFlow<String?>(null)
    val asOf: StateFlow<String?> = _asOf.asStateFlow()

    private val _state = MutableStateFlow<LawUiState>(LawUiState.Idle)
    val state: StateFlow<LawUiState> = _state.asStateFlow()

    private val _detail = MutableStateFlow<LawDetail>(LawDetail.None)
    val detail: StateFlow<LawDetail> = _detail.asStateFlow()

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _versions = MutableStateFlow<VersionsState>(VersionsState.Hidden)
    val versions: StateFlow<VersionsState> = _versions.asStateFlow()

    /** 하위 법규를 위임받은 항 바로 아래에 붙여 보여 줄지 */
    private val _inline = MutableStateFlow(true)
    val inline: StateFlow<Boolean> = _inline.asStateFlow()

    private val _view = MutableStateFlow(LawView.INSIGHTS)
    val view: StateFlow<LawView> = _view.asStateFlow()
    fun setView(v: LawView) { _view.value = v }

    private val backStack = ArrayDeque<Pair<LawUiState, LawDetail>>()
    private var job: Job? = null
    private var detailJob: Job? = null
    private var lastSubmitted = ""

    val isSample: Boolean get() = law.repository.isSample

    fun onQueryChange(text: String) { _query.value = text }
    fun setFilter(text: String) { _filter.value = text }
    fun setInline(on: Boolean) { _inline.value = on }

    fun setAsOf(ymd: String?) {
        _asOf.value = ymd
        when (val s = _state.value) {
            is LawUiState.Tree -> runTree(s.tree.query.raw, pushHistory = false)
            is LawUiState.Keyword -> runKeyword(s.result.keyword, pushHistory = false)
            else -> Unit
        }
    }

    /** 법령명 + 조문이면 위임 트리, 아니면 키워드 통합 검색 */
    fun submit(text: String = _query.value) {
        val q = text.trim()
        if (q.isEmpty()) return
        _query.value = q
        if (LawQueryParser.parse(q) is LawQueryParser.Result.Ok) runTree(q) else runKeyword(q)
    }

    fun retry() = submit(lastSubmitted)

    private fun runTree(q: String, pushHistory: Boolean = true) {
        lastSubmitted = q
        val before = _state.value to _detail.value
        job?.cancel()
        job = scope.launch {
            _state.value = LawUiState.Loading("조문을 해석하는 중")
            law.getDelegationTree(q, _asOf.value)
                .catch { e -> if (e is CancellationException) throw e else _state.value = errorOf(e) }
                .collect { p ->
                    when (p) {
                        is DelegationProgress.Step -> _state.value = LawUiState.Loading(p.message)
                        is DelegationProgress.Done -> {
                            if (pushHistory) push(before)
                            settings.addRecentLaw(q)
                            _filter.value = ""
                            _detail.value = LawDetail.None
                            _state.value = LawUiState.Tree(p.tree, defaultExpanded(p.tree), p.tree.root.id)
                        }
                    }
                }
        }
    }

    private fun runKeyword(q: String, pushHistory: Boolean = true) {
        lastSubmitted = q
        val before = _state.value to _detail.value
        job?.cancel()
        job = scope.launch {
            _state.value = LawUiState.Loading("'$q' 키워드로 법령·행정규칙·판례 찾는 중")
            _state.value = try {
                val result = law.searchKeyword(q, _asOf.value)
                if (pushHistory) push(before)
                settings.addRecentLaw(q)
                _detail.value = LawDetail.None
                LawUiState.Keyword(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorOf(e)
            }
        }
    }

    // ---------- 트리 ----------

    fun toggle(nodeId: String) = updateTree { s ->
        s.copy(expanded = if (nodeId in s.expanded) s.expanded - nodeId else s.expanded + nodeId)
    }

    fun expandAll(expand: Boolean) = updateTree { s ->
        val keepCited = s.expanded.filter { it.startsWith("cited:") }
        s.copy(expanded = keepCited.toSet() + if (expand) (s.tree.root.flatten() + s.tree.cited.flatMap { it.flatten() }).map { it.id } else listOf(s.tree.root.id))
    }

    fun select(nodeId: String) {
        updateTree { it.copy(selectedId = nodeId) }
        _detail.value = LawDetail.None
    }

    private fun updateTree(block: (LawUiState.Tree) -> LawUiState.Tree) {
        _state.update { if (it is LawUiState.Tree) block(it) else it }
    }

    private fun defaultExpanded(tree: DelegationTree): Set<String> =
        (tree.root.flatten() + tree.cited.flatMap { it.flatten() }).map { it.id }.toSet()

    // ---------- 상세 ----------

    fun openLaw(summary: LawSummary, focus: String? = null) = loadDetail(summary.name) {
        LawDetail.Document(summary.name, listOfNotNull(summary.kind, summary.effectiveDate?.let { "시행 " + com.hdlee73.everylaw.core.model.formatYmd(it) }).joinToString(" · "), law.getLawArticles(summary), focus)
    }

    fun openAdminRule(rule: AdminRuleSummary) = loadDetail(rule.name) {
        val doc = law.getAdminRule(rule.serialNo)
        LawDetail.Document(doc.name, listOfNotNull(rule.kind, doc.agency).joinToString(" · "), doc.articles)
    }

    fun openPrecedent(p: PrecedentSummary) = loadDetail(p.caseName) { LawDetail.Precedent(law.getPrecedent(p.serialNo)) }

    fun closeDetail() { _detail.value = LawDetail.None }

    private fun loadDetail(title: String, block: suspend () -> LawDetail) {
        detailJob?.cancel()
        detailJob = scope.launch {
            _detail.value = LawDetail.Loading(title)
            _detail.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LawDetail.Failed(errorOf(e).message)
            }
        }
    }

    // ---------- 시행 연혁 ----------

    fun openVersions() {
        val tree = (_state.value as? LawUiState.Tree)?.tree ?: return
        _versions.value = VersionsState.Loading
        scope.launch {
            _versions.value = try {
                VersionsState.Shown(tree.law.name, law.getLawVersions(tree.law))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VersionsState.Failed(errorOf(e).message)
            }
        }
    }

    fun closeVersions() { _versions.value = VersionsState.Hidden }

    // ---------- 뒤로가기 ----------

    private fun push(previous: Pair<LawUiState, LawDetail>) {
        val s = previous.first
        if (s is LawUiState.Idle || s is LawUiState.Tree || s is LawUiState.Keyword) {
            backStack.addLast(previous)
            if (backStack.size > 30) backStack.removeFirst()
        }
    }

    /** @return 화면 안에서 처리했으면 true */
    fun back(): Boolean {
        if (_versions.value != VersionsState.Hidden) { closeVersions(); return true }
        if (_detail.value != LawDetail.None) { closeDetail(); return true }
        job?.cancel()
        val current = _state.value
        if (current is LawUiState.Idle && backStack.isEmpty()) return false
        val prev = backStack.removeLastOrNull() ?: (LawUiState.Idle to LawDetail.None)
        _state.value = prev.first
        _detail.value = prev.second
        _filter.value = ""
        _query.value = when (val s = prev.first) {
            is LawUiState.Tree -> s.tree.query.raw
            is LawUiState.Keyword -> s.result.keyword
            else -> ""
        }
        return true
    }

    private fun errorOf(e: Throwable): LawUiState.Error = when (e) {
        is LawDomainException -> LawUiState.Error(e.message ?: "검색 결과가 없습니다.", canRetry = e !is LawDomainException.InvalidQuery)
        is DrfFormatException -> LawUiState.Error("국가법령정보 응답을 읽지 못했어요: ${e.message}. 설정의 OC 값이 맞는지 확인해 주세요.", canRetry = true)
        is retrofit2.HttpException -> LawUiState.Error("국가법령정보 서버 응답 오류 (HTTP ${e.code()}). 잠시 뒤 다시 시도해 주세요.", canRetry = true)
        is IOException -> LawUiState.Error("www.law.go.kr 에 연결하지 못했어요. 네트워크를 확인해 주세요. 미리 받아 둔 법령은 연결 없이도 볼 수 있어요.", canRetry = true)
        else -> LawUiState.Error("${e::class.simpleName}: ${e.message?.takeIf { it.isNotBlank() } ?: "(메시지 없음)"}", canRetry = true)
    }
}

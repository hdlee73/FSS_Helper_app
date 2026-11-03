package io.github.hdlee73.fsshelper.dart

import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.JsonFile
import io.github.hdlee73.fsshelper.data.SettingsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class DartSaved(
    val watch: List<DartCorp> = emptyList(),
    val recent: List<String> = emptyList(),
    /** 회사별로 사용자가 마지막으로 확인한 공시일(yyyyMMdd) */
    val seen: Map<String, String> = emptyMap(),
    /** 대시보드용: 회사별 마지막으로 확인한 새 공시 건수 */
    val newCount: Map<String, Int> = emptyMap(),
    val lastNewCheck: Long = 0,
)

/** 불러오는 중 / 결과 / 실패 */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

enum class CompanyTab(val label: String) { OVERVIEW("기업개황"), HOLDERS("최대주주·지분"), FINANCE("재무 현황"), FILINGS("공시") }

data class FilingQuery(val type: String = "a", val years: Int = 1)

/** 기업 개황 및 공시 화면 상태 */
class DartModel(settings: SettingsRepo, private val scope: CoroutineScope) {
    val client = DartClient { settings.dartKey }
    private val store = JsonFile(File(AppDirs.root, "dart.json"), DartSaved.serializer()) { DartSaved() }
    private val _saved = MutableStateFlow(store.read())
    val saved: StateFlow<DartSaved> = _saved.asStateFlow()

    private var index: CorpIndex? = null
    private val _indexState = MutableStateFlow<Load<Int>>(Load.Idle)
    /** 회사 목록 준비 상태(완료 시 회사 수) */
    val indexState: StateFlow<Load<Int>> = _indexState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<List<DartCorp>?>(null)
    val results: StateFlow<List<DartCorp>?> = _results.asStateFlow()

    private val _selected = MutableStateFlow<DartCorp?>(null)
    val selected: StateFlow<DartCorp?> = _selected.asStateFlow()
    private val _tab = MutableStateFlow(CompanyTab.OVERVIEW)
    val tab: StateFlow<CompanyTab> = _tab.asStateFlow()

    val company = MutableStateFlow<Load<DartCompany>>(Load.Idle)
    val holders = MutableStateFlow<Load<DartHolders>>(Load.Idle)
    val finance = MutableStateFlow<Load<DartFinance>>(Load.Idle)
    val filings = MutableStateFlow<Load<DartFilings>>(Load.Idle)
    private val _filingQuery = MutableStateFlow(FilingQuery())
    val filingQuery: StateFlow<FilingQuery> = _filingQuery.asStateFlow()

    private val _newCheck = MutableStateFlow<Load<Map<String, Int>>>(Load.Idle)
    val newCheck: StateFlow<Load<Map<String, Int>>> = _newCheck.asStateFlow()

    private var jobs = mutableListOf<Job>()
    private val back = ArrayDeque<DartCorp?>()

    val hasKey: Boolean get() = client.hasKey

    private fun save(block: (DartSaved) -> DartSaved) {
        _saved.update(block)
        runCatching { store.write(_saved.value) }
    }

    /** 회사 목록을 준비한다. 저장본이 7일 넘었거나 없으면 새로 받는다. */
    fun ensureIndex(force: Boolean = false) {
        if (_indexState.value is Load.Loading) return
        scope.launch {
            val file = corpFile
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > 7L * 24 * 3600 * 1000
            if (!force && index != null && !stale) return@launch
            _indexState.value = Load.Loading
            if (!force) {
                withContext(Dispatchers.IO) { CorpIndex.load(file) }?.let { index = it; _indexState.value = Load.Done(it.size) }
            }
            if ((force || stale) && client.hasKey) {
                runCatching { client.downloadCorpCodes(file) }
                    .onSuccess { withContext(Dispatchers.IO) { CorpIndex.load(file) }?.let { index = it; _indexState.value = Load.Done(it.size) } }
                    .onFailure { e -> if (index == null) _indexState.value = Load.Failed(e.message ?: "회사 목록을 받지 못했습니다.") }
            } else if (index == null) {
                _indexState.value = Load.Failed(if (client.hasKey) "회사 목록이 없습니다." else "설정에서 OpenDART 인증키를 입력하면 회사 목록을 받아 옵니다.")
            }
            if (_query.value.isNotBlank()) search()
        }
    }

    fun onQuery(q: String) { _query.value = q }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) { _results.value = null; return }
        val idx = index
        if (idx == null) { ensureIndex(); return }
        _results.value = idx.search(q)
        save { s -> s.copy(recent = (listOf(q) + s.recent.filter { it != q }).take(10)) }
    }

    fun open(corp: DartCorp, pushHistory: Boolean = true) {
        if (pushHistory && _selected.value?.corp != corp.corp) back.addLast(_selected.value)
        _selected.value = corp
        _tab.value = CompanyTab.OVERVIEW
        jobs.forEach { it.cancel() }; jobs.clear()
        company.value = Load.Idle; holders.value = Load.Idle; finance.value = Load.Idle; filings.value = Load.Idle
        loadTab(CompanyTab.OVERVIEW)
    }

    fun closeCompany() { _selected.value = null }

    fun setTab(tab: CompanyTab) { _tab.value = tab; loadTab(tab) }

    private fun <T> loadInto(target: MutableStateFlow<Load<T>>, block: suspend () -> T) {
        if (target.value is Load.Loading || target.value is Load.Done) return
        jobs += scope.launch {
            target.value = Load.Loading
            target.value = try { Load.Done(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Load.Failed(e.message ?: "불러오지 못했습니다.") }
        }
    }

    private fun loadTab(tab: CompanyTab) {
        val c = _selected.value ?: return
        when (tab) {
            CompanyTab.OVERVIEW -> loadInto(company) { client.company(c.corp).also { info -> updateWatchClass(info) } }
            CompanyTab.HOLDERS -> loadInto(holders) { client.holders(c.corp) }
            CompanyTab.FINANCE -> loadInto(finance) {
                val month = (company.value as? Load.Done)?.value?.accountMonth ?: runCatching { client.company(c.corp).accountMonth }.getOrDefault("12")
                client.finance(c.corp, month)
            }
            CompanyTab.FILINGS -> loadInto(filings) { loadFilings(c.corp, _filingQuery.value) }
        }
    }

    private suspend fun loadFilings(corp: String, q: FilingQuery): DartFilings {
        val today = LocalDate.now()
        val r = client.filings(corp, q.type, today.minusYears(q.years.toLong()).format(YMD), today.format(YMD))
        markSeen(corp, r.items.firstOrNull()?.date)
        return r
    }

    fun setFilingQuery(q: FilingQuery) {
        _filingQuery.value = q
        filings.value = Load.Idle
        loadTab(CompanyTab.FILINGS)
    }

    fun retry(tab: CompanyTab) {
        when (tab) {
            CompanyTab.OVERVIEW -> company.value = Load.Idle
            CompanyTab.HOLDERS -> holders.value = Load.Idle
            CompanyTab.FINANCE -> finance.value = Load.Idle
            CompanyTab.FILINGS -> filings.value = Load.Idle
        }
        loadTab(tab)
    }

    private fun updateWatchClass(info: DartCompany) = save { s ->
        s.copy(watch = s.watch.map { if (it.corp == info.corp) it.copy(cls = info.cls, stock = info.stock.ifBlank { it.stock }) else it })
    }

    fun isWatched(corp: String) = _saved.value.watch.any { it.corp == corp }

    fun toggleWatch(c: DartCorp) = save { s ->
        if (s.watch.any { it.corp == c.corp }) s.copy(watch = s.watch.filter { it.corp != c.corp })
        else s.copy(watch = (s.watch + c).take(40), seen = s.seen + (c.corp to LocalDate.now().format(YMD)))
    }

    private fun markSeen(corp: String, date: String?) {
        if (date == null || !isWatched(corp)) return
        save { s -> s.copy(seen = s.seen + (corp to maxOf(date, s.seen[corp].orEmpty())), newCount = s.newCount - corp) }
    }

    /** 관심기업마다 마지막 확인일 이후의 새 공시 건수를 센다(정기·주요사항·기타 모두). */
    fun checkNewFilings() {
        val watch = _saved.value.watch
        if (watch.isEmpty() || !client.hasKey || _newCheck.value is Load.Loading) return
        scope.launch {
            _newCheck.value = Load.Loading
            val today = LocalDate.now().format(YMD)
            val counts = try {
                watch.take(20).map { c ->
                    async {
                        val since = _saved.value.seen[c.corp] ?: today
                        val n = listOf("a", "m", "e").sumOf { t ->
                            runCatching { client.filings(c.corp, t, since, today).items.count { it.date > since } }.getOrDefault(0)
                        }
                        c.corp to n
                    }
                }.map { it.await() }.toMap()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _newCheck.value = Load.Failed(e.message ?: "확인하지 못했습니다."); return@launch
            }
            save { it.copy(newCount = counts.filterValues { v -> v > 0 }, lastNewCheck = System.currentTimeMillis()) }
            _newCheck.value = Load.Done(counts)
        }
    }

    /** @return 화면 안에서 처리했으면 true */
    fun back(): Boolean {
        if (_selected.value == null) return false
        val prev = back.removeLastOrNull()
        if (prev == null) _selected.value = null else open(prev, pushHistory = false)
        return true
    }

    companion object {
        val YMD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}

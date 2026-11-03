package io.github.hdlee73.fsshelper.law

import com.hdlee73.everylaw.core.data.remote.LawApiService
import com.hdlee73.everylaw.core.data.repository.RemoteLawRepository
import com.hdlee73.everylaw.core.data.repository.SwitchingLawRepository
import com.hdlee73.everylaw.core.domain.BuildPdfExtrasUseCase
import com.hdlee73.everylaw.core.domain.GetAdminRuleUseCase
import com.hdlee73.everylaw.core.domain.GetDelegationTreeUseCase
import com.hdlee73.everylaw.core.domain.GetLawArticlesUseCase
import com.hdlee73.everylaw.core.domain.GetLawVersionsUseCase
import com.hdlee73.everylaw.core.domain.GetPrecedentUseCase
import com.hdlee73.everylaw.core.domain.LawRepository
import com.hdlee73.everylaw.core.domain.SearchKeywordUseCase
import com.hdlee73.everylaw.core.parser.LawAliases
import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.SettingsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 국가법령정보 응답을 PC에 저장해 두는 캐시.
 * - 버전이 정해진 본문(MST·일련번호로 받는 법령·행정규칙·판례·3단비교)은 바뀌지 않으므로 저장본을 먼저 쓴다.
 * - 검색 목록은 늘 새로 받고, 연결이 안 될 때만 저장본으로 대신한다.
 */
class LawCache(private val dir: File = File(AppDirs.cache, "law")) {
    init { dir.mkdirs() }

    private val _offline = MutableStateFlow(false)
    /** 마지막 응답을 연결 실패로 저장본에서 가져왔는지 */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.xml")
    }

    fun read(key: String): String? = file(key).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    fun write(key: String, body: String) {
        // 오류 응답(HTML 안내 페이지 등)은 저장하지 않는다.
        if (body.length < 40 || body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true)) return
        runCatching { file(key).writeText(body, Charsets.UTF_8) }
    }

    suspend fun immutable(key: String, fetch: suspend () -> String): String {
        read(key)?.let { return it }
        return fetch().also { write(key, it); _offline.value = false }
    }

    suspend fun fresh(key: String, fetch: suspend () -> String): String = try {
        fetch().also { write(key, it); _offline.value = false }
    } catch (e: IOException) {
        read(key)?.also { _offline.value = true } ?: throw e
    } catch (e: retrofit2.HttpException) {
        read(key)?.also { _offline.value = true } ?: throw e
    }

    fun stats(): Pair<Int, Long> {
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        return files.size to files.sumOf { it.length() }
    }

    fun clear() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }
}

/** [LawApiService] 앞에 캐시를 두른다. */
class CachingLawApi(private val api: LawApiService, private val cache: LawCache) : LawApiService {
    override suspend fun searchLaws(query: String, search: Int, display: Int, page: Int) =
        cache.fresh("law?q=$query&s=$search&d=$display&p=$page") { api.searchLaws(query, search, display, page) }

    override suspend fun getLaw(mst: String, jo: String?) =
        cache.immutable("lawBody?mst=$mst&jo=$jo") { api.getLaw(mst, jo) }

    override suspend fun searchEffectiveLaws(query: String, status: String, effectiveRange: String?, display: Int) =
        cache.fresh("eflaw?q=$query&nw=$status&ef=$effectiveRange&d=$display") { api.searchEffectiveLaws(query, status, effectiveRange, display) }

    override suspend fun getEffectiveLaw(mst: String, effectiveDate: String, jo: String?) =
        cache.immutable("eflawBody?mst=$mst&ef=$effectiveDate&jo=$jo") { api.getEffectiveLaw(mst, effectiveDate, jo) }

    override suspend fun getThreeTierComparison(mst: String, kind: Int) =
        cache.immutable("thdCmp?mst=$mst&k=$kind") { api.getThreeTierComparison(mst, kind) }

    override suspend fun getLawHierarchy(mst: String) =
        cache.immutable("lsStmd?mst=$mst") { api.getLawHierarchy(mst) }

    override suspend fun searchAdminRules(query: String, search: Int, display: Int) =
        cache.fresh("admrul?q=$query&s=$search&d=$display") { api.searchAdminRules(query, search, display) }

    override suspend fun getAdminRule(serialNo: String) =
        cache.immutable("admrulBody?id=$serialNo") { api.getAdminRule(serialNo) }

    override suspend fun searchPrecedents(query: String, search: Int, display: Int, sort: String) =
        cache.fresh("prec?q=$query&s=$search&d=$display&o=$sort") { api.searchPrecedents(query, search, display, sort) }

    override suspend fun getPrecedent(serialNo: String) =
        cache.immutable("precBody?id=$serialNo") { api.getPrecedent(serialNo) }
}

/** 법령 기능의 의존성 묶음. OC가 비어 있으면 데모 데이터로 동작한다. */
class LawModule(private val settings: SettingsRepo) {
    val cache = LawCache()
    private val api = CachingLawApi(LawApiService.create(ocProvider = { settings.lawOc }), cache)

    val repository: LawRepository = SwitchingLawRepository(
        hasKey = { settings.lawOc.isNotBlank() },
        remote = RemoteLawRepository(api),
    )

    val getDelegationTree = GetDelegationTreeUseCase(repository)
    val searchKeyword = SearchKeywordUseCase(repository)
    val getLawVersions = GetLawVersionsUseCase(repository)
    val getLawArticles = GetLawArticlesUseCase(repository)
    val getPrecedent = GetPrecedentUseCase(repository)
    val getAdminRule = GetAdminRuleUseCase(repository)
    val buildPdfExtras = BuildPdfExtrasUseCase(repository)

    /**
     * 주요 금융법령(법률·시행령·시행규칙)과 감독규정 본문을 미리 받아 캐시에 둔다.
     * [progress]에 (완료 수, 전체 수, 이름)을 알린다. 실패한 이름 목록을 돌려준다.
     */
    suspend fun prefetchMajor(progress: (Int, Int, String) -> Unit): List<String> {
        val names = (LawAliases.majorLaws() + extraLaws).distinct()
        val failed = mutableListOf<String>()
        names.forEachIndexed { i, name ->
            progress(i, names.size, name)
            runCatching {
                if (name.endsWith("규정") || name.endsWith("세칙")) {
                    repository.searchAdminRules(name).firstOrNull { it.name.replace(" ", "") == name.replace(" ", "") }
                        ?.let { repository.getAdminRule(it.serialNo) }
                } else {
                    val laws = repository.searchLaws(name)
                    laws.filter { it.name == name || it.name == "$name 시행령" || it.name == "$name 시행규칙" }.forEach { law ->
                        repository.getLawArticles(law)
                        runCatching { repository.getThreeTier(law) }
                    }
                }
            }.onFailure { failed += name }
        }
        progress(names.size, names.size, "")
        return failed
    }

    private val extraLaws = listOf(
        "금융회사의 지배구조에 관한 법률",
        "금융산업의 구조개선에 관한 법률",
        "금융위원회의 설치 등에 관한 법률",
        "금융기관 검사 및 제재에 관한 규정",
        "금융기관 검사 및 제재에 관한 규정 시행세칙",
    )
}

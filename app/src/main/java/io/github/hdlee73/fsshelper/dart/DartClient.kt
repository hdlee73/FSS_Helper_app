package io.github.hdlee73.fsshelper.dart

import io.github.hdlee73.fsshelper.data.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

// ───────── 모델 (FSS Insights 와 같은 모양) ─────────

/** 회사 한 곳. [stock]이 비어 있으면 비상장사. [cls]: Y 유가증권, K 코스닥, N 코넥스, E 기타법인 */
@Serializable
data class DartCorp(val corp: String, val name: String, val stock: String = "", val cls: String = "")

fun dartMarketLabel(cls: String): String = when (cls) {
    "Y" -> "유가증권"; "K" -> "코스닥"; "N" -> "코넥스"; else -> "기타법인"
}

data class DartCompany(
    val corp: String, val name: String, val nameEn: String, val stock: String, val cls: String,
    val ceo: String, val corpNo: String, val bizNo: String, val address: String, val homepage: String, val ir: String,
    val phone: String, val industryCode: String, val established: String, val accountMonth: String,
)

data class DartShare(val shares: Double?, val ratio: Double?)
data class DartHolder(val name: String, val relation: String, val common: DartShare?, val pref: DartShare?)
data class DartHolders(val basis: String, val items: List<DartHolder>, val totalCommon: Double?, val totalPref: Double?)

data class DartFiling(val id: String, val title: String, val date: String, val by: String, val note: String, val url: String)
data class DartFilings(val items: List<DartFiling>, val from: String, val to: String)

data class BalanceCol(val label: String, val assets: Double?, val liab: Double?, val equity: Double?)
data class IncomeCol(val label: String, val sub: String, val revenue: Double?, val op: Double?, val net: Double?)
data class TrendPoint(val year: Int, val revenue: Double?, val op: Double?, val net: Double?, val debt: Double?)

/** 재무 현황(단위 억원). [eps]는 연도별 주당이익(원) */
data class DartFinance(
    val basis: String, val note: String, val annualYear: Int, val quarterLabel: String,
    val balance: List<BalanceCol>, val income: List<IncomeCol>, val trend: List<TrendPoint>, val eps: Map<Int, Double>,
)

class DartException(message: String) : Exception(message)

private val KEY_PARAM = Regex("crtfc_key=[^&\\s\"'<>]*", RegexOption.IGNORE_CASE)
/** 오류 문구에서 인증키 값을 가린다. */
fun redactKey(text: String): String = text.replace(KEY_PARAM, "crtfc_key=***")

/**
 * 금융감독원 전자공시(OpenDART) API를 사용자 본인의 인증키로 직접 부른다(내부망에서 opendart.fss.or.kr 접속 가능).
 * FSS Insights 서버(dart-lib.js)의 가공 규칙을 그대로 옮겼다.
 */
class DartClient(private val keyProvider: () -> String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val hasKey: Boolean get() = keyProvider().isNotBlank()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(url: String): Request = Request.Builder().url(url)
        // DART는 User-Agent가 없으면 오류 페이지로 돌려보낸다.
        .header("User-Agent", "Mozilla/5.0 (Linux; Android) FSS_Helper")
        .header("Accept-Language", "ko-KR,ko;q=0.9")
        .get().build()

    private suspend fun get(name: String, params: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val key = keyProvider()
        if (key.isBlank()) throw DartException("OpenDART 인증키가 없습니다. 설정에서 인증키를 입력해 주세요.")
        val qs = (mapOf("crtfc_key" to key) + params).entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        val body = try {
            http.newCall(request("$BASE/$name.json?$qs")).execute().use { res ->
                if (res.code !in 200..299) throw DartException("DART 서버 응답 ${res.code}")
                res.body?.string().orEmpty()
            }
        } catch (e: DartException) {
            throw e
        } catch (e: Exception) {
            throw DartException(redactKey("opendart.fss.or.kr 에 연결하지 못했습니다. (${e.message ?: e::class.simpleName})"))
        }
        val o = runCatching { io.github.hdlee73.fsshelper.data.AppJson.parseToJsonElement(body).jsonObject }
            .getOrElse { throw DartException("DART 응답을 읽지 못했습니다.") }
        // 000 정상, 013 조회된 데이터 없음. 그 밖은 오류(010 등록되지 않은 키, 020 사용한도 초과 등)
        when (val st = o.str("status")) {
            "000", "013" -> o
            else -> throw DartException(redactKey("DART 오류 $st: ${o.str("message")}"))
        }
    }

    // ───────── 회사 목록(고유번호) ─────────

    /**
     * 전체 회사 고유번호 목록(corpCode.xml, zip)을 받아 [file]에 탭 구분으로 저장한다.
     * 약 10만 곳이며, 하루 한 번 이상 받을 필요는 없다.
     */
    suspend fun downloadCorpCodes(file: File): Int = withContext(Dispatchers.IO) {
        val key = keyProvider()
        if (key.isBlank()) throw DartException("OpenDART 인증키가 없습니다. 설정에서 인증키를 입력해 주세요.")
        val res = try {
            http.newCall(request("$BASE/corpCode.xml?crtfc_key=${enc(key)}")).execute()
        } catch (e: Exception) {
            throw DartException(redactKey("회사 목록을 받지 못했습니다. (${e.message ?: e::class.simpleName})"))
        }
        val corps = mutableListOf<DartCorp>()
        res.use { ZipInputStream(it.body!!.byteStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && !entry.name.endsWith(".xml", ignoreCase = true)) entry = zip.nextEntry
            if (entry == null) throw DartException("회사 목록 파일 형식이 예상과 다릅니다. 인증키를 확인해 주세요.")
            corps += parseCorpCodeXml(zip)
        } }
        if (corps.isEmpty()) throw DartException("회사 목록이 비어 있습니다.")
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w -> corps.forEach { w.append(it.corp).append('\t').append(it.name).append('\t').append(it.stock).append('\n') } }
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
        corps.size
    }

    // ───────── 기업개황 ─────────

    suspend fun company(corp: String): DartCompany {
        val r = get("company", mapOf("corp_code" to corp))
        if (r.str("status") == "013" || r.str("corp_name").isBlank()) throw DartException("회사 정보를 찾지 못했습니다.")
        val cls = r.str("corp_cls").takeIf { it in setOf("Y", "K", "N", "E") } ?: "E"
        return DartCompany(
            corp = corp, name = r.str("corp_name"), nameEn = r.str("corp_name_eng"), stock = r.str("stock_code").trim(), cls = cls,
            ceo = r.str("ceo_nm"), corpNo = r.str("jurir_no"), bizNo = r.str("bizr_no"), address = r.str("adres"),
            homepage = r.str("hm_url"), ir = r.str("ir_url"), phone = r.str("phn_no"), industryCode = r.str("induty_code"),
            established = r.str("est_dt"), accountMonth = r.str("acc_mt"),
        )
    }

    // ───────── 최대주주 및 특수관계인 ─────────

    suspend fun holders(corp: String): DartHolders {
        val now = LocalDate.now().year
        val tries = buildList {
            REPORTS.drop(1).forEach { add(Triple(now, it.first, it.second)) }
            add(Triple(now - 1, "11011", "사업보고서"))
            REPORTS.drop(1).forEach { add(Triple(now - 1, it.first, it.second)) }
            add(Triple(now - 2, "11011", "사업보고서"))
        }.take(7)
        for ((year, code, label) in tries) {
            val rows = get("hyslrSttus", mapOf("corp_code" to corp, "bsns_year" to year.toString(), "reprt_code" to code)).list()
            if (rows.isEmpty()) continue
            fun slot(x: JsonObject) = DartShare(num(x.str("trmend_posesn_stock_co")), num(x.str("trmend_posesn_stock_qota_rt")))
            fun isPref(x: JsonObject) = x.str("stock_knd").contains("우선")
            var totalCommon: Double? = null
            var totalPref: Double? = null
            val byKey = LinkedHashMap<String, DartHolder>()
            for (x in rows) {
                val name = x.str("nm").trim()
                if (name == "계" || name == "합계") {
                    if (isPref(x)) totalPref = slot(x).ratio else totalCommon = slot(x).ratio
                    continue
                }
                val relation = x.str("relate").trim()
                val k = "$name|$relation"
                val prev = byKey[k] ?: DartHolder(name, relation, null, null)
                byKey[k] = if (isPref(x)) prev.copy(pref = slot(x)) else prev.copy(common = slot(x))
            }
            val items = byKey.values.toList()
            fun sum(pref: Boolean) = Math.round(items.sumOf { (if (pref) it.pref else it.common)?.ratio ?: 0.0 } * 100) / 100.0
            return DartHolders(
                basis = "$year $label",
                items = items,
                totalCommon = totalCommon ?: if (items.any { it.common != null }) sum(false) else null,
                totalPref = totalPref ?: if (items.any { it.pref != null }) sum(true) else null,
            )
        }
        return DartHolders("", emptyList(), null, null)
    }

    // ───────── 공시 목록 ─────────

    /** [type]: a 정기보고서, m 주요사항보고서, e 기타공시. 기간은 최대 10년, 최대 400건 */
    suspend fun filings(corp: String, type: String, bgn: String, end: String): DartFilings = coroutineScope {
        val kinds = PBLNTF[type] ?: throw DartException("공시 종류가 올바르지 않습니다.")
        val (from, to) = dateRange(bgn, end)
        val maxPages = if (type == "a") 2 else 3
        suspend fun page(ty: String, n: Int) = get("list", mapOf(
            "corp_code" to corp, "bgn_de" to from, "end_de" to to, "pblntf_ty" to ty,
            "page_no" to n.toString(), "page_count" to "100", "sort" to "date", "sort_mth" to "desc",
        ))
        val parts = kinds.map { ty ->
            async {
                val first = page(ty, 1)
                val total = minOf(first.str("total_page").toIntOrNull() ?: 1, maxPages)
                val rest = (2..total).map { n -> async { page(ty, n) } }.awaitAll()
                (listOf(first) + rest).flatMap { it.list() }
            }
        }.awaitAll().flatten()
        val seen = HashSet<String>()
        val items = parts.filter { seen.add(it.str("rcept_no")) }.map {
            DartFiling(it.str("rcept_no"), it.str("report_nm").trim(), it.str("rcept_dt"), it.str("flr_nm").trim(), it.str("rm").trim(), VIEW + it.str("rcept_no"))
        }.sortedByDescending { it.date }.take(400)
        DartFilings(items, from, to)
    }

    // ───────── 재무 ─────────

    private suspend fun acnt(corp: String, year: Int, code: String): List<JsonObject> =
        get("fnlttSinglAcnt", mapOf("corp_code" to corp, "bsns_year" to year.toString(), "reprt_code" to code)).list()

    suspend fun finance(corp: String, accMonth: String): DartFinance = coroutineScope {
        val now = LocalDate.now().year
        val dec = accMonth.isBlank() || accMonth == "12"
        val q3 = async { acnt(corp, now, "11014") }
        val half = async { acnt(corp, now, "11012") }
        val q1 = async { acnt(corp, now, "11013") }
        val a1 = async { acnt(corp, now - 1, "11011") }
        val a2 = async { acnt(corp, now - 2, "11011") }
        val annual1 = a1.await()
        val annualRows = annual1.ifEmpty { a2.await() }
        val year = if (annual1.isNotEmpty()) now - 1 else now - 2
        if (annualRows.isEmpty()) return@coroutineScope DartFinance("", "사업보고서 재무 자료를 찾지 못했습니다.", year, "", emptyList(), emptyList(), emptyList(), emptyMap())
        val older = acnt(corp, year - 3, "11011")
        val quarter = when {
            q3.await().isNotEmpty() -> "11014" to q3.await()
            half.await().isNotEmpty() -> "11012" to half.await()
            q1.await().isNotEmpty() -> "11013" to q1.await()
            else -> null
        }
        val fs = if (annualRows.any { it.str("fs_div") == "CFS" }) "CFS" else "OFS"
        fun bs(rows: List<JsonObject>, f: String) = Triple(field(pick(rows, fs, "BS", "assets"), f), field(pick(rows, fs, "BS", "liab"), f), field(pick(rows, fs, "BS", "equity"), f))
        fun inc(rows: List<JsonObject>, f: String) = Triple(field(pick(rows, fs, "IS", "revenue"), f), field(pick(rows, fs, "IS", "op"), f), field(pick(rows, fs, "IS", "net"), f))
        fun yearEnd(y: Int) = if (dec) "$y.12" else "$y"
        fun bcol(label: String, t: Triple<Double?, Double?, Double?>) = BalanceCol(label, t.first, t.second, t.third)
        fun icol(label: String, sub: String, t: Triple<Double?, Double?, Double?>) = IncomeCol(label, sub, t.first, t.second, t.third)

        val balance = mutableListOf<BalanceCol>()
        val income = mutableListOf<IncomeCol>()
        if (quarter != null) {
            val (code, rows) = quarter
            val (qName, m1, m2, mStart) = QUARTER.getValue(code)
            balance += bcol(if (dec) "$now.$m2 ${qName}말" else "$now ${qName}말", bs(rows, "thstrm_amount"))
            val cur = inc(rows, "thstrm_amount")
            val cum = inc(rows, "thstrm_add_amount")
            val useCum = if (cum.first != null || cum.second != null || cum.third != null) cum else cur
            income += icol("분기", if (dec) "$now.$m1~$m2" else qName, cur)
            income += icol("누적", if (dec) "$now.$mStart~$m2" else "${qName}까지", useCum)
            income += icol("${year}년 연간", "전년", inc(annualRows, "thstrm_amount"))
            income += icol("${year - 1}년 연간", "전전년", inc(annualRows, "frmtrm_amount"))
        } else {
            income += icol("${year}년 연간", "", inc(annualRows, "thstrm_amount"))
            income += icol("${year - 1}년 연간", "", inc(annualRows, "frmtrm_amount"))
            income += icol("${year - 2}년 연간", "", inc(annualRows, "bfefrmtrm_amount"))
        }
        balance += bcol(yearEnd(year), bs(annualRows, "thstrm_amount"))
        balance += bcol(yearEnd(year - 1), bs(annualRows, "frmtrm_amount"))
        balance += bcol(yearEnd(year - 2), bs(annualRows, "bfefrmtrm_amount"))
        if (quarter == null && older.isNotEmpty()) balance += bcol(yearEnd(year - 3), bs(older, "thstrm_amount"))

        val points = LinkedHashMap<Int, TrendPoint>()
        fun put(y: Int, rows: List<JsonObject>, f: String) {
            val b = bs(rows, f)
            val i = inc(rows, f)
            val debt = if (b.third != null && b.third != 0.0 && b.second != null) Math.round(b.second!! / b.third!! * 1000) / 10.0 else null
            points[y] = TrendPoint(y, i.first, i.second, i.third, debt)
        }
        put(year, annualRows, "thstrm_amount")
        put(year - 1, annualRows, "frmtrm_amount")
        put(year - 2, annualRows, "bfefrmtrm_amount")
        if (older.isNotEmpty()) {
            put(year - 3, older, "thstrm_amount")
            put(year - 4, older, "frmtrm_amount")
        }
        val trend = points.values.sortedBy { it.year }.takeLast(5)

        val eps = HashMap<Int, Double>()
        suspend fun epsOf(y: Int) {
            val rows = get("fnlttSinglAcntAll", mapOf("corp_code" to corp, "bsns_year" to y.toString(), "reprt_code" to "11011", "fs_div" to fs)).list()
                .filter { it.str("sj_div") == "IS" || it.str("sj_div") == "CIS" }
            val cands = rows.filter { Regex("주당(순)?(이익|손익)|주당순이익").containsMatchIn(squash(it.str("account_nm"))) }
            val hit = cands.firstOrNull { squash(it.str("account_nm")).contains("기본") } ?: cands.firstOrNull() ?: return
            fun set(yy: Int, v: String) { num(v)?.let { if (eps[yy] == null) eps[yy] = it } }
            set(y, hit.str("thstrm_amount")); set(y - 1, hit.str("frmtrm_amount")); set(y - 2, hit.str("bfefrmtrm_amount"))
        }
        runCatching { epsOf(year); if (older.isNotEmpty()) epsOf(year - 3) }

        DartFinance(
            basis = if (fs == "CFS") "연결" else "별도", note = "", annualYear = year,
            quarterLabel = quarter?.let { "$now ${QUARTER.getValue(it.first).component1()}" }.orEmpty(),
            balance = balance, income = income, trend = trend, eps = eps,
        )
    }

    companion object {
        const val BASE = "https://opendart.fss.or.kr/api"
        const val VIEW = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo="

        private val REPORTS = listOf("11011" to "사업보고서", "11014" to "3분기보고서", "11012" to "반기보고서", "11013" to "1분기보고서")
        private val PBLNTF = mapOf("a" to listOf("A"), "m" to listOf("B"), "e" to listOf("D", "E", "I"))
        private val QUARTER = mapOf(
            "11013" to listOf("1분기", "01", "03", "01"),
            "11012" to listOf("반기", "04", "06", "01"),
            "11014" to listOf("3분기", "07", "09", "01"),
        )
        private val ACCOUNTS = mapOf(
            "assets" to listOf(Regex("^자산총계$")),
            "liab" to listOf(Regex("^부채총계$")),
            "equity" to listOf(Regex("^자본총계$")),
            "revenue" to listOf(Regex("^매출액$"), Regex("^수익\\(매출액\\)$"), Regex("^영업수익$"), Regex("^매출$"), Regex("^순영업수익$"), Regex("^보험수익$"), Regex("^수익$")),
            "op" to listOf(Regex("^영업이익"), Regex("^영업손익")),
            "net" to listOf(Regex("^당기순이익"), Regex("^당기순손익"), Regex("^(분기|반기)순이익"), Regex("^(분기|반기)순손익")),
        )

        private val YMD = DateTimeFormatter.BASIC_ISO_DATE

        /** 조회 기간을 확인하고, 시작이 끝보다 10년 넘게 앞서면 10년으로 줄인다. */
        fun dateRange(bgn: String, end: String, today: LocalDate = LocalDate.now()): Pair<String, String> {
            val t = today.format(YMD)
            fun ok(v: String) = v.length == 8 && v.all(Char::isDigit)
            val to = if (ok(end) && end <= t) end else t
            val earliest = (to.substring(0, 4).toInt() - 10).toString() + to.substring(4)
            var from = if (ok(bgn)) bgn else today.minusYears(1).format(YMD)
            if (from > to) from = to
            if (from < earliest) from = earliest
            return from to to
        }

        fun num(v: String?): Double? = v?.replace(",", "")?.trim()?.toDoubleOrNull()

        /** 원 → 억원(소수 둘째 자리) */
        fun toEok(n: Double?): Double? = n?.let { (it / 1e6).roundToLong() / 100.0 }

        fun squash(s: String) = s.replace(Regex("\\s+"), "")

        private fun pick(rows: List<JsonObject>, fs: String, sj: String, key: String): JsonObject? {
            val inSj = rows.filter { it.str("fs_div") == fs && (it.str("sj_div") == sj || (sj == "IS" && it.str("sj_div") == "CIS")) }
            for (re in ACCOUNTS.getValue(key)) inSj.firstOrNull { re.containsMatchIn(squash(it.str("account_nm"))) }?.let { return it }
            return null
        }

        private fun field(row: JsonObject?, f: String): Double? = row?.let { toEok(num(it.str(f))) }

        /** corpCode.xml 을 읽는다: <list><corp_code/><corp_name/><stock_code/></list> */
        fun parseCorpCodeXml(input: java.io.InputStream): List<DartCorp> {
            val r = XmlPullParserFactory.newInstance().newPullParser()
            r.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            r.setInput(input, "UTF-8")
            val out = ArrayList<DartCorp>(120_000)
            var tag = ""
            var code = StringBuilder(); var name = StringBuilder(); var stock = StringBuilder()
            var ev = r.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        tag = r.name
                        if (tag == "list") { code = StringBuilder(); name = StringBuilder(); stock = StringBuilder() }
                    }
                    XmlPullParser.TEXT -> when (tag) {
                        "corp_code" -> code.append(r.text)
                        "corp_name" -> name.append(r.text)
                        "stock_code" -> stock.append(r.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (r.name == "list" && code.isNotBlank() && name.isNotBlank()) {
                            out += DartCorp(code.toString().trim(), name.toString().trim(), stock.toString().trim())
                        }
                        tag = ""
                    }
                }
                ev = r.next()
            }
            return out
        }
    }
}

internal fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.list(): List<JsonObject> = (this["list"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

/** 회사명·종목코드 검색 색인. 이름이 일치할수록, 상장사일수록 앞에 둔다. */
class CorpIndex(private val corps: List<DartCorp>) {
    private data class Entry(val corp: DartCorp, val key: String)
    private val entries = corps.map { Entry(it, norm(it.name)) }
    val size: Int get() = corps.size

    fun search(query: String, limit: Int = 30): List<DartCorp> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val isCode = q.length == 6 && q.all(Char::isDigit)
        return entries.mapNotNull { e ->
            val base = when {
                isCode -> if (e.corp.stock == q) 100 else -1
                e.key == q -> 90
                e.key.startsWith(q) -> 70
                e.key.contains(q) -> 50
                else -> -1
            }
            if (base < 0) null else e to (base + (if (e.corp.stock.isNotBlank()) 5 else 0) - minOf(e.key.length, 30) / 100.0)
        }.sortedByDescending { it.second }.take(limit).map { it.first.corp }
    }

    companion object {
        fun norm(s: String): String = s.replace(Regex("""\(\s*주\s*\)|㈜|주식회사|\s+"""), "").lowercase()

        fun load(file: File): CorpIndex? = runCatching {
            if (!file.exists()) return null
            CorpIndex(file.readLines(Charsets.UTF_8).mapNotNull { line ->
                val p = line.split('\t')
                if (p.size >= 2) DartCorp(p[0], p[1], p.getOrElse(2) { "" }) else null
            })
        }.getOrNull()
    }
}

/** 회사 목록 파일 위치 */
val corpFile: File get() = File(AppDirs.cache, "dart-corps.tsv")

package io.github.hdlee73.fsshelper.dart

import com.hdlee73.everylaw.core.domain.HwpxExporter
import com.hdlee73.everylaw.core.domain.HwpxParagraph
import io.github.hdlee73.fsshelper.BuildInfo
import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.time.LocalDate

/** 기업 개황·최대주주·재무·최근 공시를 한 문서로 묶어 한글(HWPX)·PDF로 저장한다. */
object DartReport {
    private val NUM = DecimalFormat("#,##0.##")
    private fun n(v: Double?) = v?.let { NUM.format(it) } ?: "-"
    private fun ymd(s: String) = if (s.length == 8) "${s.substring(0, 4)}.${s.substring(4, 6)}.${s.substring(6)}" else s

    /** 화면에서 이미 불러온 것은 그대로 쓰고, 없는 것만 새로 받는다. 받지 못한 부분은 그 사유를 문서에 적는다. */
    suspend fun build(model: DartModel, corp: DartCorp): List<HwpxParagraph> = coroutineScope {
        val c = model.client
        fun <T> done(l: Load<T>): T? = (l as? Load.Done)?.value
        val company = async { done(model.company.value) ?: runCatching { c.company(corp.corp) }.getOrNull() }
        val holders = async { done(model.holders.value) ?: runCatching { c.holders(corp.corp) }.getOrNull() }
        val filings = async {
            done(model.filings.value) ?: runCatching {
                val today = LocalDate.now()
                c.filings(corp.corp, "a", today.minusYears(1).format(DartModel.YMD), today.format(DartModel.YMD))
            }.getOrNull()
        }
        val info = company.await()
        val finance = async { done(model.finance.value) ?: runCatching { c.finance(corp.corp, info?.accountMonth ?: "12") }.getOrNull() }
        val out = mutableListOf<HwpxParagraph>()
        out += HwpxParagraph("${corp.name} · 고유번호 ${corp.corp}" + (if (corp.stock.isNotBlank()) " · 종목코드 ${corp.stock}" else ""), HwpxParagraph.Style.SUBTITLE)
        out += HwpxParagraph("작성일 ${LocalDate.now()} · 자료: 금융감독원 전자공시(OpenDART)", HwpxParagraph.Style.META)

        out += HwpxParagraph("1. 기업개황", HwpxParagraph.Style.SECTION)
        if (info == null) out += HwpxParagraph("기업개황을 받지 못했습니다.", HwpxParagraph.Style.GRAY)
        else {
            out += HwpxParagraph("", cells = listOf("항목", "내용"))
            listOf(
                "정식 명칭" to info.name, "영문 명칭" to info.nameEn, "대표자" to info.ceo, "법인 구분" to dartMarketLabel(info.cls),
                "종목코드" to info.stock, "법인등록번호" to info.corpNo, "사업자등록번호" to info.bizNo, "주소" to info.address,
                "홈페이지" to info.homepage, "전화번호" to info.phone, "업종코드" to info.industryCode, "설립일" to ymd(info.established), "결산월" to info.accountMonth + "월",
            ).filter { it.second.isNotBlank() }.forEach { (k, v) -> out += HwpxParagraph("", cells = listOf(k, v)) }
        }

        out += HwpxParagraph("2. 최대주주 및 특수관계인 지분", HwpxParagraph.Style.SECTION)
        val h = holders.await()
        if (h == null || h.items.isEmpty()) out += HwpxParagraph("지분 자료가 없습니다(비상장·소규모 법인은 공시하지 않을 수 있습니다).", HwpxParagraph.Style.GRAY)
        else {
            out += HwpxParagraph("기준: ${h.basis}", HwpxParagraph.Style.META)
            out += HwpxParagraph("", cells = listOf("성명", "관계", "보통주 주식수", "보통주 지분율(%)", "우선주 지분율(%)"))
            h.items.forEach { x -> out += HwpxParagraph("", cells = listOf(x.name, x.relation, n(x.common?.shares), n(x.common?.ratio), n(x.pref?.ratio))) }
            out += HwpxParagraph("", cells = listOf("합계", "", "", n(h.totalCommon), n(h.totalPref)))
        }

        out += HwpxParagraph("3. 재무 현황 (단위: 억원)", HwpxParagraph.Style.SECTION)
        val f = finance.await()
        if (f == null) out += HwpxParagraph("재무 자료를 받지 못했습니다.", HwpxParagraph.Style.GRAY)
        else {
            out += HwpxParagraph(f.basis + (if (f.note.isNotBlank()) " · ${f.note}" else ""), HwpxParagraph.Style.META)
            if (f.balance.isNotEmpty()) {
                out += HwpxParagraph("재무상태", HwpxParagraph.Style.BOLD)
                out += HwpxParagraph("", cells = listOf("구분") + f.balance.map { it.label })
                out += HwpxParagraph("", cells = listOf("자산총계") + f.balance.map { n(it.assets) })
                out += HwpxParagraph("", cells = listOf("부채총계") + f.balance.map { n(it.liab) })
                out += HwpxParagraph("", cells = listOf("자본총계") + f.balance.map { n(it.equity) })
            }
            if (f.income.isNotEmpty()) {
                out += HwpxParagraph("손익", HwpxParagraph.Style.BOLD)
                out += HwpxParagraph("", cells = listOf("구분") + f.income.map { (it.label + " " + it.sub).trim() })
                out += HwpxParagraph("", cells = listOf("매출액(영업수익)") + f.income.map { n(it.revenue) })
                out += HwpxParagraph("", cells = listOf("영업이익") + f.income.map { n(it.op) })
                out += HwpxParagraph("", cells = listOf("당기순이익") + f.income.map { n(it.net) })
            }
        }

        out += HwpxParagraph("4. 최근 1년 정기공시", HwpxParagraph.Style.SECTION)
        val fl = filings.await()
        if (fl == null || fl.items.isEmpty()) out += HwpxParagraph("해당 기간 공시가 없거나 받지 못했습니다.", HwpxParagraph.Style.GRAY)
        else {
            out += HwpxParagraph("", cells = listOf("접수일", "보고서명", "제출인", "비고"))
            fl.items.take(60).forEach { x -> out += HwpxParagraph("", cells = listOf(ymd(x.date), x.title, x.by, x.note)) }
            if (fl.items.size > 60) out += HwpxParagraph("외 ${fl.items.size - 60}건", HwpxParagraph.Style.GRAY)
        }
        out += HwpxParagraph("")
        out += HwpxParagraph("※ OpenDART 공시 자료를 옮긴 것으로, 원문은 전자공시시스템(dart.fss.or.kr)에서 확인하십시오.", HwpxParagraph.Style.GRAY)
        out
    }

    private fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_")

    /** [ext]는 "hwpx" 또는 "pdf". 문서\FSS_Helper\기업개황 폴더에 저장한다. */
    suspend fun save(model: DartModel, corp: DartCorp, ext: String): File {
        val paras = build(model, corp)
        return withContext(Dispatchers.IO) {
            val dir = File(AppDirs.exports, "기업개황").apply { mkdirs() }
            val file = File(dir, safe("${corp.name}_기업개황_${LocalDate.now()}") + ".$ext")
            val title = "${corp.name} 기업 개황 및 공시"
            file.outputStream().use { out ->
                if (ext == "pdf") PdfWriter.write(title, paras, out, footer = "FSS_Helper ${BuildInfo.VERSION}")
                else HwpxExporter.writeDocument(title, paras, out)
            }
            file
        }
    }
}

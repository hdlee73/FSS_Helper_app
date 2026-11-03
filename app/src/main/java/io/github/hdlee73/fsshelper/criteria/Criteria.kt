package io.github.hdlee73.fsshelper.criteria

import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.JsonFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.text.DecimalFormat
import java.util.UUID

/*
 * 「금융기관 검사 및 제재에 관한 규정」과 같은 규정 시행세칙의 산정 틀.
 * 숫자는 모두 '기준 고치기'에서 바꿀 수 있고, 화면 오른쪽에는 국가법령정보에서 받아 온 현행 원문을 함께 보여 준다.
 * 과징금·과태료 기본값은 2017.10. 개정 규정(금융위원회 의결)의 산정 체계를 따른다.
 */

fun newKey(): String = UUID.randomUUID().toString().substring(0, 8)

/** 가중·감경 사유 한 줄. 금전 제재는 [percent](%), 신분 제재는 [steps](단계) */
@Serializable
data class Factor(val id: String = newKey(), val label: String, val percent: Double = 0.0, val steps: Int = 1)

// ───────────────── 과징금

/** 세부평가 참작사항 한 줄(비중과 상·중·하 설명) */
@Serializable
data class ScoreItem(val id: String = newKey(), val label: String, val weight: Double, val high: String = "", val mid: String = "", val low: String = "")

/** 산정점수 구간과 부과기준율 */
@Serializable
data class Band(val label: String, val minScore: Double, val rate: Double)

@Serializable
data class PenaltyCriteria(
    val items: List<ScoreItem> = DEFAULT_SCORE_ITEMS,
    /** 점수 높은 구간부터 */
    val bands: List<Band> = listOf(Band("매우 중대한 위반", 2.3, 100.0), Band("중대한 위반", 1.6, 75.0), Band("중대성이 약한 위반", 0.0, 50.0)),
    /** 기준표에서 고려하지 못한 사유로 부과기준율을 더하거나 뺄 수 있는 폭(%p) */
    val rateAdjustLimit: Double = 10.0,
    val aggravations: List<Factor> = listOf(Factor(label = "다수 위반자 중 주도·선도", percent = 20.0)),
    val mitigations: List<Factor> = listOf(
        Factor(label = "위반상태 자진 시정·치유", percent = 30.0),
        Factor(label = "자진신고", percent = 30.0),
        Factor(label = "위반 예방을 위한 자체감사·대책 마련", percent = 50.0),
        Factor(label = "경영실태평가 1등급", percent = 10.0),
        Factor(label = "정부시책 준수 과정에서 위반", percent = 30.0),
        Factor(label = "재무상태 고려 감액", percent = 50.0),
    ),
    /** 자본금(자기자본) 대비 상한(%) */
    val capitalCapPercent: Double = 10.0,
    /** 부당이득 대비 상한(배) */
    val gainCapMultiple: Double = 10.0,
    val roundUnit: Long = 10_000,
    val source: String = "검사·제재규정 제20조 및 [별표 2] 과징금 부과기준 체계(2017.10. 개정)",
) {
    fun bandOf(score: Double): Band = bands.sortedByDescending { it.minScore }.firstOrNull { score >= it.minScore - 1e-9 } ?: bands.last()
}

val DEFAULT_SCORE_ITEMS = listOf(
    ScoreItem(label = "위반행위의 동기", weight = 0.2, high = "고의", mid = "중과실", low = "경과실"),
    ScoreItem(label = "위반행위의 방법", weight = 0.2, high = "조직적·계획적·은폐", mid = "보통", low = "단순·우발"),
    ScoreItem(label = "위반행위의 정도(부당이득 규모)", weight = 0.2, high = "큼", mid = "보통", low = "작음"),
    ScoreItem(label = "피해 규모", weight = 0.2, high = "큼", mid = "보통", low = "작음·없음"),
    ScoreItem(label = "시장에 미치는 영향", weight = 0.1, high = "큼", mid = "보통", low = "작음"),
    ScoreItem(label = "위반기간 및 위반횟수", weight = 0.1, high = "장기·반복", mid = "보통", low = "단기·1회"),
)

data class PenaltyInput(
    /** 법정부과한도액(원) */
    val statutoryMax: Long,
    /** 참작사항 id → 3(상)·2(중)·1(하) */
    val scores: Map<String, Int>,
    /** 부과기준율 조정(%p, ±[PenaltyCriteria.rateAdjustLimit]) */
    val rateAdjust: Double = 0.0,
    val aggravations: Set<String> = emptySet(),
    val mitigations: Set<String> = emptySet(),
    /** 부당이득(원). 기본과징금보다 크면 차액을 가중한다. */
    val unjustGain: Long = 0,
    /** 자본금·자기자본(원). 0이면 상한 계산 안 함 */
    val capital: Long = 0,
    val exempt: Boolean = false,
)

data class CalcLine(val label: String, val value: String, val strong: Boolean = false)

data class PenaltyResult(val score: Double, val band: Band, val rate: Double, val base: Long, val final: Long, val lines: List<CalcLine>)

// ───────────────── 과태료

@Serializable
data class FineCriteria(
    /** 위반결과(행) */
    val results: List<String> = listOf("중대", "보통", "경미"),
    val resultHints: List<String> = listOf("언론 공표 등으로 공신력 실추, 손실 초래, 금융질서 저해", "중대·경미가 아닌 경우", "사회적 파급효과가 없고 금융거래자 피해가 없음"),
    /** 위반동기(열) */
    val motives: List<String> = listOf("상", "중", "하"),
    val motiveHints: List<String> = listOf("고의이며 특별히 참작할 사유 없음", "고의이나 참작 사유 있음 또는 중과실", "그 밖의 경우"),
    /** [결과][동기] → 법정최고금액 대비 비율(%) */
    val matrix: List<List<Double>> = listOf(listOf(100.0, 80.0, 60.0), listOf(80.0, 60.0, 40.0), listOf(60.0, 40.0, 20.0)),
    val rateAdjustLimit: Double = 10.0,
    val aggravations: List<Factor> = listOf(Factor(label = "동일 법규 위반 재범", percent = 20.0)),
    val mitigations: List<Factor> = listOf(
        Factor(label = "자진신고", percent = 30.0),
        Factor(label = "자진시정", percent = 30.0),
        Factor(label = "금융거래자 피해 배상", percent = 30.0),
        Factor(label = "건별 합산액이 현저히 과중", percent = 50.0),
    ),
    /** 이 금액 미만이면 면제할 수 있다 */
    val exemptBelow: Long = 100_000,
    /** 개인에 대한 건별 합계 한도(법정최고금액의 배수) */
    val personCapMultiple: Double = 10.0,
    val roundUnit: Long = 10_000,
    val source: String = "검사·제재규정 [별표 3] 과태료 부과기준 체계(2017.10. 개정)",
)

data class FineInput(
    val statutoryMax: Long,
    /** 시행령 별표 등에 개별 기준금액이 있으면 그 금액(원). 0이면 법정최고금액 × 예정비율 */
    val presetAmount: Long = 0,
    val result: Int = 1,
    val motive: Int = 1,
    val rateAdjust: Double = 0.0,
    val aggravations: Set<String> = emptySet(),
    val mitigations: Set<String> = emptySet(),
)

data class FineResult(val rate: Double, val base: Long, val final: Long, val exemptable: Boolean, val lines: List<CalcLine>)

// ───────────────── 양정(신분 제재)

@Serializable
data class LevelTarget(
    val id: String,
    val name: String,
    /** 무거운 것부터 */
    val levels: List<String>,
    /** [위반 정도][고의·과실] → [levels]의 번호 */
    val matrix: List<List<Int>>,
    val aggravations: List<Factor>,
    val mitigations: List<Factor>,
    val aggravationCapSteps: Int = 1,
    val mitigationCapSteps: Int = 2,
    val basis: String = "",
)

@Serializable
data class LevelCriteria(
    val degrees: List<String> = listOf("중대", "보통", "경미"),
    val degreeHints: List<String> = listOf("금융질서·건전경영을 크게 해치거나 손실·피해가 큰 경우", "중대·경미가 아닌 경우", "피해가 없거나 경미하고 바로 시정된 경우"),
    val faults: List<String> = listOf("고의", "중과실", "경과실"),
    val targets: List<LevelTarget> = DEFAULT_TARGETS,
    val source: String = "검사·제재규정 제17조~제19조, 시행세칙 제45조~제50조 및 제재양정 관련 별표 — 기준표 칸 값은 예시이니 원문과 대조해 고치세요",
)

private val COMMON_AGG = listOf(
    Factor(label = "최근 3년 이내 제재 받은 사실(재범)", steps = 1),
    Factor(label = "위법·부당행위가 2개 이상(경합)", steps = 1),
    Factor(label = "검사 방해·자료 은폐", steps = 1),
)
private val COMMON_MIT = listOf(
    Factor(label = "자진신고·자진시정", steps = 1),
    Factor(label = "표창 공적(감경 대상 공적)", steps = 1),
    Factor(label = "피해 회복·손실 보전", steps = 1),
    Factor(label = "상급자 지시에 따른 행위", steps = 1),
)

val DEFAULT_TARGETS = listOf(
    LevelTarget(
        "exec", "임원",
        listOf("해임권고(해임요구)", "직무정지(업무집행정지)", "문책경고", "주의적경고", "주의"),
        listOf(listOf(0, 1, 2), listOf(1, 2, 3), listOf(2, 3, 4)),
        COMMON_AGG, COMMON_MIT, basis = "규정 제18조(임원에 대한 제재)",
    ),
    LevelTarget(
        "staff", "직원",
        listOf("면직", "정직", "감봉", "견책", "주의"),
        listOf(listOf(0, 1, 2), listOf(1, 2, 3), listOf(2, 3, 4)),
        COMMON_AGG, COMMON_MIT, basis = "규정 제19조(직원에 대한 제재), 세칙 제45조·제49조·제50조",
    ),
    LevelTarget(
        "inst", "기관",
        listOf("인가·등록 취소", "영업 전부정지", "영업 일부정지", "기관경고", "기관주의"),
        listOf(listOf(1, 2, 3), listOf(2, 3, 4), listOf(3, 4, 4)),
        listOf(Factor(label = "최근 3년 이내 기관제재(재범)", steps = 1), Factor(label = "다수 임직원 관련·조직적 위반", steps = 1)),
        listOf(Factor(label = "자진신고·자진시정", steps = 1), Factor(label = "내부통제 개선·피해 구제", steps = 1)),
        basis = "규정 제17조(기관에 대한 제재)",
    ),
)

data class LevelResult(val base: String, val final: String, val range: String, val lines: List<CalcLine>)

@Serializable
data class CriteriaData(
    val penalty: PenaltyCriteria = PenaltyCriteria(),
    val fine: FineCriteria = FineCriteria(),
    val level: LevelCriteria = LevelCriteria(),
)

// ───────────────── 계산

object Calc {
    private val wonFmt = DecimalFormat("#,##0")
    fun won(v: Long): String = wonFmt.format(v) + "원"
    fun pct(v: Double): String = if (v == Math.floor(v)) "${v.toLong()}%" else "${"%.1f".format(v)}%"
    private fun floorTo(v: Long, unit: Long) = if (unit <= 1) v else v / unit * unit

    /**
     * 과징금: 법정부과한도액 × 부과기준율(세부평가 산정점수 구간 ± 조정) = 기본과징금.
     * 가중액(사유 비율 합 + 부당이득 초과 차액)과 감경액(사유 비율 합)을 합산해 기본과징금에 더하고 뺀 뒤,
     * 자본금 대비·부당이득 대비 상한을 넘으면 넘는 만큼 깎는다.
     */
    fun penalty(c: PenaltyCriteria, input: PenaltyInput): PenaltyResult {
        val score = c.items.sumOf { it.weight * (input.scores[it.id] ?: 2) }
        val band = c.bandOf(score)
        val adj = input.rateAdjust.coerceIn(-c.rateAdjustLimit, c.rateAdjustLimit)
        val rate = (band.rate + adj).coerceAtLeast(0.0)
        val base = Math.round(input.statutoryMax * rate / 100.0)
        val aggPct = c.aggravations.filter { it.id in input.aggravations }.sumOf { it.percent }
        val gainDiff = (input.unjustGain - base).coerceAtLeast(0)
        val agg = Math.round(base * aggPct / 100.0) + gainDiff
        val mitPct = c.mitigations.filter { it.id in input.mitigations }.sumOf { it.percent }.coerceAtMost(100.0)
        val mit = Math.round(base * mitPct / 100.0)
        val adjusted = (base + agg - mit).coerceAtLeast(0)
        var capped = adjusted
        val lines = mutableListOf(
            CalcLine("세부평가 산정점수", "%.2f점".format(score) + " → ${band.label}"),
            CalcLine("부과기준율", pct(band.rate) + if (adj != 0.0) " ${if (adj > 0) "+" else ""}${pct(adj)} = ${pct(rate)}" else ""),
            CalcLine("기본과징금", "${won(input.statutoryMax)} × ${pct(rate)} = ${won(base)}", true),
            CalcLine("가중", "${pct(aggPct)} → ${won(Math.round(base * aggPct / 100.0))}" + if (gainDiff > 0) " + 부당이득 초과 차액 ${won(gainDiff)}" else ""),
            CalcLine("감경", "${pct(mitPct)} → ${won(mit)}"),
            CalcLine("가중·감경 합산", "${won(base)} + ${won(agg)} − ${won(mit)} = ${won(adjusted)}"),
        )
        if (input.capital > 0) {
            val cap = Math.round(input.capital * c.capitalCapPercent / 100.0)
            if (capped > cap) { lines += CalcLine("자본금 대비 상한", "${pct(c.capitalCapPercent)} ${won(cap)}로 감액"); capped = cap }
        }
        if (input.unjustGain > 0) {
            val cap = Math.round(input.unjustGain * c.gainCapMultiple)
            if (capped > cap) { lines += CalcLine("부당이득 대비 상한", "${c.gainCapMultiple.toLong()}배 ${won(cap)}로 감액"); capped = cap }
        }
        if (input.statutoryMax in 1 until capped) { lines += CalcLine("법정부과한도액", "${won(input.statutoryMax)}로 제한"); capped = input.statutoryMax }
        val final = if (input.exempt) 0 else floorTo(capped, c.roundUnit)
        if (c.roundUnit > 1) lines += CalcLine("끝수", "${won(c.roundUnit)} 미만 버림")
        if (input.exempt) lines += CalcLine("면제", "위법하지 않다고 오인한 데 정당한 사유가 있어 면제")
        lines += CalcLine("과징금 부과액", won(final), true)
        return PenaltyResult(score, band, rate, base, final, lines)
    }

    /** 과태료: 법정최고금액 × 예정비율(위반결과 × 동기, ± 조정) 또는 개별 기준금액 → 가중 → 감경 → 끝수 */
    fun fine(c: FineCriteria, input: FineInput): FineResult {
        val r = input.result.coerceIn(0, c.results.size - 1)
        val m = input.motive.coerceIn(0, c.motives.size - 1)
        val preset = c.matrix.getOrNull(r)?.getOrNull(m) ?: 0.0
        val adj = input.rateAdjust.coerceIn(-c.rateAdjustLimit, c.rateAdjustLimit)
        val rate = (preset + adj).coerceIn(0.0, 100.0)
        val base = if (input.presetAmount > 0) input.presetAmount else Math.round(input.statutoryMax * rate / 100.0)
        val aggPct = c.aggravations.filter { it.id in input.aggravations }.sumOf { it.percent }
        val mitPct = c.mitigations.filter { it.id in input.mitigations }.sumOf { it.percent }.coerceAtMost(100.0)
        val afterAgg = Math.round(base * (1 + aggPct / 100.0))
        val afterMit = Math.round(afterAgg * (1 - mitPct / 100.0)).coerceAtLeast(0)
        var amount = afterMit
        val lines = mutableListOf(
            CalcLine("예정비율", "위반결과 '${c.results[r]}' × 동기 '${c.motives[m]}' = ${pct(preset)}" + if (adj != 0.0) " ${if (adj > 0) "+" else ""}${pct(adj)} = ${pct(rate)}" else ""),
            CalcLine("기준금액", if (input.presetAmount > 0) "개별 기준금액 ${won(base)}" else "${won(input.statutoryMax)} × ${pct(rate)} = ${won(base)}", true),
            CalcLine("가중", "+${pct(aggPct)} → ${won(afterAgg)}"),
            CalcLine("감경", "−${pct(mitPct)} → ${won(afterMit)}"),
        )
        if (input.statutoryMax in 1 until amount) { lines += CalcLine("법정최고금액", "${won(input.statutoryMax)}로 제한"); amount = input.statutoryMax }
        val final = floorTo(amount, c.roundUnit)
        if (c.roundUnit > 1) lines += CalcLine("끝수", "${won(c.roundUnit)} 미만 버림")
        val exemptable = final in 1 until c.exemptBelow
        if (exemptable) lines += CalcLine("면제 가능", "${won(c.exemptBelow)} 미만이라 면제할 수 있습니다")
        lines += CalcLine("과태료 부과액", won(final), true)
        return FineResult(rate, base, final, exemptable, lines)
    }

    /** 신분 제재: 기준표(위반 정도 × 고의·과실)로 기본 양정 → 가중(무겁게)·감경(가볍게) 단계를 상한 안에서 적용 */
    fun level(t: LevelTarget, c: LevelCriteria, degree: Int, fault: Int, override: Int?, agg: Set<String>, mit: Set<String>): LevelResult {
        val fromMatrix = t.matrix.getOrNull(degree)?.getOrNull(fault) ?: (t.levels.size - 1)
        val baseIdx = (override ?: fromMatrix).coerceIn(0, t.levels.size - 1)
        val aggSum = t.aggravations.filter { it.id in agg }.sumOf { it.steps }
        val mitSum = t.mitigations.filter { it.id in mit }.sumOf { it.steps }
        val a = minOf(aggSum, t.aggravationCapSteps)
        val m = minOf(mitSum, t.mitigationCapSteps)
        val target = baseIdx - a + m
        val clipped = target !in t.levels.indices
        val finalIdx = target.coerceIn(0, t.levels.size - 1)
        val lo = (finalIdx + 1).coerceAtMost(t.levels.size - 1)
        val hi = (finalIdx - 1).coerceAtLeast(0)
        val range = if (lo == finalIdx && hi == finalIdx) t.levels[finalIdx] else "${t.levels[finalIdx]}(${t.levels[lo]} ~ ${t.levels[hi]})"
        val lines = listOf(
            CalcLine("기본 양정", (if (override != null) "직접 선택" else "위반 정도 '${c.degrees.getOrElse(degree) { "" }}' × '${c.faults.getOrElse(fault) { "" }}'") + " → ${t.levels[baseIdx]}", true),
            CalcLine("가중", "${a}단계" + if (aggSum > a) " (사유 합 ${aggSum}단계 → 상한 ${t.aggravationCapSteps}단계)" else ""),
            CalcLine("감경", "${m}단계" + if (mitSum > m) " (사유 합 ${mitSum}단계 → 상한 ${t.mitigationCapSteps}단계)" else ""),
            CalcLine("양정 결과", t.levels[finalIdx] + if (clipped) " (가장 무겁거나 가벼운 단계에서 멈춤)" else "", true),
            CalcLine("사전통지 표기", range),
        )
        return LevelResult(t.levels[baseIdx], t.levels[finalIdx], range, lines)
    }
}

class CriteriaStore(file: File = File(AppDirs.root, "criteria.json")) {
    private val store = JsonFile(file, CriteriaData.serializer()) { CriteriaData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<CriteriaData> = _data.asStateFlow()

    fun update(block: (CriteriaData) -> CriteriaData) {
        _data.update(block)
        runCatching { store.write(_data.value) }
    }

    fun resetPenalty() = update { it.copy(penalty = PenaltyCriteria()) }
    fun resetFine() = update { it.copy(fine = FineCriteria()) }
    fun resetLevel() = update { it.copy(level = LevelCriteria()) }
}

/** 123,450,000 → "1억 2,345만 원" */
fun koreanWon(v: Long): String {
    if (v <= 0) return ""
    val eok = v / 100_000_000
    val man = (v % 100_000_000) / 10_000
    val won = v % 10_000
    return buildList {
        if (eok > 0) add("%,d억".format(eok))
        if (man > 0) add("%,d만".format(man))
        if (won > 0 || isEmpty()) add("%,d".format(won))
    }.joinToString(" ") + " 원"
}

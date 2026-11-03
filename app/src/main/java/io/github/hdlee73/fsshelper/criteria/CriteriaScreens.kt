package io.github.hdlee73.fsshelper.criteria

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.ui.InfoBox
import io.github.hdlee73.fsshelper.ui.Os
import io.github.hdlee73.fsshelper.ui.ScreenHeader

/** 세 기준 화면이 함께 쓰는 것 */
class CriteriaEnv(
    val store: CriteriaStore,
    val regulation: RegulationModel,
    val openLaw: (String) -> Unit,
    val message: (String) -> Unit,
)

/** 휴대폰: 위쪽 탭으로 '시뮬레이션'과 '규정 원문'을 오간다. */
@Composable
private fun CriteriaLayout(
    title: String,
    subtitle: String,
    source: String,
    editing: Boolean,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    topic: Topic,
    env: CriteriaEnv,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, subtitle) {
            if (editing) TextButton(onClick = onReset) { Text("기본값으로") }
            OutlinedButton(onClick = onEdit) { Text(if (editing) "고치기 끝" else "기준 고치기") }
        }
        var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
        androidx.compose.material3.TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            androidx.compose.material3.Tab(tab == 0, { tab = 0 }, text = { Text("시뮬레이션") })
            androidx.compose.material3.Tab(tab == 1, { tab = 1 }, text = { Text("규정 원문") })
        }
        if (tab == 0) {
            val sc = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(sc).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoBox("기준: $source. '규정 원문' 탭은 국가법령정보에서 받은 현행 원문입니다. 다르면 '기준 고치기'로 숫자를 맞추세요(이 기기에만 저장).")
                content()
            }
        } else {
            RegulationPane(env.regulation, topic, env.openLaw, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Section(title: String, trailing: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

/** 결과 카드: 산정 과정 한 줄씩, 맨 아래 굵게 */
@Composable
private fun ResultCard(title: String, lines: List<CalcLine>, onMessage: (String) -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                TextButton(onClick = { Os.copy(lines.joinToString("\n") { "${it.label}: ${it.value}" }); onMessage("산정 과정을 복사했어요.") }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("복사")
                }
            }
            lines.forEach { l ->
                Row {
                    Text(l.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), modifier = Modifier.width(104.dp))
                    Text(l.value, style = if (l.strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (l.strong) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ───────────────────────────── 과징금

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PenaltyScreen(env: CriteriaEnv) {
    val data by env.store.data.collectAsState()
    val c = data.penalty
    var editing by remember { mutableStateOf(false) }
    var statMax by remember { mutableStateOf(0L) }
    var related by remember { mutableStateOf(0L) }
    var legalRate by remember { mutableStateOf("") }
    var scores by remember { mutableStateOf(mapOf<String, Int>()) }
    var adjust by remember { mutableStateOf(0.0) }
    var agg by remember { mutableStateOf(setOf<String>()) }
    var mit by remember { mutableStateOf(setOf<String>()) }
    var gain by remember { mutableStateOf(0L) }
    var capital by remember { mutableStateOf(0L) }
    var exempt by remember { mutableStateOf(false) }
    fun upd(block: (PenaltyCriteria) -> PenaltyCriteria) = env.store.update { it.copy(penalty = block(it.penalty)) }

    val result = Calc.penalty(c, PenaltyInput(statMax, scores, adjust, agg, mit, gain, capital, exempt))
    CriteriaLayout("과징금 산정기준", "기본과징금(법정부과한도액 × 부과기준율) → 가중·감경 → 상한", c.source, editing, { editing = !editing }, env.store::resetPenalty, Topic.PENALTY, env) {
        ResultCard("산정 결과  ${Calc.won(result.final)}", result.lines, env.message)

        Section("1. 법정부과한도액") {
            MoneyField("법정부과한도액", statMax) { statMax = it }
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("위반 관련 금액(선택)", related, Modifier.weight(1f)) { related = it }
                NumberField("법정 비율(%)", legalRate, Modifier.width(130.dp)) { legalRate = it }
                TextButton(onClick = { legalRate.toDoubleOrNull()?.let { r -> statMax = Math.round(related * r / 100.0) } }, enabled = related > 0 && legalRate.toDoubleOrNull() != null) { Text("한도액으로") }
            }
            Hint("법률이 '위반 금액의 N% 이하'처럼 정한 경우 위반 관련 금액과 비율로 한도액을 구할 수 있습니다.")
        }

        Section("2. 위반행위의 중대성(세부평가)", trailing = { Text("산정점수 %.2f · ${result.band.label}".format(result.score), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }) {
            c.items.forEach { item ->
                if (editing) {
                    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextBox(item.label, Modifier.weight(1f)) { v -> upd { p -> p.copy(items = p.items.map { if (it.id == item.id) it.copy(label = v) else it }) } }
                        NumberField("비중", item.weight.toString(), Modifier.width(90.dp)) { v -> v.toDoubleOrNull()?.let { w -> upd { p -> p.copy(items = p.items.map { if (it.id == item.id) it.copy(weight = w) else it }) } } }
                        IconButton(onClick = { upd { p -> p.copy(items = p.items.filter { it.id != item.id }) } }) { Icon(Icons.Outlined.Close, "빼기", Modifier.size(16.dp)) }
                    }
                } else {
                    val v = scores[item.id] ?: 2
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text("비중 ${item.weight}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3 to "상" + item.high.hintSuffix(), 2 to "중" + item.mid.hintSuffix(), 1 to "하" + item.low.hintSuffix()).forEach { (n, l) ->
                            FilterChip(v == n, { scores = scores + (item.id to n) }, label = { Text(l, maxLines = 1) })
                        }
                    }
                }
            }
            if (editing) TextButton(onClick = { upd { p -> p.copy(items = p.items + ScoreItem(label = "새 참작사항", weight = 0.1)) } }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text("참작사항 더하기") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("중대성 구분과 부과기준율", style = MaterialTheme.typography.labelLarge)
            c.bands.forEachIndexed { i, b ->
                if (editing) FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextBox(b.label, Modifier.weight(1f)) { v -> upd { p -> p.copy(bands = p.bands.mapIndexed { k, x -> if (k == i) x.copy(label = v) else x }) } }
                    NumberField("점수 이상", b.minScore.toString(), Modifier.width(110.dp)) { v -> v.toDoubleOrNull()?.let { n -> upd { p -> p.copy(bands = p.bands.mapIndexed { k, x -> if (k == i) x.copy(minScore = n) else x }) } } }
                    NumberField("기준율(%)", b.rate.toString(), Modifier.width(110.dp)) { v -> v.toDoubleOrNull()?.let { n -> upd { p -> p.copy(bands = p.bands.mapIndexed { k, x -> if (k == i) x.copy(rate = n) else x }) } } }
                } else Row {
                    val on = b == result.band
                    Text((if (on) "▶ " else "   ") + b.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text("${b.minScore}점 이상 · ${Calc.pct(b.rate)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("기준율 조정(±${Calc.pct(c.rateAdjustLimit)}p)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                listOf(-c.rateAdjustLimit, -5.0, 0.0, 5.0, c.rateAdjustLimit).distinct().forEach { d ->
                    FilterChip(adjust == d, { adjust = d }, label = { Text(if (d > 0) "+${Calc.pct(d)}" else Calc.pct(d)) })
                }
            }
            Hint("기준표에서 고려하지 못했거나 달리 볼 사유가 있으면 조정하고, 그 이유를 조치안에 적습니다.")
        }

        Section("3. 가중·감경") {
            FactorList("가중 사유", c.aggravations, agg, editing, percent = true, onPick = { agg = it }, onChange = { l -> upd { it.copy(aggravations = l) } })
            MoneyField("부당이득(기본과징금보다 크면 차액 가중)", gain) { gain = it }
            FactorList("감경 사유", c.mitigations, mit, editing, percent = true, onPick = { mit = it }, onChange = { l -> upd { it.copy(mitigations = l) } })
            Hint("가중액과 감경액을 먼저 합산한 뒤 기본과징금에 더하고 뺍니다.")
        }

        Section("4. 상한·면제") {
            MoneyField("자본금·자기자본(선택, ${Calc.pct(c.capitalCapPercent)} 상한)", capital) { capital = it }
            if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("자본금 상한(%)", c.capitalCapPercent.toString(), Modifier.width(150.dp)) { v -> v.toDoubleOrNull()?.let { n -> upd { it.copy(capitalCapPercent = n) } } }
                NumberField("부당이득 상한(배)", c.gainCapMultiple.toString(), Modifier.width(150.dp)) { v -> v.toDoubleOrNull()?.let { n -> upd { it.copy(gainCapMultiple = n) } } }
                NumberField("끝수 단위(원)", c.roundUnit.toString(), Modifier.width(150.dp)) { v -> v.toLongOrNull()?.let { n -> upd { it.copy(roundUnit = n) } } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(exempt, { exempt = it })
                Text("위법하지 않다고 오인한 데 정당한 사유가 있음(면제)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun String.hintSuffix() = if (isBlank()) "" else "($this)"

// ───────────────────────────── 과태료

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FineScreen(env: CriteriaEnv) {
    val data by env.store.data.collectAsState()
    val c = data.fine
    var editing by remember { mutableStateOf(false) }
    var statMax by remember { mutableStateOf(0L) }
    var preset by remember { mutableStateOf(0L) }
    var res by remember { mutableStateOf(1) }
    var mot by remember { mutableStateOf(1) }
    var adjust by remember { mutableStateOf(0.0) }
    var agg by remember { mutableStateOf(setOf<String>()) }
    var mit by remember { mutableStateOf(setOf<String>()) }
    var memo by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf<Triple<String, Long, Long>>() }
    fun upd(block: (FineCriteria) -> FineCriteria) = env.store.update { it.copy(fine = block(it.fine)) }

    val result = Calc.fine(c, FineInput(statMax, preset, res, mot, adjust, agg, mit))
    CriteriaLayout("과태료 산정기준", "법정최고금액 × 예정비율(위반결과 × 동기) → 가중·감경", c.source, editing, { editing = !editing }, env.store::resetFine, Topic.FINE, env) {
        ResultCard("산정 결과  ${Calc.won(result.final)}", result.lines, env.message)

        Section("1. 법정최고금액") {
            MoneyField("법정최고금액(법률에 정한 과태료 한도)", statMax) { statMax = it }
            MoneyField("개별 기준금액(시행령 별표 등에 정해져 있으면, 선택)", preset) { preset = it }
        }

        Section("2. 예정금액 산정표(위반결과 × 동기)") {
            Row {
                Spacer(Modifier.width(64.dp))
                c.motives.forEach { m -> Text("동기\n$m", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
            }
            c.results.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("결과 $r", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                    c.motives.indices.forEach { j ->
                        val v = c.matrix.getOrNull(i)?.getOrNull(j) ?: 0.0
                        if (editing) NumberField("", v.toString(), Modifier.weight(1f).padding(2.dp)) { t -> t.toDoubleOrNull()?.let { n -> upd { f -> f.copy(matrix = f.matrix.mapIndexed { a, row -> if (a == i) row.mapIndexed { b, x -> if (b == j) n else x } else row }) } } }
                        else MatrixCell(Calc.pct(v), res == i && mot == j, Modifier.weight(1f)) { res = i; mot = j }
                    }
                }
            }
            c.results.forEachIndexed { i, r -> Hint("결과 '$r': ${c.resultHints.getOrElse(i) { "" }}") }
            c.motives.forEachIndexed { i, m -> Hint("동기 '$m': ${c.motiveHints.getOrElse(i) { "" }}") }
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("예정비율 조정(±${Calc.pct(c.rateAdjustLimit)}p)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                listOf(-c.rateAdjustLimit, 0.0, c.rateAdjustLimit).distinct().forEach { d -> FilterChip(adjust == d, { adjust = d }, label = { Text(if (d > 0) "+${Calc.pct(d)}" else Calc.pct(d)) }) }
            }
            Hint("검사를 거부·방해·기피한 경우 위반결과는 '중대'로 봅니다.")
        }

        Section("3. 가중·감경") {
            FactorList("가중 사유", c.aggravations, agg, editing, percent = true, onPick = { agg = it }, onChange = { l -> upd { it.copy(aggravations = l) } })
            FactorList("감경 사유", c.mitigations, mit, editing, percent = true, onPick = { mit = it }, onChange = { l -> upd { it.copy(mitigations = l) } })
            if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("면제 기준(원 미만)", c.exemptBelow.toString(), Modifier.width(160.dp)) { v -> v.toLongOrNull()?.let { n -> upd { it.copy(exemptBelow = n) } } }
                NumberField("개인 합계 한도(배)", c.personCapMultiple.toString(), Modifier.width(160.dp)) { v -> v.toDoubleOrNull()?.let { n -> upd { it.copy(personCapMultiple = n) } } }
                NumberField("끝수 단위(원)", c.roundUnit.toString(), Modifier.width(150.dp)) { v -> v.toLongOrNull()?.let { n -> upd { it.copy(roundUnit = n) } } }
            }
        }

        Section("4. 여러 위반 합산(위반행위별로 각각 부과)") {
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextBox(memo, Modifier.weight(1f), placeholder = "위반 내용 메모") { memo = it }
                OutlinedButton(onClick = { rows += Triple(memo.ifBlank { "위반 ${rows.size + 1}" }, result.final, statMax); memo = "" }, enabled = result.final > 0) { Text("지금 결과 더하기") }
            }
            rows.forEachIndexed { i, (m, amt, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. $m", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Calc.won(amt), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Outlined.Close, "빼기", Modifier.size(14.dp)) }
                }
            }
            if (rows.isNotEmpty()) {
                val total = rows.sumOf { it.second }
                val cap = Math.round((rows.maxOf { it.third }) * c.personCapMultiple)
                Text("합계 ${Calc.won(total)}" + if (cap in 1 until total) " → 개인 한도(법정최고금액의 ${c.personCapMultiple.toLong()}배) ${Calc.won(cap)}로 제한" else "",
                    style = MaterialTheme.typography.titleSmall)
                Hint("합산액이 현저히 과중하면 감경 사유('건별 합산액이 현저히 과중')를 검토하세요.")
            }
        }
    }
}

// ───────────────────────────── 양정기준

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelScreen(env: CriteriaEnv) {
    val data by env.store.data.collectAsState()
    val c = data.level
    var editing by remember { mutableStateOf(false) }
    var targetId by remember { mutableStateOf(c.targets.firstOrNull()?.id ?: "") }
    var degree by remember { mutableStateOf(1) }
    var fault by remember { mutableStateOf(1) }
    var override by remember { mutableStateOf<Int?>(null) }
    var agg by remember { mutableStateOf(setOf<String>()) }
    var mit by remember { mutableStateOf(setOf<String>()) }
    val t = c.targets.firstOrNull { it.id == targetId } ?: c.targets.first()
    fun updTarget(block: (LevelTarget) -> LevelTarget) = env.store.update { d -> d.copy(level = d.level.copy(targets = d.level.targets.map { if (it.id == t.id) block(it) else it })) }

    val result = Calc.level(t, c, degree, fault, override, agg, mit)
    CriteriaLayout("양정기준", "제재 대상별 기준표(위반 정도 × 고의·과실) → 가중·감경 단계", c.source, editing, { editing = !editing }, env.store::resetLevel, Topic.LEVEL, env) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.targets.forEach { x -> FilterChip(x.id == t.id, { targetId = x.id; override = null; agg = emptySet(); mit = emptySet() }, label = { Text(x.name) }) }
        }
        ResultCard("${t.name} 양정 결과  ${result.final}", result.lines, env.message)

        Section("1. 제재 단계(무거운 것부터)") {
            if (editing) {
                t.levels.forEachIndexed { i, l ->
                    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextBox(l, Modifier.weight(1f)) { v -> updTarget { x -> x.copy(levels = x.levels.mapIndexed { k, o -> if (k == i) v else o }) } }
                    }
                }
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                t.levels.forEachIndexed { i, l -> FilterChip(override == i, { override = if (override == i) null else i }, label = { Text("${i + 1}. $l") }) }
            }
            Hint(if (override != null) "기본 양정을 직접 골랐습니다. 다시 누르면 기준표로 돌아갑니다." else "기준표 대신 기본 양정을 직접 고르려면 위 단계를 누르세요. ${t.basis}")
        }

        Section("2. 기준표(위반 정도 × 고의·과실)") {
            Row {
                Spacer(Modifier.width(96.dp))
                c.faults.forEach { f -> Text(f, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
            }
            c.degrees.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("정도 $d", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
                    c.faults.indices.forEach { j ->
                        val idx = t.matrix.getOrNull(i)?.getOrNull(j) ?: (t.levels.size - 1)
                        if (editing) {
                            MatrixCell(t.levels.getOrElse(idx) { "" }, false, Modifier.weight(1f)) {
                                // 누를 때마다 한 단계 가볍게(끝에서 다시 가장 무겁게)
                                updTarget { x -> x.copy(matrix = x.matrix.mapIndexed { a, row -> if (a == i) row.mapIndexed { b, v -> if (b == j) (v + 1) % x.levels.size else v } else row }) }
                            }
                        } else MatrixCell(t.levels.getOrElse(idx) { "" }, override == null && degree == i && fault == j, Modifier.weight(1f)) { degree = i; fault = j; override = null }
                    }
                }
            }
            c.degrees.forEachIndexed { i, d -> Hint("정도 '$d': ${c.degreeHints.getOrElse(i) { "" }}") }
            if (editing) Hint("고치기 중에는 칸을 누를 때마다 한 단계씩 가볍게 바뀝니다.")
        }

        Section("3. 가중·감경(단계)") {
            FactorList("가중 사유", t.aggravations, agg, editing, percent = false, onPick = { agg = it }, onChange = { l -> updTarget { it.copy(aggravations = l) } })
            FactorList("감경 사유", t.mitigations, mit, editing, percent = false, onPick = { mit = it }, onChange = { l -> updTarget { it.copy(mitigations = l) } })
            if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("가중 상한(단계)", t.aggravationCapSteps.toString(), Modifier.width(150.dp)) { v -> v.toIntOrNull()?.let { n -> updTarget { it.copy(aggravationCapSteps = n) } } }
                NumberField("감경 상한(단계)", t.mitigationCapSteps.toString(), Modifier.width(150.dp)) { v -> v.toIntOrNull()?.let { n -> updTarget { it.copy(mitigationCapSteps = n) } } }
            } else Hint("가중은 최대 ${t.aggravationCapSteps}단계, 감경은 최대 ${t.mitigationCapSteps}단계까지 적용합니다.")
        }
    }
}

// ───────────────────────────── 입력 부품

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MatrixCell(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.padding(2.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** 가중·감경 사유 고르기(보통) 또는 고치기 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactorList(
    title: String, factors: List<Factor>, picked: Set<String>, editing: Boolean, percent: Boolean,
    onPick: (Set<String>) -> Unit, onChange: (List<Factor>) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (editing) {
        factors.forEach { f ->
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextBox(f.label, Modifier.weight(1f)) { v -> onChange(factors.map { if (it.id == f.id) it.copy(label = v) else it }) }
                if (percent) NumberField("%", f.percent.toString(), Modifier.width(90.dp)) { v -> v.toDoubleOrNull()?.let { n -> onChange(factors.map { if (it.id == f.id) it.copy(percent = n) else it }) } }
                else NumberField("단계", f.steps.toString(), Modifier.width(90.dp)) { v -> v.toIntOrNull()?.let { n -> onChange(factors.map { if (it.id == f.id) it.copy(steps = n) else it }) } }
                IconButton(onClick = { onChange(factors.filter { it.id != f.id }) }) { Icon(Icons.Outlined.Close, "빼기", Modifier.size(16.dp)) }
            }
        }
        TextButton(onClick = { onChange(factors + Factor(label = "새 사유", percent = if (percent) 10.0 else 0.0, steps = 1)) }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text("사유 더하기") }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            factors.forEach { f ->
                val on = f.id in picked
                FilterChip(on, { onPick(if (on) picked - f.id else picked + f.id) }, label = { Text(f.label + "  " + if (percent) Calc.pct(f.percent) else "${f.steps}단계") })
            }
        }
    }
}

/** 원 단위 금액. 쉼표를 넣어 보여 주고, 아래에 억·만 단위로 읽어 준다. */
@Composable
fun MoneyField(label: String, value: Long, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (value > 0) "%,d".format(value) else "") }
    // 바깥에서 값이 바뀌면(한도액으로 등) 따라간다.
    val shown = text.filter(Char::isDigit).toLongOrNull() ?: 0L
    if (shown != value) text = if (value > 0) "%,d".format(value) else ""
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val digits = t.filter(Char::isDigit).take(16)
            val v = digits.toLongOrNull() ?: 0L
            text = if (digits.isEmpty()) "" else "%,d".format(v)
            onChange(v)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        suffix = { Text("원") },
        supportingText = if (value > 0) ({ Text(koreanWon(value), style = MaterialTheme.typography.labelSmall) }) else null,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value.removeSuffix(".0")) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t.filter { it.isDigit() || it == '.' || it == '-' }; onChange(text) },
        label = if (label.isNotBlank()) ({ Text(label, style = MaterialTheme.typography.labelSmall) }) else null,
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun TextBox(value: String, modifier: Modifier, placeholder: String = "", onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}


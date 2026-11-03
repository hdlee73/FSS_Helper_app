package io.github.hdlee73.fsshelper.law

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hdlee73.everylaw.core.domain.CompareTable
import com.hdlee73.everylaw.core.domain.HwpxExporter
import com.hdlee73.everylaw.core.domain.HwpxOptions
import com.hdlee73.everylaw.core.domain.TreeFilter
import com.hdlee73.everylaw.core.domain.withParagraphPieces
import com.hdlee73.everylaw.core.model.Article
import com.hdlee73.everylaw.core.model.DelegationTree
import com.hdlee73.everylaw.core.model.LawLevel
import com.hdlee73.everylaw.core.model.LawNode
import com.hdlee73.everylaw.core.model.formatYmd
import io.github.hdlee73.fsshelper.BuildInfo
import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.SettingsRepo
import io.github.hdlee73.fsshelper.ui.AppCard
import io.github.hdlee73.fsshelper.ui.CardTitle
import io.github.hdlee73.fsshelper.ui.EmptyState
import io.github.hdlee73.fsshelper.ui.ErrorBox
import io.github.hdlee73.fsshelper.ui.InfoBox
import io.github.hdlee73.fsshelper.ui.Os
import io.github.hdlee73.fsshelper.ui.Pill
import io.github.hdlee73.fsshelper.ui.ScreenHeader
import io.github.hdlee73.fsshelper.ui.SearchField
import io.github.hdlee73.fsshelper.ui.Tone
import io.github.hdlee73.fsshelper.ui.parseYmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 조문 인용 표기: 「법령명」 제N조 */
fun citationOf(sourceName: String, article: Article): String =
    "「$sourceName」 " + (article.number?.display ?: article.title)

private val EXAMPLES = listOf("자본시장법 제54조", "금소법 제19조", "지배구조법 제24조", "은행법 제34조", "내부통제", "설명의무")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LawScreen(
    model: LawSearchModel,
    settings: SettingsRepo,
    cache: LawCache,
    onMessage: (String) -> Unit,
    onKeyGuide: () -> Unit = {},
) {
    val query by model.query.collectAsState()
    val asOf by model.asOf.collectAsState()
    val state by model.state.collectAsState()
    val offline by cache.offline.collectAsState()
    val app by settings.state.collectAsState()
    var asOfText by remember(asOf) { mutableStateOf(asOf?.let { formatYmd(it) }.orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "법률 검색",
            subtitle = when {
                model.isSample -> "데모 데이터로 동작 중입니다. 국가법령정보 OC를 받아 넣으면 실제 법령을 조회합니다(오른쪽 'OC 받는 방법')."
                offline -> "www.law.go.kr 연결이 안 돼 저장해 둔 자료를 보여 주고 있습니다."
                else -> "법령명 + 조문을 넣으면 항·호·목마다 위임한 하위 규정을 바로 아래에 펼쳐 보여 줍니다. 단어만 넣으면 키워드 통합 검색입니다."
            },
        ) {
            if (offline) Pill("오프라인 캐시", Tone.WARN)
            if (model.isSample) { Pill("데모", Tone.WARN); TextButton(onClick = onKeyGuide) { Text("OC 받는 방법") } }
        }
        var showAsOf by remember { mutableStateOf(asOf != null) }
        SearchField(query, model::onQueryChange, {
            val ymd = asOfText.takeIf { it.isNotBlank() }?.let(::parseYmd)
            if (ymd != asOf) model.setAsOf(ymd)
            model.submit()
        }, "예: 자본시장법 제54조, 내부통제", Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!showAsOf) TextButton(onClick = { showAsOf = true }) { Text("기준일 지정") }
            if (showAsOf) OutlinedTextField(
                value = asOfText,
                onValueChange = { asOfText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("기준일 (비우면 현행)", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                isError = asOfText.isNotBlank() && parseYmd(asOfText) == null,
            )
            if (!showAsOf) Spacer(Modifier.weight(1f))
            Button(onClick = {
                val ymd = asOfText.takeIf { it.isNotBlank() }?.let(::parseYmd)
                if (ymd != asOf) model.setAsOf(ymd)
                model.submit()
            }) { Text("검색") }
        }
        Spacer(Modifier.height(4.dp))

        when (val s = state) {
            LawUiState.Idle -> IdleView(app.recentLaw, app.favoriteLaw, onPick = { model.submit(it) }, onClearRecent = settings::clearRecentLaw)
            is LawUiState.Loading -> Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(10.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is LawUiState.Error -> Column(Modifier.padding(horizontal = 12.dp)) {
                ErrorBox(s.message) { if (s.canRetry) TextButton(onClick = model::retry) { Text("다시 시도") } }
            }
            is LawUiState.Tree -> TreeView(model, s, app.favoriteLaw, settings, onMessage)
            is LawUiState.Keyword -> KeywordView(model, s, onMessage)
        }
    }

    val versions by model.versions.collectAsState()
    VersionsDialog(versions, onClose = model::closeVersions, onPick = { v ->
        model.closeVersions()
        model.setAsOf(v.effectiveDate)
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleView(recent: List<String>, favorites: List<String>, onPick: (String) -> Unit, onClearRecent: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("예시로 찾아보기")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { EXAMPLES.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }) } }
        }
        if (favorites.isNotEmpty()) AppCard(Modifier.fillMaxWidth()) {
            CardTitle("즐겨찾기")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { favorites.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }, leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(14.dp)) }) } }
        }
        if (recent.isNotEmpty()) AppCard(Modifier.fillMaxWidth()) {
            CardTitle("최근 검색") { TextButton(onClick = onClearRecent) { Text("지우기") } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { recent.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }) } }
        }
        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("검색 방법")
            Text(
                "• 법령명(약칭 가능) + 조문: 자본시장법 제54조, 금소법 19조 2항, 은행법 시행령 제20조의2\n" +
                    "• 키워드: 내부통제, 설명의무 → 조문 제목·본문, 법령, 행정규칙, 판례를 한꺼번에 찾습니다.\n" +
                    "• 법령명 + 키워드: 자본시장법 내부자 → 그 법령 안에서만 찾습니다.\n" +
                    "• 기준일을 넣으면 위반행위 당시 시행 중이던 조문으로 조회합니다(폐지된 구법령 포함).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeView(
    model: LawSearchModel,
    s: LawUiState.Tree,
    favorites: List<String>,
    settings: SettingsRepo,
    onMessage: (String) -> Unit,
) {
    val view by model.view.collectAsState()
    val scope = rememberCoroutineScope()
    val tree = s.tree
    val detailNow by model.detail.collectAsState()
    if (detailNow != LawDetail.None) {
        // 휴대폰: 판례·규정 본문은 화면 전체로 띄우고, 뒤로가기로 닫는다.
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = 8.dp)) { DetailView(detailNow, model::closeDetail, onMessage) }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ── 공통 머리: 법령 이름, 즐겨찾기·연혁, 보기 방식, 저장
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tree.law.name + " " + tree.query.target, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (tree.version?.let { "${it.effectiveDateLabel} 시행 버전" } ?: "현행") + " · 하위 규정 ${tree.root.descendantCount}건" +
                        levelSummary(tree.root).let { if (it.isBlank()) "" else " · $it" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val fav = tree.query.raw in favorites
            IconButton(onClick = { settings.toggleFavoriteLaw(tree.query.raw) }) {
                Icon(if (fav) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (fav) "즐겨찾기 해제" else "즐겨찾기")
            }
            IconButton(onClick = model::openVersions) { Icon(Icons.Outlined.History, "시행 연혁") }
            var more by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, "더 보기") }
                androidx.compose.material3.DropdownMenu(more, { more = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("한글(HWPX)로 저장·공유") }, onClick = {
                        more = false
                        scope.launch {
                            val file = runCatching { exportHwpx(tree) }.getOrElse { onMessage("한글 파일을 만들지 못했어요: ${it.message}"); null }
                            if (file != null) Os.share(file)
                        }
                    })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("전체 복사") }, onClick = { more = false; Os.copy(treeAsText(tree)); onMessage("위임 법령 전체를 복사했어요.") })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LawView.entries.forEach { v -> FilterChip(view == v, { model.setView(v) }, label = { Text(v.label) }) }
        }
        tree.warnings.forEach { InfoBox(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (view) {
                LawView.INSIGHTS -> InsightsView(model, s, onMessage)
                LawView.COMPARE -> CompareView(tree, onMessage)
            }
        }
    }
}

/** "시행령 3 · 시행규칙 1 · 감독규정 2"처럼 단계별 건수 */
private fun levelSummary(root: LawNode): String =
    root.children.flatMap { it.flatten() }.groupingBy { it.level }.eachCount().entries.sortedBy { it.key.ordinal }
        .joinToString(" · ") { "${it.key.shortLabel} ${it.value}" }

// ── 단계 비교: 위임 내용이 있는 단계만 열로 만든다(법률 + 0~4단계 = 1~5단)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareView(tree: DelegationTree, onMessage: (String) -> Unit) {
    val present = remember(tree) { tree.root.children.flatMap { it.flatten() }.map { it.level }.toSet() - LawLevel.LAW }
    var chosen by remember(tree) { mutableStateOf(present) }
    val columns = CompareTable.columns(chosen)
    val rows = remember(tree, chosen) { CompareTable.build(tree.root, chosen) }
    val names = remember(tree) {
        tree.root.flatten().groupBy { it.level }.mapValues { (_, v) -> v.map { it.sourceName }.distinct().joinToString(" · ") }
    }
    val ls = rememberLazyListState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
            Text("${columns.size}단 비교", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp, top = 8.dp))
            if (present.isEmpty()) Text("이 조문에는 위임받은 하위 규정이 없어 법률만 보여 줍니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            LawLevel.entries.filter { it in present }.forEach { lv ->
                FilterChip(lv in chosen, { chosen = if (lv in chosen) chosen - lv else chosen + lv }, label = { Text(lv.label) })
            }
            TextButton(onClick = { Os.copy(compareAsText(columns, rows)); onMessage("비교표를 복사했어요(엑셀에 붙여 넣을 수 있습니다).") }) { Text("비교표 복사") }
        }
        val hs = rememberScrollState()
        val colW = 240.dp
        Row(Modifier.horizontalScroll(hs).width(colW * columns.size).height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))) {
            columns.forEach { lv ->
                Column(Modifier.weight(1f).padding(8.dp)) {
                    Text(lv.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(names[lv].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(hs)) {
            LazyColumn(state = ls, modifier = Modifier.width(colW * columns.size).fillMaxHeight()) {
                items(rows.size) { i ->
                    val row = rows[i]
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        columns.forEachIndexed { ci, lv ->
                            Box(Modifier.weight(1f).fillMaxHeight().background(if (i % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                                .padding(horizontal = 8.dp, vertical = 6.dp)) {
                                SelectionContainer {
                                    if (ci == 0) Text(row.lawText, style = MaterialTheme.typography.bodySmall)
                                    else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.cells[lv].orEmpty().forEach { n ->
                                            Text(listOfNotNull(n.article.fullTitle.ifBlank { n.displayHeading }, n.part).joinToString(" "), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            Text(n.article.body, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            if (ci < columns.size - 1) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (tree.cited.isNotEmpty()) item {
                    SectionLabel("이 조문을 인용한 하위 법규 ${tree.cited.size}건(위임 아님) · '위임 펼쳐 보기' 맨 아래에서 볼 수 있습니다")
                }
            }
        }
    }
}

private fun compareAsText(columns: List<LawLevel>, rows: List<com.hdlee73.everylaw.core.domain.CompareRow>): String = buildString {
    fun cell(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    appendLine(columns.joinToString("\t") { it.label })
    rows.forEach { r ->
        appendLine(columns.mapIndexed { i, lv ->
            if (i == 0) cell(r.lawText) else cell(r.cells[lv].orEmpty().joinToString("\n\n") { n -> n.displayHeading + "\n" + n.article.body })
        }.joinToString("\t"))
    }
}

// ── 목록+본문: 왼쪽 위임 트리, 오른쪽 본문
@Composable
fun DetailView(d: LawDetail, onClose: () -> Unit, onMessage: (String) -> Unit) {
    AppCard(Modifier.fillMaxSize()) {
        when (d) {
            LawDetail.None -> Unit
            is LawDetail.Loading -> {
                CardTitle(d.title) { TextButton(onClick = onClose) { Text("닫기") } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("불러오는 중", style = MaterialTheme.typography.bodySmall)
                }
            }
            is LawDetail.Failed -> {
                CardTitle("불러오지 못했어요") { TextButton(onClick = onClose) { Text("닫기") } }
                ErrorBox(d.message)
            }
            is LawDetail.Document -> DocumentView(d, onClose, onMessage)
            is LawDetail.Precedent -> {
                val p = d.detail
                CardTitle(p.summary.caseName) { TextButton(onClick = onClose) { Text("닫기") } }
                Text(listOfNotNull(p.summary.court, p.summary.caseNumber, p.summary.decisionDate?.let(::formatYmd)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { Os.copy("${p.summary.court.orEmpty()} ${p.summary.decisionDate?.let(::formatYmd).orEmpty()} 선고 ${p.summary.caseNumber.orEmpty()} 판결".trim()); onMessage("판례 인용 표기를 복사했어요.") }) { Text("판례 인용 복사") }
                val sc = rememberScrollState()
                Box(Modifier.weight(1f)) {
                    SelectionContainer {
                        Column(Modifier.verticalScroll(sc).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Block("판시사항", p.holdings); Block("판결요지", p.gist); Block("참조조문", p.referencedArticles); Block("참조판례", p.referencedCases); Block("전문", p.fullText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    if (body.isBlank()) return
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DocumentView(d: LawDetail.Document, onClose: () -> Unit, onMessage: (String) -> Unit) {
    var find by remember(d.title) { mutableStateOf(d.focus.orEmpty()) }
    val shown = if (find.isBlank()) d.articles else d.articles.filter {
        val no = TreeFilter.articleNo(find)
        if (no != null) it.number?.let { n -> n.main == no.main && n.branch == no.branch } == true
        else it.fullTitle.contains(find) || it.content.contains(find)
    }
    CardTitle(d.title) { TextButton(onClick = onClose) { Text("닫기") } }
    if (d.subtitle.isNotBlank()) Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SearchField(find, { find = it }, {}, "조문 찾기 (단어 또는 제12조) · 전체 ${d.articles.size}개 조", Modifier.fillMaxWidth())
    val ls = rememberLazyListState()
    Box(Modifier.weight(1f)) {
        LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { (it.number?.display ?: "") + "|" + it.title + "|" + it.content.hashCode() }) { a ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.fullTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { val c = citationOf(d.title, a); Os.copy(c); onMessage("복사했어요: $c") }) { Text("인용") }
                        TextButton(onClick = { Os.copy(citationOf(d.title, a) + "\n" + a.body); onMessage("조문 원문을 복사했어요.") }) { Text("원문 복사") }
                    }
                    SelectionContainer { Text(a.body, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun KeywordView(model: LawSearchModel, s: LawUiState.Keyword, onMessage: (String) -> Unit) {
    val detail by model.detail.collectAsState()
    val r = s.result
    val ls = rememberLazyListState()
    if (detail != LawDetail.None) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = 8.dp)) { DetailView(detail, model::closeDetail, onMessage) }
        return
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = ls, modifier = Modifier.fillMaxSize()) {
                item {
                    Text("'${r.keyword}' 검색 결과" + (r.scopeLaw?.let { " · ${it.name} 안에서" } ?: ""), style = MaterialTheme.typography.titleSmall)
                    if (r.scanNote.isNotBlank()) Text(r.scanNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.warnings.forEach { Spacer(Modifier.height(4.dp)); InfoBox(it) }
                }
                if (r.articleHits.isEmpty() && r.laws.isEmpty() && r.adminRules.isEmpty() && r.precedents.isEmpty()) {
                    item { EmptyState(Icons.Outlined.ManageSearch, "찾은 결과가 없어요", "다른 단어로 찾거나, 법령명과 조문 번호를 함께 넣어 보세요.") }
                }
                if (r.articleHits.isNotEmpty()) {
                    item { SectionLabel("조문 ${r.articleHits.size}건") }
                    items(r.articleHits, key = { "a:" + it.law.mst + it.article.heading }) { h ->
                        ListLine("${h.law.name} ${h.article.fullTitle}", h.snippet) {
                            val no = h.article.number
                            if (no != null) model.submit("${h.law.name} ${no.display}") else model.openLaw(h.law)
                        }
                    }
                }
                if (r.laws.isNotEmpty()) {
                    item { SectionLabel("법령 ${r.laws.size}건") }
                    items(r.laws, key = { "l:" + it.mst }) { l -> ListLine(l.name, listOfNotNull(l.kind, l.effectiveDate?.let { "시행 " + formatYmd(it) }).joinToString(" · ")) { model.openLaw(l, r.keyword) } }
                }
                if (r.adminRules.isNotEmpty()) {
                    item { SectionLabel("행정규칙 ${r.adminRules.size}건") }
                    items(r.adminRules, key = { "r:" + it.serialNo }) { a -> ListLine(a.name, listOfNotNull(a.kind, a.agency).joinToString(" · ")) { model.openAdminRule(a) } }
                }
                if (r.precedents.isNotEmpty()) {
                    item { SectionLabel("판례 ${r.precedents.size}건") }
                    items(r.precedents, key = { "p:" + it.serialNo }) { p -> ListLine(p.caseName, listOfNotNull(p.court, p.caseNumber, p.decisionDate?.let(::formatYmd)).joinToString(" · ")) { model.openPrecedent(p) } }
                }
            }
        }
    }
}

@Composable
private fun VersionsDialog(state: VersionsState, onClose: () -> Unit, onPick: (com.hdlee73.everylaw.core.model.LawVersion) -> Unit) {
    if (state == VersionsState.Hidden) return
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
        title = { Text(if (state is VersionsState.Shown) "${state.lawName} 시행 연혁" else "시행 연혁") },
        text = {
            when (state) {
                VersionsState.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                is VersionsState.Failed -> Text(state.message)
                is VersionsState.Shown -> LazyColumn(Modifier.height(420.dp).fillMaxWidth()) {
                    items(state.versions, key = { it.mst + it.effectiveDate }) { v ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(v.effectiveDateLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                            Text(listOfNotNull(v.revisionType, v.promulgationDate?.let { "공포 " + formatYmd(it) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            v.status?.let { Pill(it, if (it.contains("현행")) Tone.OK else if (it.contains("예정")) Tone.WARN else Tone.NEUTRAL) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                VersionsState.Hidden -> Unit
            }
        },
    )
}

private fun treeAsText(tree: DelegationTree): String = buildString {
    fun walk(n: LawNode, d: Int) {
        append("  ".repeat(d)).append("[").append(n.level.shortLabel).append("] ").append(n.sourceName).append(" ").append(n.displayHeading).append('\n')
        n.article.body.lines().forEach { append("  ".repeat(d + 1)).append(it).append('\n') }
        n.children.forEach { walk(it, d + 1) }
    }
    walk(tree.root, 0)
}

private suspend fun exportHwpx(tree: DelegationTree): File = withContext(Dispatchers.IO) {
    val safe = (tree.law.name + " " + tree.query.article.display).replace(Regex("""[\\/:*?"<>|]"""), "_")
    val file = File(AppDirs.exports, "$safe.hwpx")
    file.outputStream().use { HwpxExporter.write(tree, HwpxOptions(footer = "FSS_Helper ${BuildInfo.VERSION} · 이현덕 with Claude", heading = "FSS_Helper 법률 검색 결과"), it) }
    file
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
private fun ListLine(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

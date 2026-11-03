package io.github.hdlee73.fsshelper.law

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.everylaw.core.domain.ItemAttachment
import com.hdlee73.everylaw.core.domain.TreeFilter
import com.hdlee73.everylaw.core.model.Article
import com.hdlee73.everylaw.core.model.ArticleItem
import com.hdlee73.everylaw.core.model.DelegationTree
import com.hdlee73.everylaw.core.model.ItemPath
import com.hdlee73.everylaw.core.model.LawLevel
import com.hdlee73.everylaw.core.model.LawNode
import com.hdlee73.everylaw.core.model.formatYmd
import com.hdlee73.everylaw.core.parser.CitationLinker
import io.github.hdlee73.fsshelper.ui.Os
import io.github.hdlee73.fsshelper.ui.SearchField

/*
 * FSS_insights의 법령 보기 방식을 그대로 옮긴 화면:
 *  - 찾은 조문을 흰 카드에 항·호·목 단위로 그리고, 그 항·호·목을 위임 근거로 삼는 하위 규정을 바로 아래에 붙인다(왼쪽 세로선).
 *  - 하위 규정은 단계 번호 동그라미(법률 1 · 시행령 2 · 시행규칙 3 · 감독규정 4 · 시행세칙 5)와 세로선으로 이어 그린다.
 *  - 이 조문을 '인용'만 한 법규는 위임과 섞지 않고 맨 아래 '인용된 법규'에 건수만 보여 주고 눌러야 펼친다.
 */

val LawBodyStyle = TextStyle(fontSize = 14.sp, lineHeight = 24.sp)

/** 계층 하나의 톤: 카드 배경(container), 카드 글자(onContainer), 배지·단계 번호(accent/onAccent) */
@Immutable
data class LevelTone(val container: Color, val onContainer: Color, val accent: Color, val onAccent: Color)

private val LinkBlue = Color(0xFF2F5BD3)
private val DeepNavy = Color(0xFF0B1B3A)
private val GoldAccent = Color(0xFFC8A24A)

private val LightLevels = mapOf(
    LawLevel.LAW to LevelTone(Color(0xFFE7ECF8), DeepNavy, DeepNavy, Color.White),
    LawLevel.DECREE to LevelTone(Color(0xFFE7ECF8), DeepNavy, LinkBlue, Color.White),
    LawLevel.RULE to LevelTone(Color(0xFFE3F4F0), Color(0xFF0B3D35), Color(0xFF0F7B6C), Color.White),
    LawLevel.SUPERVISORY_REGULATION to LevelTone(Color(0xFFF6EEDB), Color(0xFF3F3010), GoldAccent, DeepNavy),
    LawLevel.ENFORCEMENT_DETAIL to LevelTone(Color(0xFFE3E8F1), DeepNavy, Color(0xFF56627A), Color.White),
)

private val DarkLevels = mapOf(
    LawLevel.LAW to LevelTone(Color(0xFF1C2A4B), Color(0xFFE3E8F5), Color(0xFFE3E8F5), DeepNavy),
    LawLevel.DECREE to LevelTone(Color(0xFF1C2A4B), Color(0xFFE3E8F5), Color(0xFF9DB6FF), Color(0xFF0A1F5C)),
    LawLevel.RULE to LevelTone(Color(0xFF123A33), Color(0xFFD3F1EA), Color(0xFF6FD0BF), Color(0xFF00382F)),
    LawLevel.SUPERVISORY_REGULATION to LevelTone(Color(0xFF3A3220), Color(0xFFF6EEDB), GoldAccent, DeepNavy),
    LawLevel.ENFORCEMENT_DETAIL to LevelTone(Color(0xFF243350), Color(0xFFD5DCEC), Color(0xFFA8B3CC), DeepNavy),
)

@Composable
fun levelTone(level: LawLevel): LevelTone {
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    return (if (dark) DarkLevels else LightLevels).getValue(level)
}

/** 트리 노드의 본문 인용이 어느 법령 기준인지: 법률 이름·시행령 이름·시행규칙 이름을 트리에서 찾는다. */
fun citationContext(root: LawNode, node: LawNode): CitationLinker.Context {
    val all = root.flatten()
    val decree = all.firstOrNull { it.level == LawLevel.DECREE }?.sourceName
    val rule = all.firstOrNull { it.level == LawLevel.RULE }?.sourceName
    val own = when (node.level) {
        LawLevel.LAW, LawLevel.DECREE, LawLevel.RULE -> node.sourceName
        else -> null // 감독규정·세칙의 "제2-3조"는 행정규칙 조문이라 연결하지 않는다
    }
    return CitationLinker.Context(root.sourceName, decree, rule, own)
}

/** 조문 본문. "제12조", "법 제11조", 「다른 법」 제N조 같은 인용이 눌러지는 링크가 된다. 찾는 글자는 노랗게 칠한다. */
@Composable
fun CitationText(text: String, ctx: CitationLinker.Context, onCite: (String) -> Unit, highlight: String = "", modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val hlBg = MaterialTheme.colorScheme.tertiaryContainer
    val annotated = remember(text, ctx, linkColor, highlight, hlBg) {
        val cites = CitationLinker.find(text, ctx)
        buildAnnotatedString {
            var pos = 0
            cites.forEach { c ->
                append(text.substring(pos, c.start))
                withLink(LinkAnnotation.Clickable(c.query, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) { onCite(c.query) }) {
                    append(text.substring(c.start, c.end))
                }
                pos = c.end
            }
            append(text.substring(pos))
            val q = highlight.trim()
            if (q.length >= 2) {
                var from = 0
                while (true) {
                    val i = text.indexOf(q, from, ignoreCase = true)
                    if (i < 0) break
                    addStyle(SpanStyle(background = hlBg), i, i + q.length)
                    from = i + q.length
                }
            }
        }
    }
    SelectionContainer { Text(annotated, style = LawBodyStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = modifier) }
}

private const val CITED_ID = "cited:section"

/** 위임 펼쳐 보기(FSS_insights 방식) */
@Composable
fun InsightsView(model: LawSearchModel, s: LawUiState.Tree, onMessage: (String) -> Unit) {
    val filter by model.filter.collectAsState()
    val tree = s.tree
    val root = if (filter.isBlank()) tree.root else TreeFilter.filter(tree.root, filter)
    val expanded = s.expanded
    val onCite: (String) -> Unit = { q -> model.onQueryChange(q); model.submit(q) }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchField(filter, model::setFilter, {}, "결과 안에서 찾기 (단어 또는 제12조)", Modifier.weight(1f))
            TextButton(onClick = { model.expandAll(true) }) { Text("본문 모두 펼치기") }
            TextButton(onClick = { model.expandAll(false) }) { Text("제목만") }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "head") { ArticleHead(root, tree) }
                item(key = "article") {
                    ArticleCard {
                        ArticleBody(root, filter, onCite) { nodes ->
                            nodes.forEachIndexed { i, n -> StepNode(root, n, i == nodes.lastIndex, expanded, filter, model::toggle, onCite, onMessage) }
                        }
                    }
                }
                if (tree.cited.isNotEmpty()) {
                    val open = CITED_ID in expanded
                    item(key = "cited") { CitedHeader(tree.cited, open) { model.toggle(CITED_ID) } }
                    if (open) item(key = "cited-body") {
                        ArticleCard {
                            Column { tree.cited.forEachIndexed { i, n -> StepNode(root, n, i == tree.cited.lastIndex, expanded, filter, model::toggle, onCite, onMessage) } }
                        }
                    }
                }
                item(key = "prec-title") {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Gavel, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("관련 판례", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Text("${tree.precedents.size}건" + tree.precedentBasis.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(tree.precedents, key = { "p:" + it.serialNo + it.caseNumber }) { p ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { model.openPrecedent(p) },
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            p.matchNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            Text(p.caseName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(p.court, p.caseNumber, p.decisionDate?.let(::formatYmd), p.caseType).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** 남색 머리 카드: 법령 이름과 조 제목, 위임·인용 건수 */
@Composable
private fun ArticleHead(root: LawNode, tree: DelegationTree) {
    val tone = levelTone(LawLevel.LAW)
    val delegated = root.children.flatMap { it.flatten() }
    val cited = tree.cited.flatMap { it.flatten() }
    Surface(shape = RoundedCornerShape(14.dp), color = tone.accent, contentColor = tone.onAccent, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(root.sourceName, style = MaterialTheme.typography.labelLarge, color = tone.onAccent.copy(alpha = 0.8f))
            Text(articleTitle(root.article), style = MaterialTheme.typography.titleLarge, color = tone.onAccent)
            Text(
                buildString {
                    append("위임한 하위 규정 ${delegated.size}건")
                    val counts = delegated.groupingBy { it.level }.eachCount().toSortedMap()
                    if (counts.isNotEmpty()) append(" (" + counts.entries.joinToString(" · ") { "${it.key.shortLabel} ${it.value}" } + ")")
                    if (cited.isNotEmpty()) append("  ·  인용한 법규 ${cited.size}건")
                },
                style = MaterialTheme.typography.bodySmall, color = tone.onAccent.copy(alpha = 0.85f),
            )
        }
    }
}

/** 찾는 대상이 특정 항·호여도 조 제목("제112조(의결권 등)")을 맨 앞에 보여 준다. */
private fun articleTitle(article: Article): String = article.parsed.header.ifBlank {
    val t = article.title.trim()
    val shown = if (t.isEmpty() || t.startsWith("(")) t else "($t)"
    article.number?.display.orEmpty() + shown
}

@Composable
private fun ArticleCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/**
 * 조문 본문을 항·호·목 단위로 그리고, 각 위치를 위임 근거로 삼는 하위 규정을 그 줄 바로 아래에 붙인다.
 * 위치를 알 수 없는 하위 규정은 맨 아래 "조 전체"에 둔다.
 */
@Composable
private fun ArticleBody(root: LawNode, highlight: String, onCite: (String) -> Unit, renderNodes: @Composable (List<LawNode>) -> Unit) {
    val ctx = citationContext(root, root)
    val parsed = root.article.parsed
    val attached = remember(root) { ItemAttachment.attach(root.article, root.children) }

    @Composable
    fun Group(path: ItemPath, depth: Int) {
        val nodes = attached[path] ?: return
        InlineDelegation(nodes, "${path.display.ifEmpty { "조 전체" }}에 위임한 하위 규정", depth) { renderNodes(nodes) }
    }

    @Composable
    fun Item(item: ArticleItem, depth: Int) {
        CitationText(item.text, ctx, onCite, highlight, Modifier.padding(start = (depth * 14).dp))
        Group(item.path, depth)
        item.children.forEach { Item(it, depth + 1) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(articleTitle(root.article), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            val cite = citationOf(root.sourceName, root.article)
            IconButton(onClick = { Os.copy(cite + "\n" + root.article.body); }) { Icon(Icons.Outlined.ContentCopy, "원문 복사", Modifier.size(16.dp)) }
        }
        if (parsed.lead.isNotBlank()) CitationText(parsed.lead, ctx, onCite, highlight)
        if (parsed.items.isEmpty() && parsed.lead.isBlank()) CitationText(root.article.content, ctx, onCite, highlight)
        Group(ItemPath.LEAD, 0)
        parsed.items.forEach { Item(it, 0) }
        Group(ItemPath(), 0)
    }
}

/** 위임한 항·호 바로 아래에 펼쳐 보이는 하위 규정. 왼쪽 세로선으로 위 줄에 딸린 내용임을 나타낸다. */
@Composable
private fun InlineDelegation(nodes: List<LawNode>, title: String, depth: Int, content: @Composable () -> Unit) {
    Row(
        Modifier.padding(start = (depth * 14).dp, top = 2.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "↳ $title (${nodes.sumOf { 1 + it.descendantCount }}건)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

/** 위임 단계 번호(법률 1 · 시행령 2 · 시행규칙 3 · 감독규정 4 · 시행세칙 5)를 담은 동그라미 */
@Composable
fun StepDot(level: LawLevel, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val tone = levelTone(level)
    Box(Modifier.size(size).clip(CircleShape).background(tone.accent), contentAlignment = Alignment.Center) {
        Text("${level.ordinal + 1}", color = tone.onAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** 하위 규정 한 건. 왼쪽에 단계 번호와 이어지는 세로선, 오른쪽에 제목·본문. 펼치면 본문과 그 아래 단계들이 이어진다. */
@Composable
private fun StepNode(
    root: LawNode, node: LawNode, isLast: Boolean, expanded: Set<String>, highlight: String,
    onToggle: (String) -> Unit, onCite: (String) -> Unit, onMessage: (String) -> Unit,
) {
    val tone = levelTone(node.level)
    val open = node.id in expanded
    val labelColor = if (node.level == LawLevel.SUPERVISORY_REGULATION) tone.onContainer else tone.accent
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepDot(node.level)
            Box(Modifier.width(2.dp).weight(1f).background(if (isLast) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 24.dp).clickable { onToggle(node.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(node.level.label, style = MaterialTheme.typography.labelMedium, color = labelColor)
                Text(node.displayHeading.ifBlank { node.sourceName }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    maxLines = if (open) 3 else 2, overflow = TextOverflow.Ellipsis)
                if (node.children.isNotEmpty()) Text("하위 ${node.descendantCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val cite = citationOf(node.sourceName, node.article)
                IconButton(onClick = { Os.copy(cite + "\n" + node.article.body); onMessage("조문 원문을 복사했어요.") }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "원문 복사", Modifier.size(14.dp))
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "접기" else "펼치기", Modifier.size(20.dp))
            }
            Text(node.sourceName + (node.issuer?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (node.delegatedBy.isNotEmpty()) {
                Text("위임 근거: " + node.delegatedBy.joinToString(", ") { "${root.sourceName} ${it.heading}" },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                CitationText(node.article.body, citationContext(root, node), onCite, highlight)
                if (node.children.isNotEmpty()) Column(Modifier.padding(top = 8.dp)) {
                    node.children.forEachIndexed { i, c -> StepNode(root, c, i == node.children.lastIndex, expanded, highlight, onToggle, onCite, onMessage) }
                }
            }
        }
    }
}

/** 질의한 조문을 인용만 했을 뿐 위임받지는 않은 하위 법규. 건수만 보여 주고 눌러야 펼친다. */
@Composable
private fun CitedHeader(cited: List<LawNode>, open: Boolean, onToggle: () -> Unit) {
    val counts = cited.flatMap { it.flatten() }.groupingBy { it.level }.eachCount().toSortedMap()
    val total = counts.values.sum()
    val summary = counts.entries.joinToString(" · ") { "${it.key.label} ${it.value}건" }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("인용된 법규 ${total}건", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("이 조문을 인용했지만 위임받은 규정은 아니에요 · $summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (open) "접기" else "보기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

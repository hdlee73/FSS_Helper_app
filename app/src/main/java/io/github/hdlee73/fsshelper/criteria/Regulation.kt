package io.github.hdlee73.fsshelper.criteria

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hdlee73.everylaw.core.domain.LawRepository
import com.hdlee73.everylaw.core.model.AdminRuleDocument
import com.hdlee73.everylaw.core.model.Annex
import com.hdlee73.everylaw.core.model.Article
import io.github.hdlee73.fsshelper.ui.ErrorBox
import io.github.hdlee73.fsshelper.ui.InfoBox
import io.github.hdlee73.fsshelper.ui.Os
import io.github.hdlee73.fsshelper.ui.SearchField
import io.github.hdlee73.fsshelper.ui.ymdLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 원문을 보여 줄 주제: 어떤 조문·별표를 고를지 정한다. */
enum class Topic(val words: List<String>, val annexWords: List<String>, val exclude: List<String> = emptyList()) {
    LEVEL(listOf("제재", "가중", "감면", "감경", "양정", "징계", "면책"), listOf("양정", "제재", "징계"), listOf("과징금", "과태료")),
    PENALTY(listOf("과징금"), listOf("과징금")),
    FINE(listOf("과태료"), listOf("과태료")),
}

/** 「금융기관 검사 및 제재에 관한 규정」과 시행세칙 원문(국가법령정보 행정규칙)을 받아 둔다. */
class RegulationModel(private val repo: LawRepository, private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Loaded(val doc: AdminRuleDocument) : State
        data class Failed(val message: String) : State
    }

    enum class Doc(val label: String, val ruleName: String) {
        RULE("검사·제재규정", "금융기관 검사 및 제재에 관한 규정"),
        DETAIL("시행세칙", "금융기관 검사 및 제재에 관한 규정 시행세칙"),
    }

    private val _states = MutableStateFlow(Doc.entries.associateWith<Doc, State> { State.Idle })
    val states: StateFlow<Map<Doc, State>> = _states.asStateFlow()

    val isSample: Boolean get() = repo.isSample

    fun ensure(force: Boolean = false) {
        Doc.entries.forEach { d ->
            val s = _states.value[d]
            if (!force && (s is State.Loaded || s is State.Loading)) return@forEach
            _states.value = _states.value + (d to State.Loading)
            scope.launch {
                val next = runCatching {
                    val key = squash(d.ruleName)
                    val hits = repo.searchAdminRules(d.ruleName)
                    val hit = hits.firstOrNull { squash(it.name) == key } ?: hits.firstOrNull { squash(it.name).startsWith(key) }
                        ?: error("국가법령정보에서 '${d.ruleName}'을 찾지 못했습니다.")
                    State.Loaded(repo.getAdminRule(hit.serialNo))
                }.getOrElse { State.Failed(it.message ?: "원문을 받아 오지 못했습니다.") }
                _states.value = _states.value + (d to next)
            }
        }
    }

    companion object {
        fun squash(s: String) = s.filter { it.isLetterOrDigit() }

        fun articlesFor(doc: AdminRuleDocument, topic: Topic): List<Article> = doc.articles.filter { a ->
            val head = a.title + " " + a.content.take(60)
            topic.words.any { it in head } && topic.exclude.none { it in a.title }
        }.ifEmpty { doc.articles.filter { a -> topic.words.any { it in a.content } } }

        fun annexesFor(doc: AdminRuleDocument, topic: Topic): List<Annex> =
            doc.annexes.filter { x -> topic.annexWords.any { it in x.title } && topic.exclude.none { it in x.title } }
    }
}

/** 오른쪽 원문 판: 규정·시행세칙 탭, 이 주제의 조문과 별표(펼쳐 보기), 전체 조문 찾기 */
@Composable
fun RegulationPane(model: RegulationModel, topic: Topic, onOpenLaw: (String) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { model.ensure() }
    val states by model.states.collectAsState()
    var doc by remember { mutableStateOf(RegulationModel.Doc.RULE) }
    var all by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("원문", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 4.dp))
            RegulationModel.Doc.entries.forEach { d -> FilterChip(doc == d, { doc = d }, label = { Text(d.label) }) }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            FilterChip(all, { all = !all }, label = { Text("전체 조문") })
        }
        when (val s = states[doc]) {
            is RegulationModel.State.Loaded -> {
                val d = s.doc
                val arts = if (all) d.articles else RegulationModel.articlesFor(d, topic)
                val annexes = if (all) d.annexes else RegulationModel.annexesFor(d, topic)
                val f = find.trim()
                val shownArts = if (f.isBlank()) arts else arts.filter { f in it.content || f in it.title }
                Text(
                    d.name + (d.effectiveDate?.let { " · ${ymdLabel(it)} 시행" } ?: "") + if (model.isSample) " · 데모 데이터" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp),
                )
                SearchField(find, { find = it }, {}, "원문에서 찾기", Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp))
                val listState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, contentPadding = PaddingValues(start = 10.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (annexes.isNotEmpty()) items(annexes, key = { "x:" + it.no + it.title }) { AnnexCard(it) }
                        items(shownArts, key = { "a:" + (it.number?.display ?: "") + it.title + it.content.length }) { ArticleCard(it, f) }
                        if (shownArts.isEmpty() && annexes.isEmpty()) item { InfoBox("이 주제에 맞는 조문을 찾지 못했습니다. '전체 조문'을 눌러 보세요.") }
                    }
                }
            }
            is RegulationModel.State.Failed -> Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ErrorBox(s.message) { TextButton(onClick = { model.ensure(force = true) }) { Text("다시 시도") } }
                InfoBox("원문은 국가법령정보(www.law.go.kr)에서 받아 옵니다. 설정에서 법령 OC를 넣었는지, 이 PC에서 www.law.go.kr에 접속되는지 확인해 주세요. 한 번 받아 오면 연결이 끊겨도 저장본을 보여 줍니다.")
                TextButton(onClick = { onOpenLaw(RegulationModel.Doc.RULE.ruleName) }) { Text("법률 검색에서 찾아보기") }
            }
            else -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("원문을 받아 오는 중…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ArticleCard(a: Article, find: String) {
    var open by remember(a) { mutableStateOf(true) }
    Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text((a.number?.display.orEmpty() + if (a.title.isNotBlank()) "(${a.title})" else "").ifBlank { "조문" },
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            if (open) SelectionContainer { Text(highlightText(a.body, find), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun AnnexCard(x: Annex) {
    var open by remember(x) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("[${x.label}]", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(x.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            if (open) {
                if (x.content.isNotBlank()) SelectionContainer { Text(x.content, style = MaterialTheme.typography.bodySmall) }
                else Text("별표 본문이 글자로 제공되지 않아 파일로만 볼 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    x.pdfUrl?.let { u -> TextButton(onClick = { Os.browse(absolute(u)) }) { Text("PDF로 보기") } }
                    x.fileUrl?.let { u -> TextButton(onClick = { Os.browse(absolute(u)) }) { Text("한글 파일 받기") } }
                }
            }
        }
    }
}

private fun absolute(u: String) = if (u.startsWith("http")) u else "https://www.law.go.kr" + (if (u.startsWith("/")) u else "/$u")

@Composable
private fun highlightText(text: String, q: String): androidx.compose.ui.text.AnnotatedString {
    val bg = MaterialTheme.colorScheme.tertiaryContainer
    return remember(text, q, bg) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            if (q.length >= 2) {
                var from = 0
                while (true) {
                    val i = text.indexOf(q, from)
                    if (i < 0) break
                    addStyle(androidx.compose.ui.text.SpanStyle(background = bg), i, i + q.length)
                    from = i + q.length
                }
            }
        }
    }
}

package io.github.hdlee73.fsshelper.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.ui.EmptyState
import io.github.hdlee73.fsshelper.ui.SearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 매뉴얼·서식 화면 상태(화면을 떠났다 와도 남는다) */
class LibraryUi {
    var manualQuery by mutableStateOf("")
    /** 홈 화면 등에서 열라고 한 매뉴얼 쪽 */
    var manualJump by mutableStateOf<Int?>(null)
    var tocOpen by mutableStateOf(false)
    var formNo by mutableStateOf<String?>(null)
    var formFilter by mutableStateOf("")
    val manualViewer = PdfViewerState()
    val formViewer = PdfViewerState()

    fun backManual(): Boolean = when {
        tocOpen -> { tocOpen = false; true }
        manualQuery.isNotBlank() -> { manualQuery = ""; true }
        else -> false
    }
    fun backForms(): Boolean = when {
        formNo != null -> { formNo = null; true }
        formFilter.isNotBlank() -> { formFilter = ""; true }
        else -> false
    }
}

/** 서식 작성처럼 화면 밖(앱 전체)에서 하는 일 */
class LibraryActions(
    val writeForm: (no: String) -> Unit,
    val openedManual: (title: String, page: Int) -> Unit,
    val openedForm: (no: String) -> Unit,
    val message: (String) -> Unit,
)

/** 자료를 풀고 있거나 없을 때 */
@Composable
private fun LibraryGate(store: LibraryStore, content: @Composable () -> Unit) {
    val status by store.status.collectAsState()
    LaunchedEffect(Unit) { store.ensure() }
    when (val s = status) {
        LibraryStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("매뉴얼·서식을 준비하는 중…", style = MaterialTheme.typography.bodySmall)
            }
        }
        is LibraryStatus.Missing -> EmptyState(Icons.Outlined.Lock, "매뉴얼·서식을 열 수 없습니다", s.message)
        LibraryStatus.Ready -> content()
    }
}

// ───────────────────────────── 검사품질관리 매뉴얼

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(store: LibraryStore, ui: LibraryUi, actions: LibraryActions) = LibraryGate(store) {
    val lib by store.data.collectAsState()
    val doc = rememberPdf(if (lib.hasManual) lib.manualPdf else "", 0)
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(ui.manualQuery, { ui.manualQuery = it }, {}, "매뉴얼에서 찾기", Modifier.weight(1f))
            OutlinedButton(onClick = { ui.tocOpen = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Outlined.FormatListBulleted, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("목차")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            return@Column
        }
        LaunchedEffect(doc, ui.manualJump) {
            ui.manualJump?.let { p -> ui.manualViewer.goTo(p, emptyList()); ui.manualJump = null }
        }
        val q = ui.manualQuery
        if (q.isNotBlank()) {
            val hits by produceState(emptyList<LibraryStore.Hit>(), q) { value = withContext(Dispatchers.IO) { store.search("manual", q) } }
            Text("찾은 쪽 ${hits.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                if (q.length >= 2 && hits.isEmpty()) item { Text("'$q'이(가) 들어 있는 쪽이 없습니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
                items(hits, key = { it.page }) { h ->
                    ListRow("${h.page + 1}쪽 · " + (lib.manualToc.lastOrNull { it.page <= h.page }?.title ?: ""), h.snippet, twoLine = true) {
                        ui.manualJump = h.page
                        ui.manualQuery = ""
                        actions.openedManual(lib.manualToc.lastOrNull { it.page <= h.page }?.title ?: "${h.page + 1}쪽", h.page)
                    }
                }
            }
        } else {
            PdfPages(doc, emptyList(), ui.manualViewer, Modifier.fillMaxSize())
        }
        if (ui.tocOpen) {
            ModalBottomSheet(onDismissRequest = { ui.tocOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
                Text("목차 ${lib.manualToc.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
                LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    if (lib.manualToc.isEmpty()) item { Text("목차를 찾지 못했습니다. 찾기로 쪽을 찾으세요.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp)) }
                    items(lib.manualToc, key = { it.title + it.page }) { t ->
                        ListRow(t.title, "${t.page + 1}쪽") {
                            scope.launch { ui.manualViewer.goTo(t.page, emptyList()) }
                            ui.tocOpen = false
                            actions.openedManual(t.title, t.page)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── 검사관련 서식

@Composable
fun FormsScreen(store: LibraryStore, ui: LibraryUi, actions: LibraryActions) = LibraryGate(store) {
    val lib by store.data.collectAsState()
    val doc = rememberPdf(if (lib.hasForms) lib.formsPdf else "", 0)
    val no = ui.formNo
    if (no != null) {
        val r = FormCatalog.form(no)
        val span = lib.formPages[no]
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.label.trim(), style = MaterialTheme.typography.titleMedium)
                Text(listOf(r.basis, r.target.takeIf { it.isNotBlank() }?.let { "받는 곳 $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "검사관련 서식" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { actions.writeForm(no) }, enabled = lib.canWriteForms) {
                    Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("문서 앱으로 작성")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when {
                doc != null && span != null -> PdfPages(doc, span.pages, ui.formViewer, Modifier.fillMaxSize())
                doc != null -> Text("원본에서 '< 제${no}호 서식 >' 표시를 찾지 못했습니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
        }
        return@LibraryGate
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 6.dp)) {
            SearchField(ui.formFilter, { ui.formFilter = it }, {}, "서식 번호·이름 찾기 (예: 3-8, 문답서)", Modifier.fillMaxWidth())
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val f = LibraryStore.squash(ui.formFilter)
        val forms = remember(lib.formNos, f) {
            lib.formNos.map { FormCatalog.form(it) }.filter { r -> f.isEmpty() || LibraryStore.squash(r.no + r.name + r.basis).contains(f) || LibraryStore.squash("제${r.no}호").contains(f) }
        }
        if (forms.isEmpty()) {
            EmptyState(Icons.Outlined.Description, "맞는 서식이 없습니다", "번호(예: 3-8)나 이름 일부로 찾아 보세요.")
            return@Column
        }
        Text("서식 ${forms.size}종 · 누르면 원본 모양을 봅니다", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            var lastGroup = ""
            forms.forEach { r ->
                val g = r.group.ifBlank { "제${r.no.substringBefore('-')}장" }
                if (g != lastGroup) {
                    lastGroup = g
                    item(key = "g:$g:${r.no}") { GroupHeader(g, forms.count { (it.group.ifBlank { "제${it.no.substringBefore('-')}장" }) == g }) }
                }
                item(key = r.no) { FormRow(r) { ui.formNo = r.no; actions.openedForm(r.no) } }
            }
        }
    }
}

// ───────────────────────────── 공통

/** 서식 묶음 제목(상시감시·검사준비·현장검사 등): 크고 굵게, 왼쪽 남색 띠 */
@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ListRow(title: String, sub: String, twoLine: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (twoLine) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FormRow(r: FormRef, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(r.no, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name.ifBlank { "제${r.no}호 서식" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.basis.isNotBlank()) Text(r.basis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

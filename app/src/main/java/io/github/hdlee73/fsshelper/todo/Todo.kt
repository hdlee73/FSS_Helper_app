package io.github.hdlee73.fsshelper.todo

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.JsonFile
import io.github.hdlee73.fsshelper.ui.EmptyState
import io.github.hdlee73.fsshelper.ui.LocalStatus
import io.github.hdlee73.fsshelper.ui.ScreenHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.util.UUID

@Composable
fun TodoScreen(store: TodoStore) {
    val data by store.data.collectAsState()
    var text by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    val today = LocalDate.now()
    val items = data.items
        .filter { when (filter) { 0 -> !it.done; 1 -> it.done; else -> true } }
        .sortedWith(compareBy<Todo>({ it.done }, { !it.important }, { it.dueDate() ?: LocalDate.MAX }, { -it.createdAt }))
    fun submit() {
        val d = parseDue(due) ?: ""
        store.add(text, d); text = ""; due = ""
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("할 일", "해야 할 일을 적어 두고 끝나면 체크하세요(이 기기에만 저장).") {
            if (data.items.any { it.done }) TextButton(onClick = store::clearDone) { Text("끝낸 일 지우기") }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { submit(); true } else false },
                placeholder = { Text("할 일을 적고 완료") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(due, { due = it }, Modifier.weight(1f), placeholder = { Text("기한 예: 11/20") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium,
                    isError = due.isNotBlank() && parseDue(due) == null)
                Button(onClick = ::submit, enabled = text.isNotBlank()) { Text("추가") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("할 일 ${data.items.count { !it.done }}", "끝냄 ${data.items.count { it.done }}", "전체").forEachIndexed { i, l -> FilterChip(filter == i, { filter = i }, label = { Text(l) }) }
            }
            if (items.isEmpty()) {
                EmptyState(Icons.Outlined.Checklist, if (filter == 1) "끝낸 일이 없습니다" else "할 일이 없습니다", "위 칸에 적고 '추가'를 누르세요. 기한은 '11/20', '내일'처럼 적을 수 있습니다.")
            } else {
                val listState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp, end = 12.dp)) {
                        items(items, key = { it.id }) { t -> TodoRow(t, today, store) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(t: Todo, today: LocalDate, store: TodoStore) {
    val status = LocalStatus.current
    var editing by remember(t.id) { mutableStateOf(false) }
    var draft by remember(t.id, t.text) { mutableStateOf(t.text) }
    Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(t.done, { store.toggle(t.id) })
            if (editing) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { store.edit(t.id, draft); editing = false; true } else false
                }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    t.text,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
            }
            t.dueDate()?.let { d ->
                val late = !t.done && d.isBefore(today)
                val soon = !t.done && !late && !d.isAfter(today.plusDays(2))
                Text(
                    (if (d == today) "오늘" else "${d.monthValue}/${d.dayOfMonth}") + if (late) " 지남" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = when { late -> status.bad; soon -> status.warn; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            IconButton(onClick = { store.star(t.id) }) {
                Icon(if (t.important) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (t.important) "중요 해제" else "중요", Modifier.size(18.dp),
                    tint = if (t.important) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            }
            TextButton(onClick = { if (editing) store.edit(t.id, draft); editing = !editing }) { Text(if (editing) "저장" else "고치기") }
            IconButton(onClick = { store.delete(t.id) }) { Icon(Icons.Outlined.Close, "지우기", Modifier.size(16.dp)) }
        }
    }
}

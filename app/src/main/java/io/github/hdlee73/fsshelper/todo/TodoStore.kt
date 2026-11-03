package io.github.hdlee73.fsshelper.todo

import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.JsonFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.util.UUID

@Serializable
data class Todo(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val text: String,
    val done: Boolean = false,
    /** yyyy-MM-dd, 비어 있으면 기한 없음 */
    val due: String = "",
    val important: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long = 0,
) {
    fun dueDate(): LocalDate? = runCatching { if (due.isBlank()) null else LocalDate.parse(due) }.getOrNull()
}

@Serializable
data class TodoData(val items: List<Todo> = emptyList())

class TodoStore(file: File = File(AppDirs.root, "todo.json")) {
    private val store = JsonFile(file, TodoData.serializer()) { TodoData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<TodoData> = _data.asStateFlow()

    private fun save(block: (TodoData) -> TodoData) {
        _data.update(block)
        runCatching { store.write(_data.value) }
    }

    fun add(text: String, due: String = "") { if (text.isNotBlank()) save { it.copy(items = listOf(Todo(text = text.trim(), due = due)) + it.items) } }
    fun toggle(id: String) = save { d -> d.copy(items = d.items.map { if (it.id == id) it.copy(done = !it.done, doneAt = if (!it.done) System.currentTimeMillis() else 0) else it }) }
    fun star(id: String) = save { d -> d.copy(items = d.items.map { if (it.id == id) it.copy(important = !it.important) else it }) }
    fun edit(id: String, text: String) = save { d -> d.copy(items = d.items.map { if (it.id == id) it.copy(text = text) else it }) }
    fun setDue(id: String, due: String) = save { d -> d.copy(items = d.items.map { if (it.id == id) it.copy(due = due) else it }) }
    fun delete(id: String) = save { d -> d.copy(items = d.items.filter { it.id != id }) }
    fun clearDone() = save { d -> d.copy(items = d.items.filter { !it.done }) }

    val openCount: Int get() = _data.value.items.count { !it.done }
}

/** "11/20", "2026-11-20", "20261120", "내일" → yyyy-MM-dd */
fun parseDue(text: String, today: LocalDate = LocalDate.now()): String? {
    val t = text.trim()
    if (t.isEmpty()) return ""
    if (t == "오늘") return today.toString()
    if (t == "내일") return today.plusDays(1).toString()
    val d = t.filter(Char::isDigit)
    return runCatching {
        when (d.length) {
            8 -> LocalDate.of(d.take(4).toInt(), d.substring(4, 6).toInt(), d.takeLast(2).toInt())
            3, 4 -> { val parts = t.split('/', '.', '-').filter { it.isNotBlank() }; LocalDate.of(today.year, parts[0].toInt(), parts[1].toInt()) }
            else -> null
        }?.toString()
    }.getOrNull()
}


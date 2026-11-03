package io.github.hdlee73.fsshelper.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import io.github.hdlee73.fsshelper.ui.NavyBand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.data.Recent
import io.github.hdlee73.fsshelper.data.RecentKind
import io.github.hdlee73.fsshelper.data.RecentStore
import io.github.hdlee73.fsshelper.ui.Dest
import io.github.hdlee73.fsshelper.ui.SearchField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 홈 카드에 붙일 작은 표시(예: 할 일 3) */
data class HomeBadges(val counts: Map<Dest, String> = emptyMap())

/**
 * 홈: 법령 검색 한 칸과 최근 사용 목록만 둔다(메뉴는 위쪽 탭).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(recents: RecentStore, badges: HomeBadges, onTool: (Dest) -> Unit, onRecent: (Recent) -> Unit, onSearch: (String) -> Unit) {
    val data by recents.data.collectAsState()
    var tab by remember { mutableStateOf<RecentKind?>(null) }
    var starredOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("무엇을 찾으세요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                SearchField(query, { query = it }, { if (query.isNotBlank()) onSearch(query.trim()) }, "법령 검색 (예: 자본시장법 제54조)", Modifier.fillMaxWidth())
                Text("위쪽 탭을 옆으로 밀면 모든 메뉴가 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("최근 사용", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = listOf("전체") + RecentKind.entries.map { it.label } + "★ 즐겨찾기"
                val sel = when { starredOnly -> RecentKind.entries.size + 1; tab == null -> 0; else -> RecentKind.entries.indexOf(tab) + 1 }
                labels.forEachIndexed { i, l ->
                    FilterChip(selected = i == sel, onClick = {
                        starredOnly = i == RecentKind.entries.size + 1
                        tab = if (i in 1..RecentKind.entries.size) RecentKind.entries[i - 1] else null
                    }, label = { Text(l) }, border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ))
                }
            }
        }
        val items = data.items
            .filter { tab == null || it.type == tab }
            .filter { !starredOnly || it.starred }
        item {
            Surface(
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    if (items.isEmpty()) {
                        Text(
                            if (data.items.isEmpty()) "아직 사용한 것이 없습니다. 서식·매뉴얼·법령·기업을 열면 여기에 쌓입니다." else "조건에 맞는 항목이 없습니다.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                        )
                    }
                    items.take(40).forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        RecentRow(r, recents, onRecent)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("최근 ${data.items.size}개 · 즐겨찾기 ${data.items.count { it.starred }}개", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (data.items.any { !it.starred }) TextButton(onClick = recents::clear) { Text("기록 지우기") }
            }
        }
    }
}

/** 옅은 남색 바탕의 도구 그림(한 가지 색으로 차분하게) */
@Composable
fun ToolGlyph(icon: ImageVector, @Suppress("UNUSED_PARAMETER") tint: Color, size: Int = 30) {
    val c = MaterialTheme.colorScheme.primary
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c, modifier = Modifier.size((size * 0.56f).dp))
    }
}

private val TIME = DateTimeFormatter.ofPattern("a h:mm")
private val DATE = DateTimeFormatter.ofPattern("M월 d일")

private fun whenLabel(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val d = t.toLocalDate()
    val today = LocalDate.now()
    val day = when (d) { today -> "오늘"; today.minusDays(1) -> "어제"; else -> d.format(DATE) }
    return "$day · " + t.format(TIME).replace("AM", "오전").replace("PM", "오후")
}

private fun kindDest(k: RecentKind): Dest = when (k) {
    RecentKind.FORM, RecentKind.FILE -> Dest.FORMS
    RecentKind.MANUAL -> Dest.MANUAL
    RecentKind.LAW -> Dest.LAW
    RecentKind.CORP -> Dest.DART
}

@Composable
private fun RecentRow(r: Recent, recents: RecentStore, onOpen: (Recent) -> Unit) {
    val d = kindDest(r.type)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolGlyph(d.icon, d.tint, 26)
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.type.label} · ${r.where.ifBlank { d.label }} · ${whenLabel(r.at)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { recents.toggleStar(r.key) }, modifier = Modifier.size(36.dp)) {
            Icon(if (r.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (r.starred) "즐겨찾기 해제" else "즐겨찾기", Modifier.size(18.dp),
                tint = if (r.starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = { recents.remove(r.key) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Close, "목록에서 빼기", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

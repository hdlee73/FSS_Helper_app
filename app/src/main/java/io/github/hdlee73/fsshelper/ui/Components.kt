package io.github.hdlee73.fsshelper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.hdlee73.fsshelper.Platform
import java.io.File

/** 흰 카드. 테두리 한 줄로 구분하고 그림자는 쓰지 않는다. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

enum class Tone { OK, WARN, BAD, ACCENT, NEUTRAL }

@Composable
fun Pill(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val s = LocalStatus.current
    val (fg, bg) = when (tone) {
        Tone.OK -> s.ok to s.okBg
        Tone.WARN -> s.warn to s.warnBg
        Tone.BAD -> s.bad to s.badBg
        Tone.ACCENT -> MaterialTheme.colorScheme.onPrimaryContainer to MaterialTheme.colorScheme.primaryContainer
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 데이터가 없을 때 무엇이 나타날지와 다음 행동을 알려 주는 안내 */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** 검색창. Enter로 [onSearch], Esc로 지운다. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    // 입력 중인 글은 화면 안에 두고 밖으로는 알리기만 한다(한글 조합이 끊기지 않게). 밖에서 값을 바꾸면 따라간다.
    var local by remember { mutableStateOf(value) }
    val sent = remember { ArrayDeque<String>() }
    // 늦게 도착한 '내가 보낸 값'은 무시하고, 밖에서 새로 정한 값(뒤로가기 등)만 받아들인다.
    LaunchedEffect(value) { if (value != local && value !in sent) local = value }
    val set: (String) -> Unit = { local = it; sent.addLast(it); if (sent.size > 64) sent.removeFirst(); onValueChange(it) }
    OutlinedTextField(
        value = local,
        onValueChange = set,
        modifier = modifier.onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { sent.clear(); onSearch(); true }
            else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && local.isNotEmpty()) { set(""); true }
            else false
        },
        singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (local.isNotEmpty()) IconButton(onClick = { set("") }) { Icon(Icons.Outlined.Close, "지우기", modifier = Modifier.size(16.dp)) }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { sent.clear(); onSearch() }),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/**
 * 화면 맨 위 줄. 휴대폰에서는 제목이 위쪽 앱 막대에 이미 있으므로 설명과 버튼만 보여 준다.
 * 버튼이 많으면 다음 줄로 넘어간다.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable (() -> Unit)? = null) {
    if (subtitle == null && actions == null) return
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp))
        actions?.invoke()
    }
}

@Composable
fun LabelValue(label: String, value: String, labelWidth: Int = 96) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(labelWidth.dp))
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val s = LocalStatus.current
    Surface(modifier.fillMaxWidth(), color = s.badBg, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = s.bad, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

@Composable
fun InfoBox(message: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(modifier.fillMaxWidth(), color = color, shape = MaterialTheme.shapes.medium) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
    }
}

object Os {
    private val ctx get() = Platform.app

    fun copy(text: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("FSS_Helper", text))
        }
    }

    fun browse(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun uriOf(file: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)

    fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "hwp" -> "application/x-hwp"
        "hwpx" -> "application/hwp+zip"
        "apk" -> "application/vnd.android.package-archive"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    /** 한글 문서를 받을 수 있는 앱마다 쓰는 MIME이 달라서(한컴오피스, 이음오피스, 폴라리스 등) 여러 개를 함께 찾는다. */
    private val HWP_MIMES = listOf(
        "application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp",
        "application/hwp", "application/hwp+zip", "application/vnd.hancom.hwpx", "application/haansofthwpx",
    )

    private fun candidateMimes(file: File): List<String> = when (file.extension.lowercase()) {
        "hwp", "hwpx" -> listOf(mimeOf(file)) + HWP_MIMES + "application/octet-stream"
        else -> listOf(mimeOf(file))
    }.distinct()

    /** 이 파일을 열 수 있는 앱 수(설치된 문서 앱 확인용) */
    fun viewerCount(file: File): Int = viewIntents(file).size

    private fun viewIntents(file: File): List<Intent> {
        val pm = ctx.packageManager
        val uri = uriOf(file)
        val seen = LinkedHashMap<String, Intent>()
        for (mime in candidateMimes(file)) {
            val base = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            @Suppress("DEPRECATION")
            val acts = runCatching { pm.queryIntentActivities(base, 0) }.getOrDefault(emptyList())
            for (ri in acts) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || pkg in seen) continue
                seen[pkg] = Intent(base).setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return seen.values.toList()
    }

    /**
     * 파일을 다른 앱으로 연다. 한글(HWP/HWPX)은 한컴오피스·이음오피스·폴라리스 오피스 등
     * 설치된 호환 앱을 모두 찾아 고르게 한다. 열 앱이 없으면 false.
     */
    fun open(file: File): Boolean = runCatching {
        val targets = viewIntents(file)
        if (targets.isEmpty()) return@runCatching false
        val intent = if (targets.size == 1) targets[0]
        else Intent.createChooser(targets[0], file.name)
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 파일을 메일·메신저·드라이브 등으로 보낸다. */
    fun share(file: File) {
        runCatching {
            val i = Intent(Intent.ACTION_SEND).setType(mimeOf(file)).putExtra(Intent.EXTRA_STREAM, uriOf(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    val archLabel: String get() = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
}

/** yyyyMMdd → yyyy.MM.dd */
fun ymdLabel(ymd: String?): String =
    if (ymd != null && ymd.length == 8 && ymd.all(Char::isDigit)) "${ymd.substring(0, 4)}.${ymd.substring(4, 6)}.${ymd.substring(6)}" else ymd.orEmpty()

/** "2026-11-03", "2026.11.03", "20261103" → "20261103". 알아볼 수 없으면 null */
fun parseYmd(text: String): String? {
    val digits = text.filter(Char::isDigit)
    if (digits.length != 8) return null
    return runCatching { java.time.LocalDate.parse(digits, java.time.format.DateTimeFormatter.BASIC_ISO_DATE); digits }.getOrNull()
}

/** 파일 고르기 창(시스템 문서 선택기). 고른 파일을 앱 캐시에 복사해 넘긴다. */
fun pickFile(mimes: Array<String> = arrayOf("*/*"), onPicked: (File) -> Unit) {
    Platform.activity?.pickFile(mimes, onPicked)
}

/** 저장 위치를 골라 [source]를 복사한다(시스템 문서 저장 창). */
fun saveFileAs(source: File, suggestedName: String = source.name, onDone: (Boolean) -> Unit = {}) {
    Platform.activity?.saveFileAs(source, suggestedName, onDone) ?: onDone(false)
}

package io.github.hdlee73.fsshelper.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.dart.DartModel
import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.SettingsRepo
import io.github.hdlee73.fsshelper.data.ThemeMode
import io.github.hdlee73.fsshelper.law.LawModule
import io.github.hdlee73.fsshelper.ui.AppCard
import io.github.hdlee73.fsshelper.ui.CardTitle
import io.github.hdlee73.fsshelper.ui.InfoBox
import io.github.hdlee73.fsshelper.ui.Os
import io.github.hdlee73.fsshelper.ui.Pill
import io.github.hdlee73.fsshelper.ui.ScreenHeader
import io.github.hdlee73.fsshelper.ui.Tone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 미리 받기 진행 상태(화면을 떠나도 유지) */
class PrefetchUi {
    var running by mutableStateOf(false)
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var current by mutableStateOf("")
    var result by mutableStateOf<String?>(null)
}

@Composable
fun SettingsScreen(settings: SettingsRepo, law: LawModule, dart: DartModel, prefetch: PrefetchUi, appScope: kotlinx.coroutines.CoroutineScope, onMessage: (String) -> Unit) {
    val s by settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("설정", "법령 OC·DART 인증키는 앱에 기본으로 들어 있습니다. 직접 넣은 키는 이 기기의 보안 저장소로 암호화합니다.")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("국가법령정보 OC") { Pill(when { settings.lawOcBuiltIn -> "기본 키"; settings.lawOc.isNotBlank() -> "내 키"; else -> "데모 데이터" }, if (settings.lawOc.isNotBlank()) Tone.OK else Tone.WARN) }
                KeyGuideBody(KeyKind.LAW, settings.userLawOc, onSave = { settings.setLawOc(it); onMessage("법령 OC를 저장했습니다.") }, onTest = { testLaw(law) }, builtIn = settings.lawOcBuiltIn)
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("OpenDART 인증키") { Pill(when { settings.dartKeyBuiltIn -> "기본 키"; dart.hasKey -> "내 키"; else -> "키 없음" }, if (dart.hasKey) Tone.OK else Tone.WARN) }
                KeyGuideBody(KeyKind.DART, settings.userDartKey, onSave = { settings.setDartKey(it); onMessage("DART 인증키를 저장했습니다. 회사 목록을 받아 옵니다."); dart.ensureIndex(force = true) }, onTest = { testDart(dart) }, builtIn = settings.dartKeyBuiltIn)
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("법령 저장본 (오프라인 대비)")
                val (files, bytes) = remember(prefetch.running, prefetch.result) { law.cache.stats() }
                Text("한 번 본 법령 본문은 기기에 저장해 두고, 연결이 끊기면 저장본으로 보여 줍니다. 지금 ${files}건 · ${"%.1f".format(bytes / 1024.0 / 1024.0)}MB",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (prefetch.running) {
                    LinearProgressIndicator(progress = { if (prefetch.total == 0) 0f else prefetch.done.toFloat() / prefetch.total }, modifier = Modifier.fillMaxWidth())
                    Text("${prefetch.done}/${prefetch.total} ${prefetch.current}", style = MaterialTheme.typography.bodySmall)
                }
                prefetch.result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !prefetch.running && settings.lawOc.isNotBlank(), onClick = {
                        prefetch.running = true; prefetch.result = null
                        appScope.launch {
                            try {
                                val failed = law.prefetchMajor { d, t, n -> prefetch.done = d; prefetch.total = t; prefetch.current = n }
                                prefetch.result = if (failed.isEmpty()) "주요 금융법령을 모두 받았습니다." else "받지 못한 법령 ${failed.size}건: ${failed.take(5).joinToString(", ")}"
                            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                prefetch.result = "중단됨: ${e.message}"
                            } finally { prefetch.running = false }
                        }
                    }) { Text("주요 금융법령 미리 받기") }
                    OutlinedButton(enabled = !prefetch.running, onClick = { law.cache.clear(); prefetch.result = "저장본을 비웠습니다." }) { Text("저장본 비우기") }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("화면")
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ThemeMode.SYSTEM to "시스템 따라가기", ThemeMode.LIGHT to "밝게", ThemeMode.DARK to "어둡게").forEach { (m, label) ->
                        FilterChip(s.theme == m, { settings.update { it.copy(theme = m) } }, label = { Text(label) })
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("업데이트")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("자동으로 새 버전 확인", style = MaterialTheme.typography.bodyMedium)
                        Text("앱을 켤 때 하루 한 번 확인하고, 새 버전이 있으면 위쪽 막대와 앱 정보에 알려 줍니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.autoUpdate, { v -> settings.update { it.copy(autoUpdate = v) } })
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("내 자료")
                Text("할 일·기준·최근 사용·법령 저장본은 이 기기의 앱 전용 공간에만 저장되고 외부로 보내지 않습니다. 앱을 지우면 함께 지워집니다. 내보낸 한글·PDF 파일은 저장할 때 공유 창으로 원하는 곳에 보낼 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

suspend fun testLaw(law: LawModule): String =
    runCatching { law.repository.searchLaws("은행법") }.fold(
        { if (it.isNotEmpty()) "연결됐습니다: '은행법' ${it.size}건" else "응답은 왔지만 결과가 없습니다. OC와 신청한 IP를 확인하세요." },
        { "연결 실패: ${it.message} (내부망이면 www.law.go.kr 접속이 허용되는지 확인하세요)" },
    )

suspend fun testDart(dart: DartModel): String =
    runCatching { dart.client.company("00126380") }.fold({ "연결됐습니다: ${it.name}" }, { "연결 실패: ${it.message}" })


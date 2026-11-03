package io.github.hdlee73.fsshelper.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.data.SettingsRepo
import io.github.hdlee73.fsshelper.ui.InfoBox
import io.github.hdlee73.fsshelper.ui.Os
import kotlinx.coroutines.launch

/** 인증키 종류별 발급 안내 */
enum class KeyKind(val id: String, val title: String, val field: String, val signUp: String, val steps: List<String>, val note: String) {
    LAW(
        "law", "국가법령정보 OC", "OC (가입 이메일의 @ 앞부분)", "https://open.law.go.kr/LSO/openApi/guideList.do",
        listOf(
            "국가법령정보 공동활용(open.law.go.kr)에 접속해 회원가입·로그인합니다(인터넷 PC에서).",
            "[OPEN API] → [OPEN API 신청]에서 '법령', '행정규칙', '판례' 목록·본문 조회를 신청합니다. 서버 IP·도메인 칸은 신청 화면 안내에 따라 적습니다.",
            "승인 메일이 오면(보통 1~2일) OC 값은 가입한 이메일의 @ 앞부분입니다. 예: hong@korea.kr → hong",
            "아래 칸에 OC를 넣고 '저장'한 뒤 '연결 확인'을 누르세요.",
        ),
        "OC가 없으면 법률 검색은 데모 데이터(일부 조문)로만 동작합니다.",
    ),
    DART(
        "dart", "OpenDART 인증키", "인증키 (40자)", "https://opendart.fss.or.kr/uss/umt/EgovMberInsertView.do",
        listOf(
            "전자공시 OpenDART(opendart.fss.or.kr)에 접속해 [인증키 신청/관리] → [인증키 신청]을 누릅니다.",
            "이메일·사용 용도를 넣고 이메일 인증을 마치면 개인용 인증키(영문·숫자 40자)가 바로 발급됩니다.",
            "[인증키 신청/관리] → [오픈API 이용현황]에서 키를 복사합니다(개인 키는 하루 2만 건까지).",
            "아래 칸에 붙여 넣고 '저장'하면 회사 목록(약 1~2분)을 받아 옵니다.",
        ),
        "인증키가 없으면 기업 개황·공시를 조회할 수 없습니다.",
    ),
}

/** 발급 방법 + 입력 칸. 설정 화면과 안내 창에서 함께 쓴다. */
@Composable
fun KeyGuideBody(kind: KeyKind, current: String, onSave: (String) -> Unit, onTest: suspend () -> String, builtIn: Boolean = false) {
    var text by remember(current) { mutableStateOf(current) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        kind.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { Os.browse(kind.signUp) }) { Text("신청 페이지 열기") }
        if (builtIn) Text("지금은 앱에 내장된 기본 키를 씁니다. 내 키를 넣으면 그 키를 먼저 씁니다(비우고 저장하면 기본 키로 돌아감).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(text, { text = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(kind.field) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(text); result = "저장했습니다." }, enabled = text != current) { Text("저장") }
            OutlinedButton(onClick = { scope.launch { result = "확인 중…"; result = onTest() } }, enabled = current.isNotBlank() || builtIn) { Text("연결 확인") }
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
    }
}

/** 키 없이 법률 검색·기업 검색을 처음 열 때 띄우는 안내 창 */
@Composable
fun KeyGuideDialog(kind: KeyKind, settings: SettingsRepo, current: String, onSave: (String) -> Unit, onTest: suspend () -> String, onDismiss: () -> Unit) {
    var hide by remember { mutableStateOf(false) }
    fun close() {
        if (hide) settings.update { it.copy(keyGuideHidden = it.keyGuideHidden + kind.id) }
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text("${kind.title}가 필요합니다") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kind.note, style = MaterialTheme.typography.bodyMedium)
                KeyGuideBody(kind, current, onSave, onTest)
                InfoBox("직접 넣은 키는 이 기기의 보안 저장소(Android Keystore)로 암호화해 앱 안에만 저장합니다.")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hide = !hide }.padding(end = 8.dp)) {
                    Checkbox(hide, { hide = it })
                    Text("다시 보지 않기", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = ::close) { Text(if (current.isBlank()) "나중에" else "닫기") } },
    )
}

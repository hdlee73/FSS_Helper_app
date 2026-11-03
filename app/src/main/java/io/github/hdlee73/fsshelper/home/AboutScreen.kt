package io.github.hdlee73.fsshelper.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.github.hdlee73.fsshelper.R
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.BuildInfo
import io.github.hdlee73.fsshelper.update.UpdateModel
import io.github.hdlee73.fsshelper.update.UpdateState
import io.github.hdlee73.fsshelper.ui.AppCard
import io.github.hdlee73.fsshelper.ui.CardTitle
import io.github.hdlee73.fsshelper.ui.InfoBox
import io.github.hdlee73.fsshelper.ui.LabelValue
import io.github.hdlee73.fsshelper.ui.Os
import io.github.hdlee73.fsshelper.ui.Pill
import io.github.hdlee73.fsshelper.ui.ScreenHeader
import io.github.hdlee73.fsshelper.ui.Tone

const val APP_NAME = "FSS_Helper"
const val AUTHOR = "이현덕 with Claude"
const val AUTHOR_EMAIL = "hdlee73@gmail.com"
const val AUTHOR_HOME = "https://hdlee73.github.io/Home/"

private val GUIDE = listOf(
    "메뉴와 뒤로가기" to "왼쪽 위 ☰ 또는 화면 왼쪽 끝에서 밀면 메뉴가 열립니다. 뒤로가기 버튼·제스처는 앱을 끄지 않고 직전 화면(또는 화면 안의 이전 단계)으로 돌아갑니다. 홈에서 두 번 누르면 앱을 닫습니다.",
    "홈" to "남색 머리띠의 검색창에 '은행법 제34조'처럼 적으면 바로 법률 검색으로 갑니다. 아래 카드로 도구를 열고, 최근 사용 목록에서 다시 엽니다. ☆을 누르면 즐겨찾기에 남습니다.",
    "검사품질관리 매뉴얼" to "검사품질관리 매뉴얼이 앱 안에 들어 있습니다(암호화 보관). 위쪽 '목차'로 절을 고르거나 찾기 칸에 낱말을 넣어 쪽을 찾습니다. 두 손가락으로 벌리면 확대됩니다.",
    "검사관련 서식" to "서식 번호(예: 3-8)나 이름으로 찾아 원본 모양을 봅니다. '문서 앱으로 작성'을 누르면 서식 원본을 설치된 한글 호환 앱(한컴오피스, 이음오피스, 폴라리스 오피스 등)에서 골라 열어 그대로 작성할 수 있습니다.",
    "법률 검색" to "'자본시장법 제54조'처럼 찾으면 조문을 항·호·목 단위로 보여 주고, 각 항·호·목을 위임 근거로 삼은 시행령·시행규칙·감독규정·세칙을 바로 아래에 붙여 보여 줍니다. 위임이 아니라 인용만 한 법규는 '인용된 법규'에 따로 모읍니다. 기준일을 넣으면 그날 시행 조문을 봅니다.",
    "기업 개황" to "회사 이름이나 종목코드로 찾아 기업개황·최대주주·재무·공시를 보고, 한글(HWPX)·PDF로 저장해 공유합니다. ☆로 관심기업에 넣으면 새 공시 건수를 알려 줍니다.",
    "양정기준" to "임원·직원·기관을 고르고 위반 정도와 고의·과실로 기본 양정을 정한 뒤 가중·감경 단계를 더해 결과와 사전통지 표기를 봅니다. 검사·제재규정과 시행세칙 원문도 함께 봅니다.",
    "과징금·과태료 산정기준" to "법정한도와 세부평가(또는 위반결과×동기)를 고르면 기본금액, 가중·감경, 상한까지 단계별로 계산합니다. 기준 숫자는 '기준 고치기'로 현행 규정에 맞춰 바꿀 수 있습니다(이 기기에만 저장).",
    "할 일" to "할 일을 적고 완료(↵). 기한은 '11/20', '내일'처럼 적고, ☆로 중요 표시, 체크로 끝냅니다.",
    "인증키" to "국가법령정보 OC와 OpenDART 인증키는 앱에 기본으로 들어 있습니다. 내 키를 쓰려면 설정에서 바꿀 수 있고, 직접 넣은 키는 이 기기의 보안 저장소로 암호화해 둡니다.",
)

@Composable
fun AboutScreen(update: UpdateModel, onMessage: (String) -> Unit) {
    val state by update.state.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.app_logo), null, Modifier.size(60.dp))
                Column {
                    Text(APP_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("버전 ${BuildInfo.VERSION} · Android", style = MaterialTheme.typography.bodyMedium)
                    Text("검사품질관리 매뉴얼·서식 · 법률 검색 · 기업 개황 · 양정·과징금·과태료 · 할 일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LabelValue("만든이", AUTHOR, 72)
            LinkRow("이메일", AUTHOR_EMAIL) { Os.browse("mailto:$AUTHOR_EMAIL") }
            LinkRow("홈페이지", AUTHOR_HOME) { Os.browse(AUTHOR_HOME) }
        }

        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("업데이트") { Pill("서명 확인", Tone.OK) }
            Text("앱을 켤 때 하루 한 번 새 버전을 확인합니다(설정에서 끌 수 있음). 받은 APK가 이 앱과 같은 공식 서명일 때만 설치합니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val s = state) {
                UpdateState.Idle -> Text("현재 버전 ${BuildInfo.VERSION}", style = MaterialTheme.typography.bodyMedium)
                UpdateState.Checking -> { Text("확인하는 중…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is UpdateState.UpToDate -> Text("최신 버전입니다 (${BuildInfo.VERSION}).", style = MaterialTheme.typography.bodyMedium)
                is UpdateState.Available -> {
                    Text("새 버전 ${s.manifest.version}이 있습니다.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (s.manifest.notes.isNotBlank()) Text(s.manifest.notes, style = MaterialTheme.typography.bodySmall)
                }
                is UpdateState.Downloading -> { Text("${s.manifest.version} 내려받는 중… ${(s.progress * 100).toInt()}%"); LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth()) }
                is UpdateState.Ready -> Text("${s.manifest.version} 설치 준비가 됐습니다. 공식 서명을 확인했습니다.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                is UpdateState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is UpdateState.Available -> {
                        Button(onClick = { update.download() }) { Text("내려받기") }
                        TextButton(onClick = { update.skip() }) { Text("건너뛰기") }
                    }
                    is UpdateState.Ready -> {
                        Button(onClick = { update.install(onMessage) }) { Text("설치") }
                        TextButton(onClick = { update.skip() }) { Text("나중에") }
                    }
                    else -> OutlinedButton(onClick = { update.check() }, enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading) { Text("업데이트 확인") }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("기본 사용 방법")
            GUIDE.forEach { (title, body) ->
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("자료 출처와 유의 사항")
            Text("법령 정보: 국가법령정보센터 Open API(법제처) · 공시 정보: OpenDART(금융감독원) · 검사품질관리 매뉴얼·서식: 금융감독원 내부 자료(앱 안에 암호화 보관, 외부 유출 금지). 이 앱은 업무 보조 도구이며, 법령 해석과 제재 양정의 최종 판단은 원문과 내부 기준에 따릅니다. 입력한 자료는 이 기기에만 저장됩니다.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable(onClick = onClick))
    }
}

package io.github.hdlee73.fsshelper.data

import io.github.hdlee73.fsshelper.BuildConfig

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    /** 국가법령정보 Open API 인증값(OC). 기기 Keystore로 암호화한 값 */
    val lawOcSealed: String = "",
    /** OpenDART 인증키. 기기 Keystore로 암호화한 값 */
    val dartKeySealed: String = "",
    val sidebarCollapsed: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 앱을 켤 때 하루 한 번 새 버전을 확인한다 */
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val skippedVersion: String = "",
    val recentLaw: List<String> = emptyList(),
    val favoriteLaw: List<String> = emptyList(),
    /** 처음 실행 안내를 본 버전 */
    val seenWelcome: String = "",
    /** 인증키 안내 창을 다시 보지 않기로 한 것("law", "dart") */
    val keyGuideHidden: Set<String> = emptySet(),
)

/** 설정 저장소. 화면은 [state]를 구독하고, 바꿀 때는 [update]를 부른다. */
class SettingsRepo(file: File = File(AppDirs.root, "settings.json")) {
    private val store = JsonFile(file, AppSettings.serializer()) { AppSettings() }
    private val _state = MutableStateFlow(store.read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    /** 직접 넣은 키가 있으면 그것을, 없으면 빌드할 때 GitHub Secrets에서 넣은 키를 쓴다. */
    val lawOc: String get() = Secrets.open(_state.value.lawOcSealed).ifBlank { BuildConfig.LAW_OC }
    val dartKey: String get() = Secrets.open(_state.value.dartKeySealed).ifBlank { BuildConfig.DART_API_KEY }
    /** 사용자가 직접 넣은 키(화면 입력 칸에는 이것만 보여 준다. 내장 키는 화면에 드러내지 않는다) */
    val userLawOc: String get() = Secrets.open(_state.value.lawOcSealed)
    val userDartKey: String get() = Secrets.open(_state.value.dartKeySealed)
    val lawOcBuiltIn: Boolean get() = _state.value.lawOcSealed.isBlank() && BuildConfig.LAW_OC.isNotBlank()
    val dartKeyBuiltIn: Boolean get() = _state.value.dartKeySealed.isBlank() && BuildConfig.DART_API_KEY.isNotBlank()

    fun update(block: (AppSettings) -> AppSettings) {
        _state.update(block)
        runCatching { store.write(_state.value) }
    }

    fun setLawOc(value: String) = update { it.copy(lawOcSealed = Secrets.seal(value.trim())) }
    fun setDartKey(value: String) = update { it.copy(dartKeySealed = Secrets.seal(value.trim())) }

    fun addRecentLaw(q: String) = update { s -> s.copy(recentLaw = (listOf(q) + s.recentLaw.filter { it != q }).take(15)) }
    fun clearRecentLaw() = update { it.copy(recentLaw = emptyList()) }
    fun toggleFavoriteLaw(q: String) = update { s ->
        s.copy(favoriteLaw = if (q in s.favoriteLaw) s.favoriteLaw - q else (s.favoriteLaw + q).take(30))
    }
}

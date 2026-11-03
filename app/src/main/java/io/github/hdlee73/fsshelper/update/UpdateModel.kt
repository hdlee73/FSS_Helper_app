package io.github.hdlee73.fsshelper.update

import io.github.hdlee73.fsshelper.data.SettingsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val at: Long) : UpdateState
    data class Available(val manifest: UpdateManifest, val url: String) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState
    /** 서명을 확인한 APK */
    data class Ready(val manifest: UpdateManifest, val setup: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateModel(private val settings: SettingsRepo, private val scope: CoroutineScope, private val updater: Updater = Updater()) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 앱을 켤 때: 자동 업데이트가 켜져 있고 하루가 지났으면 조용히 확인한다(실패해도 알리지 않음). */
    fun autoCheck() {
        val s = settings.state.value
        if (!s.autoUpdate || System.currentTimeMillis() - s.lastUpdateCheck < 20 * 3600 * 1000L) return
        check(quiet = true)
    }

    fun check(quiet: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val found = updater.check()
                settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                when {
                    found == null -> UpdateState.UpToDate(System.currentTimeMillis())
                    quiet && found.first.version == settings.state.value.skippedVersion -> UpdateState.Idle
                    else -> UpdateState.Available(found.first, found.second)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: "업데이트를 확인하지 못했습니다.")
            }
        }
    }

    fun download() {
        val s = _state.value as? UpdateState.Available ?: return
        scope.launch {
            _state.value = UpdateState.Downloading(s.manifest, 0f)
            _state.value = try {
                val file = updater.download(s.url) { p -> _state.value = UpdateState.Downloading(s.manifest, p) }
                UpdateState.Ready(s.manifest, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "내려받지 못했습니다.")
            }
        }
    }

    fun skip() {
        val v = when (val s = _state.value) {
            is UpdateState.Available -> s.manifest.version
            is UpdateState.Ready -> s.manifest.version
            else -> return
        }
        settings.update { it.copy(skippedVersion = v) }
        _state.value = UpdateState.Idle
    }

    /** 설치 창을 띄운다(안드로이드가 설치를 마치면 앱을 다시 연다). 실패하면 이유를 알린다. */
    fun install(onMessage: (String) -> Unit) {
        val s = _state.value as? UpdateState.Ready ?: return
        runCatching { updater.launchInstaller(s.setup) }.onFailure { onMessage(it.message ?: "설치 창을 열지 못했습니다.") }
    }
}

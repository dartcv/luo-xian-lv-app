package com.example.autoplaymusic.ui.settings

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.autoplaymusic.data.AccountSession
import com.example.autoplaymusic.data.AppearanceSettings
import com.example.autoplaymusic.data.AppearanceStore
import com.example.autoplaymusic.data.ConfigStore
import com.example.autoplaymusic.data.KeyLayout
import com.example.autoplaymusic.data.SessionStore
import com.example.autoplaymusic.service.MusicAccessibilityService
import com.example.autoplaymusic.update.AppUpdate
import com.example.autoplaymusic.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val session: AccountSession? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** 应用更新检查结果：非空时弹出更新对话框 */
    val update: AppUpdate? = null,
    val updateStatus: String? = null,
    /** 注册成功：触发跳转 + Snackbar */
    val registered: Boolean = false,
    val appearance: AppearanceSettings = AppearanceSettings(),
)

class SettingsViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val sessionStore = SessionStore(app)
    private val updater = UpdateManager(app)
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() =
        _state.update {
            it.copy(
                session = sessionStore.current(),
                appearance = AppearanceStore.load(app),
            )
        }

    fun login(
        account: String,
        password: String,
    ) {
        if (account.isBlank() || password.isBlank()) return
        _state.update { it.copy(busy = true) }
        updater.login(account.trim(), password) { result ->
            _state.update { it.copy(busy = false) }
            result
                .onSuccess { login ->
                    sessionStore.save(login.session)
                    refresh()
                    _state.update { it.copy(message = "登录成功") }
                }.onFailure { e -> _state.update { it.copy(error = e.message ?: "登录失败") } }
        }
    }

    fun logout() {
        sessionStore.clear()
        refresh()
    }

    fun register(
        username: String,
        email: String,
        password: String,
        code: String,
        nickname: String,
    ) {
        if (listOf(username, email, password, code).any(String::isBlank)) {
            _state.update { it.copy(message = "请填写完整信息") }
            return
        }
        _state.update { it.copy(busy = true) }
        updater.register(username.trim(), email.trim(), password, code, nickname.trim()) { result ->
            _state.update { it.copy(busy = false) }
            result
                .onSuccess { login ->
                    sessionStore.save(login.session)
                    refresh()
                    _state.update { it.copy(registered = true, message = "注册成功") }
                }.onFailure { e -> _state.update { it.copy(error = e.message ?: "注册失败") } }
        }
    }

    fun ackRegistered() = _state.update { it.copy(registered = false) }

    fun checkAppUpdate() {
        _state.update { it.copy(updateStatus = "正在检查更新") }
        updater.checkApp { result ->
            result
                .onSuccess { update ->
                    if (update == null) {
                        _state.update { it.copy(updateStatus = "已是最新版本") }
                    } else {
                        _state.update { it.copy(update = update, updateStatus = null) }
                    }
                }.onFailure { e ->
                    _state.update { it.copy(updateStatus = null, error = e.message ?: "检查更新失败") }
                }
        }
    }

    fun installUpdate(
        activity: Activity,
        update: AppUpdate,
    ) {
        updater.install(activity, update) { status -> _state.update { it.copy(updateStatus = status) } }
    }

    fun dismissUpdate() = _state.update { it.copy(update = null) }

    fun consumeUpdateStatus() = _state.update { it.copy(updateStatus = null) }

    fun saveAppearance(settings: AppearanceSettings) {
        AppearanceStore.save(app, settings)
        _state.update { it.copy(appearance = settings) }
    }

    /** 开关飘雪：直接改偏好并落盘，不需要走对话框。 */
    fun setSnowEnabled(enabled: Boolean) {
        val next = _state.value.appearance.copy(snowEnabled = enabled)
        AppearanceStore.save(app, next)
        _state.update { it.copy(appearance = next) }
    }

    fun loadCalibration(): KeyLayout = ConfigStore.load(app)

    /** 保存校准并热加载到服务；返回是否成功。 */
    fun saveCalibration(layout: KeyLayout): Boolean =
        runCatching {
            ConfigStore.save(app, layout)
            MusicAccessibilityService.instance?.reloadConfig()
        }.isSuccess

    fun dismissError() = _state.update { it.copy(error = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

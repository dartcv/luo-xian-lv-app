package com.example.autoplaymusic

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.autoplaymusic.data.AppearanceStore
import com.example.autoplaymusic.data.SongRepository
import com.example.autoplaymusic.service.MusicAccessibilityService
import com.example.autoplaymusic.ui.AppEvents
import com.example.autoplaymusic.ui.components.OnboardingDialog
import com.example.autoplaymusic.ui.components.Snowfall
import com.example.autoplaymusic.ui.navigation.AppNavHost
import com.example.autoplaymusic.ui.theme.LuoXianLvTheme
import com.example.autoplaymusic.update.HotUpdateCoordinator

/** Compose 单 Activity 入口：只负责挂 UI 树与生命周期级的服务/热更新对齐。 */
class MainActivity : AppCompatActivity() {
    private lateinit var repository: SongRepository
    private lateinit var hotUpdates: HotUpdateCoordinator
    private var showOnboarding by mutableStateOf(false)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        repository = SongRepository(this)
        hotUpdates = HotUpdateCoordinator(this, repository)
        AppearanceStore.initialize(this)
        // 权限引导只在首次启动弹一次，此后不再打扰；
        // 之后的运行时检查在「我的」页悬浮窗开关处（LibraryViewModel.setFloatingEnabled）
        showOnboarding = isFirstLaunch() && !MusicAccessibilityService.isEnabled(this)
        setContent {
            val appearance by AppearanceStore.settings.collectAsState()
            LuoXianLvTheme(appearance = appearance) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 页面底色由各页自己铺的渐变底承担（见 ui/theme/Backdrop.kt）。
                    AppNavHost()
                    // 飘雪盖在最上层：Canvas 不消费触摸，不会挡住底下的按钮与列表。
                    Snowfall(enabled = appearance.snowEnabled)
                    if (showOnboarding) {
                        OnboardingDialog(
                            onEnable = {
                                markOnboardingDone()
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onLater = ::markOnboardingDone,
                        )
                    }
                }
            }
        }
    }

    private val appPrefs by lazy { getSharedPreferences("app_state", MODE_PRIVATE) }

    private fun isFirstLaunch(): Boolean = !appPrefs.getBoolean("onboarding_done", false)

    private fun markOnboardingDone() {
        appPrefs.edit().putBoolean("onboarding_done", true).apply()
        showOnboarding = false
    }

    override fun onResume() {
        super.onResume()
        // 无障碍服务可能在本应用暂停期间被启用；回到前台时按持久化偏好重新对齐悬浮窗
        MusicAccessibilityService.instance?.showFloating(repository.floatingEnabled)
        hotUpdates.check { runOnUiThread { AppEvents.notifyLibraryChanged() } }
    }
}

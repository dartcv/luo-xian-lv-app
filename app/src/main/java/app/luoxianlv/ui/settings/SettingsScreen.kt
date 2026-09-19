package app.luoxianlv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luoxianlv.data.AccountSession
import app.luoxianlv.service.KeepAlive
import app.luoxianlv.ui.components.ErrorDialogHost
import app.luoxianlv.ui.components.NavBarClearance
import app.luoxianlv.ui.components.PageTitle
import app.luoxianlv.ui.components.PreferenceDivider
import app.luoxianlv.ui.components.PreferenceItem
import app.luoxianlv.ui.components.PreferenceSection
import app.luoxianlv.ui.components.PreferenceSwitchItem
import app.luoxianlv.ui.components.SettingsCard
import app.luoxianlv.ui.components.SnackbarNotice

/** 设置页：整页一张白卡，卡内按组平铺（小标题 + 行 + 分隔线）；网站登录在对话框中完成。 */
@Composable
fun SettingsScreen(
    onLogin: () -> Unit,
    onAbout: () -> Unit,
    onDiagnostics: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAppearance by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.refresh()
        if (!granted) KeepAlive.openNotificationSettings(context)
    }

    LaunchedEffect(Unit) { vm.refresh() }
    // 从系统授权页返回时刷新保活状态（电池白名单 / 通知权限都在系统页里改）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SnackbarNotice(state.message, snackbarHostState, vm::consumeMessage)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = NavBarClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageTitle("设置") }
        item {
            SettingsCard {
                PreferenceSection("账号") {
                    PreferenceItem(
                        title = "网站账号",
                        summary =
                            state.session?.let {
                                "已登录${it.nickname.takeIf { n -> n.isNotBlank() }?.let { n -> " · $n" }.orEmpty()}"
                            } ?: "未登录",
                    ) { onLogin() }
                }
                PreferenceDivider()
                PreferenceSection("外观") {
                    PreferenceItem(
                        title = "控件透明度",
                        summary = "${"%.0f".format(state.appearance.transparency * 100)}%",
                    ) { showAppearance = true }
                    PreferenceDivider()
                    PreferenceSwitchItem(
                        title = "飘雪",
                        checked = state.appearance.snowEnabled,
                        onCheckedChange = vm::setSnowEnabled,
                        summary = "显示雪花动效",
                    )
                }
                PreferenceDivider()
                PreferenceSection("后台运行") {
                    PreferenceItem("播放诊断", "查看播放状态与诊断记录", onClick = onDiagnostics)
                    PreferenceDivider()
                    PreferenceItem(
                        title = "忽略电池优化",
                        summary =
                            if (state.keepAlive.batteryExempt) {
                                "已允许"
                            } else {
                                "未允许，后台播放可能中断"
                            },
                    ) { KeepAlive.requestBatteryExemption(context) }
                    run {
                        PreferenceDivider()
                        PreferenceItem(
                            title = "播放通知",
                            summary =
                                if (state.keepAlive.notificationsGranted) {
                                "已开启"
                                } else {
                                "未开启，点击设置"
                                },
                        ) {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                KeepAlive.openNotificationSettings(context)
                            }
                        }
                    }
                    PreferenceDivider()
                    PreferenceItem(
                        title = "自启动与后台权限",
                        summary = "部分手机需在系统设置中允许后台运行",
                    ) { KeepAlive.openAutoStartSettings(context) }
                }
                PreferenceDivider()
                PreferenceSection("关于") {
                    PreferenceItem(title = "关于落弦律", onClick = onAbout)
                }
            }
        }
    }

    if (showAppearance) {
        AppearanceDialog(
            initial = state.appearance,
            onDismiss = { showAppearance = false },
            onSave = {
                vm.saveAppearance(it)
                showAppearance = false
            },
        )
    }
    ErrorDialogHost(state.error, vm::dismissError)
}


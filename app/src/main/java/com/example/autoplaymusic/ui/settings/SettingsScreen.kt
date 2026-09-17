package com.example.autoplaymusic.ui.settings

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autoplaymusic.data.AccountSession
import com.example.autoplaymusic.ui.components.ErrorDialogHost
import com.example.autoplaymusic.ui.components.NavBarClearance
import com.example.autoplaymusic.ui.components.PageTitle
import com.example.autoplaymusic.ui.components.PreferenceDivider
import com.example.autoplaymusic.ui.components.PreferenceItem
import com.example.autoplaymusic.ui.components.PreferenceSection
import com.example.autoplaymusic.ui.components.PreferenceSwitchItem
import com.example.autoplaymusic.ui.components.SettingsCard
import com.example.autoplaymusic.ui.components.SnackbarNotice

/** 设置页：整页一张白卡，卡内按组平铺（小标题 + 行 + 分隔线）；网站登录在对话框中完成。 */
@Composable
fun SettingsScreen(
    onRegister: () -> Unit,
    onAbout: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showCalibration by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }
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
                    ) { showAccount = true }
                }
                PreferenceDivider()
                PreferenceSection("播放") {
                    PreferenceItem(
                        title = "按键位置",
                        summary = "校准游戏琴键与调式按钮的屏幕坐标",
                    ) { showCalibration = true }
                }
                PreferenceDivider()
                PreferenceSection("外观") {
                    PreferenceItem(
                        title = "控件透明度",
                        summary = "${"%.0f".format(state.appearance.transparency * 100)}% · 影响卡片与导航栏等容器",
                    ) { showAppearance = true }
                    PreferenceDivider()
                    PreferenceSwitchItem(
                        title = "飘雪",
                        checked = state.appearance.snowEnabled,
                        onCheckedChange = vm::setSnowEnabled,
                        summary = "从屏幕上方飘落微小雪花",
                    )
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
    if (showAccount) {
        AccountDialog(
            session = state.session,
            busy = state.busy,
            onLogin = vm::login,
            onLogout = {
                vm.logout()
                showAccount = false
            },
            onRegister = {
                showAccount = false
                onRegister()
            },
            onDismiss = { showAccount = false },
        )
    }
    if (showCalibration) {
        CalibrationDialog(
            initial = vm.loadCalibration(),
            onDismiss = { showCalibration = false },
            onSave = { layout ->
                vm.saveCalibration(layout).also { ok ->
                    if (ok) showCalibration = false
                }
            },
        )
    }
    ErrorDialogHost(state.error, vm::dismissError)
}

/** 网站账号对话框：未登录显示登录表单（含注册入口），已登录显示账号信息与退出。 */
@Composable
private fun AccountDialog(
    session: AccountSession?,
    busy: Boolean,
    onLogin: (account: String, password: String) -> Unit,
    onLogout: () -> Unit,
    onRegister: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session == null) "登录网站账号" else "网站账号") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (session == null) {
                    var account by remember { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = account,
                        onValueChange = { account = it },
                        label = { Text("用户名 / 邮箱") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        onClick = { onLogin(account, password) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("登录") }
                    TextButton(
                        onClick = onRegister,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    ) { Text("注册网站账号") }
                } else {
                    Text(
                        "已登录${session.nickname.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("退出网站账号") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

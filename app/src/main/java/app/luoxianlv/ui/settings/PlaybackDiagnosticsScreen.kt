package app.luoxianlv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.luoxianlv.data.ConfigStore
import app.luoxianlv.debug.DebugExport
import app.luoxianlv.service.MusicAccessibilityService
import app.luoxianlv.ui.components.NavBarClearance
import app.luoxianlv.ui.components.PreferenceItem
import app.luoxianlv.ui.components.PreferenceSection
import app.luoxianlv.ui.components.SettingsCard
import kotlinx.coroutines.launch

@Composable
fun PlaybackDiagnosticsScreen(onBack: () -> Unit, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf<MusicAccessibilityService.Diagnostics?>(null) }
    LaunchedEffect(Unit) { diagnostics = MusicAccessibilityService.instance?.diagnostics() }
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    val d = diagnostics
    val layout = remember { ConfigStore.load(context.applicationContext) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = NavBarClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("播放诊断", modifier = Modifier.padding(top = 12.dp))
            }
        }
        item {
            SettingsCard {
                PreferenceSection("当前状态") {
                    PreferenceItem("无障碍服务", if (d?.serviceEnabled == true) "已开启" else "未开启") {}
                    PreferenceItem("播放状态", when { d == null -> "服务未运行"; d.playing -> "播放中"; d.preparing -> "识别屏幕中"; else -> "已停止" }) {}
                    PreferenceItem("当前曲目", d?.songTitle ?: "服务未运行") {}
                }
            }
        }
        item {
            SettingsCard {
                PreferenceSection("最近一次环境信息") {
                    val display = d?.display?.let { "${it.first} × ${it.second} · 旋转 ${it.third * 90}°" } ?: "暂无"
                    PreferenceItem("当前屏幕", display) {}
                    PreferenceItem("播放时屏幕", d?.playbackDisplay?.let { "${it.first} × ${it.second} · 旋转 ${it.third * 90}°" } ?: "暂无") {}
                    PreferenceItem("最近点击坐标", d?.lastCoordinates ?: "暂无") {}
                    PreferenceItem("播放错误", d?.error ?: "无") {}
                    PreferenceItem("手势结果", d?.gestureFailure ?: "无") {}
                }
            }
        }
        item {
            SettingsCard {
                PreferenceSection("按键布局（归一化比例，0-1）") {
                    PreferenceItem("音符 X", layout.noteX.joinToString(" ") { "%.3f".format(it) }) {}
                    PreferenceItem("音符 Y", "%.3f".format(layout.noteY)) {}
                    PreferenceItem(
                        "模式键",
                        layout.modes.entries.joinToString(" ") { "${it.key.name}=%.3f,%.3f".format(it.value[0], it.value[1]) },
                    ) {}
                }
            }
        }
        item {
            SettingsCard {
                PreferenceSection("调试") {
                    PreferenceItem("导出调试 ZIP", "包含最近 15 张游戏截图、日志与设备信息，分享前请确认内容") {
                        if (!exporting) scope.launch {
                            exporting = true
                            val ok = DebugExport.exportAndShare(context.applicationContext)
                            exporting = false
                            snackbarHostState.showSnackbar(if (ok) "已打开分享面板" else "导出失败，请稍后重试")
                        }
                    }
                }
            }
        }
        item { Text("提示：播放前请保持目标游戏界面可见，并确保无障碍、悬浮窗和通知权限均已开启。") }
    }
}

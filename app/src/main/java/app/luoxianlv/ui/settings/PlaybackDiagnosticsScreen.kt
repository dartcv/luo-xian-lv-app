package app.luoxianlv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luoxianlv.service.MusicAccessibilityService
import app.luoxianlv.ui.components.NavBarClearance
import app.luoxianlv.ui.components.PreferenceItem
import app.luoxianlv.ui.components.PreferenceSection
import app.luoxianlv.ui.components.SettingsCard

@Composable
fun PlaybackDiagnosticsScreen(onBack: () -> Unit, snackbarHostState: SnackbarHostState) {
    var diagnostics by remember { mutableStateOf<MusicAccessibilityService.Diagnostics?>(null) }
    LaunchedEffect(Unit) { diagnostics = MusicAccessibilityService.instance?.diagnostics() }
    val d = diagnostics
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
                    val display = d?.display?.let { "${it.first} × ${it.second} · ${it.third}" } ?: "暂无"
                    PreferenceItem("屏幕", display) {}
                    PreferenceItem("播放错误", d?.error ?: "无") {}
                    PreferenceItem("手势结果", d?.gestureFailure ?: "无") {}
                }
            }
        }
        item { Text("提示：播放前请保持目标游戏界面可见，并确保无障碍、悬浮窗和通知权限均已开启。") }
    }
}

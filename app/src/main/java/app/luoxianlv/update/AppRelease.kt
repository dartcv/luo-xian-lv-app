package app.luoxianlv.update

import org.json.JSONObject
import java.net.URI

/**
 * 一个下载渠道。官方渠道（oss）用 manifest 顶层签名链接，
 * GitHub 渠道走服务端代理 `/api/update/github`（302 到 release asset）。
 * [sha256] / [size] 取渠道自己的校验值；老 manifest 没给时回退到顶层 apkSha256 / apkSize。
 */
data class UpdateSource(
    val id: String,
    val url: String,
    val sha256: String = "",
    val size: Long = 0,
) {
    val label: String get() = if (id == "github") "GitHub 渠道" else "官方渠道"
}

data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val size: Long,
    val notes: List<String>,
    val mandatory: Boolean,
    val sources: List<UpdateSource>,
)

/** Strictly newer APKs only; channel URLs must refer to the same signed artifact. */
fun parseAppRelease(json: JSONObject, currentCode: Int, baseUrl: String, preferred: String, allowLocal: Boolean): AppRelease? {
    if (!json.optBoolean("enabled", true)) return null
    val code = json.getInt("latestVersionCode")
    if (code <= currentCode) return null
    val sha = json.getString("apkSha256").lowercase()
    require(sha.matches(Regex("[a-f0-9]{64}"))) { "更新信息缺少有效的安装包校验值" }
    val sources = mutableListOf<UpdateSource>()
    val channels = json.optJSONObject("channels")
    val topSize = json.optLong("apkSize", 0)
    for (id in listOf(preferred, "oss", "github").distinct()) {
        val channel = channels?.optJSONObject(id) ?: continue
        val url = channel.optString("url").ifBlank { null } ?: continue
        val channelSha =
            channel.optString("sha256").lowercase().takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: sha
        val channelSize = channel.optLong("size", 0).takeIf { it > 0 } ?: topSize
        sources += UpdateSource(id, validatedUpdateUrl(url, baseUrl, allowLocal), channelSha, channelSize)
    }
    if (sources.isEmpty()) sources += UpdateSource("oss", validatedUpdateUrl(json.getString("apkUrl"), baseUrl, allowLocal), sha, topSize)
    val notes = json.optJSONArray("releaseNotes")
    return AppRelease(code, json.getString("latestVersionName"), sha, json.optLong("apkSize", 0),
        if (notes == null) emptyList() else List(notes.length()) { notes.getString(it) },
        json.optBoolean("mandatory") || currentCode < json.optInt("minSupportedVersionCode", 1), sources)
}

fun validatedUpdateUrl(value: String, baseUrl: String, allowLocal: Boolean): String {
    val uri = URI(baseUrl.trimEnd('/') + "/").resolve(value)
    val local = allowLocal && uri.host in listOf("127.0.0.1", "localhost", "10.0.2.2")
    require(uri.host != null && uri.userInfo == null && (uri.scheme == "https" || (local && uri.scheme == "http"))) {
        "更新下载地址必须使用 HTTPS"
    }
    return uri.toString()
}

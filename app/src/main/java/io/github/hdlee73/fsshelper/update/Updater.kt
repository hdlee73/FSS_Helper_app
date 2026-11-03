package io.github.hdlee73.fsshelper.update

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.hdlee73.fsshelper.BuildInfo
import io.github.hdlee73.fsshelper.Platform
import io.github.hdlee73.fsshelper.data.AppDirs
import io.github.hdlee73.fsshelper.data.AppJson
import io.github.hdlee73.fsshelper.ui.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 새 버전 정보(GitHub 최신 릴리스) */
data class UpdateManifest(val version: String, val notes: String = "", val published: String = "")

class UpdateException(message: String) : Exception(message)

/**
 * 자동 업데이트. GitHub 최신 릴리스의 APK를 받아, 지금 설치된 앱과 같은 서명 키로 서명됐는지 확인한 뒤에만 설치 창을 띄운다.
 * 배포 서명 키는 저장소 주인의 GitHub Secrets에만 있으므로 다른 사람이 만든 APK는 업데이트로 받아들이지 않는다
 * (안드로이드도 서명이 다른 APK는 덮어 설치하지 않는다).
 */
class Updater {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followRedirects(true).build()

    private fun req(url: String) = Request.Builder().url(url)
        .header("User-Agent", "FSS_Helper-Android/${BuildInfo.VERSION}")
        .header("Accept", "application/vnd.github+json, application/octet-stream, */*")
        .build()

    /** 최신 릴리스를 확인한다. 새 버전이 없으면 null. 두 번째 값은 APK 주소. */
    suspend fun check(): Pair<UpdateManifest, String>? = withContext(Dispatchers.IO) {
        val body = try {
            http.newCall(req("https://api.github.com/repos/${BuildInfo.REPO}/releases/latest")).execute().use { res ->
                if (res.code == 404) throw UpdateException("아직 공개된 릴리스가 없습니다.")
                if (res.code !in 200..299) throw UpdateException("업데이트 서버 응답 오류(${res.code})")
                res.body?.string().orEmpty()
            }
        } catch (e: UpdateException) {
            throw e
        } catch (e: Exception) {
            throw UpdateException("업데이트 서버에 연결하지 못했습니다. 인터넷 연결을 확인해 주세요.")
        }
        val release = AppJson.parseToJsonElement(body).jsonObject
        val version = release["tag_name"]?.jsonPrimitive?.content.orEmpty().removePrefix("v")
        val apk = release["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.content.orEmpty().endsWith(".apk") }
            ?: throw UpdateException("릴리스에 APK 파일이 없습니다.")
        val m = UpdateManifest(version, release["body"]?.jsonPrimitive?.content.orEmpty().take(1500), release["published_at"]?.jsonPrimitive?.content.orEmpty())
        if (compareVersions(version, BuildInfo.VERSION) <= 0) null else m to apk["browser_download_url"]!!.jsonPrimitive.content
    }

    suspend fun download(url: String, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(AppDirs.cache, "update").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "FSS_Helper-update.apk")
        http.newCall(req(url)).execute().use { res ->
            if (res.code !in 200..299) throw UpdateException("내려받지 못했습니다(${res.code}).")
            val b = res.body ?: throw UpdateException("내려받지 못했습니다.")
            val total = b.contentLength().coerceAtLeast(1)
            var done = 0L
            b.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        }
        verifySigner(file)
        file
    }

    /** 받은 APK의 서명 인증서가 지금 앱과 같은지 확인한다. */
    private fun verifySigner(apk: File) {
        val pm = Platform.app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val mine = pm.getPackageInfo(Platform.app.packageName, flags)
        val theirs = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: run { apk.delete(); throw UpdateException("받은 파일이 올바른 APK가 아닙니다.") }
        if (theirs.packageName != Platform.app.packageName || certs(mine) != certs(theirs) || certs(mine).isEmpty()) {
            apk.delete()
            throw UpdateException("공식 서명이 아닌 파일이라 설치하지 않습니다.")
        }
    }

    @Suppress("DEPRECATION")
    private fun certs(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners?.toList().orEmpty() else p.signatures?.toList().orEmpty()
        return sigs.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** 설치 창을 띄운다. '알 수 없는 앱 설치' 허용이 필요하면 그 설정 화면을 먼저 연다. */
    fun launchInstaller(apk: File) {
        val ctx = Platform.app
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            throw UpdateException("'이 출처 허용'을 켠 뒤 다시 '설치'를 눌러 주세요.")
        }
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(Os.uriOf(apk), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** "1.2.10" > "1.2.9" */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}

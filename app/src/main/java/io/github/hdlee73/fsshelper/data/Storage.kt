package io.github.hdlee73.fsshelper.data

import io.github.hdlee73.fsshelper.Platform
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** 앱 데이터 폴더(앱 전용 내부 저장소). 앱을 지우면 함께 지워진다. */
object AppDirs {
    val root: File get() = Platform.app.filesDir
    fun sub(name: String): File = File(root, name).apply { mkdirs() }
    val cache: File get() = Platform.app.cacheDir
    /** 내보낸 파일(HWPX·PDF). 공유하기로 다른 앱에 넘긴다. */
    val exports: File get() = File(root, "exports").apply { mkdirs() }
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    explicitNulls = false
}

/** JSON 파일 하나에 값을 저장한다. 쓰기는 임시 파일에 쓴 뒤 바꿔 끼워서 중간에 꺼져도 파일이 깨지지 않는다. */
class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) {
    fun read(): T = runCatching {
        if (!file.exists()) default() else AppJson.decodeFromString(serializer, file.readText(Charsets.UTF_8))
    }.getOrElse {
        runCatching { file.copyTo(File(file.parentFile, file.name + ".broken"), overwrite = true) }
        default()
    }

    @Synchronized
    fun write(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(serializer, value), Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

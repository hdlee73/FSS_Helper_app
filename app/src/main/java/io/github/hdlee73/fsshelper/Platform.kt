package io.github.hdlee73.fsshelper

import android.annotation.SuppressLint
import android.content.Context

/** 앱 버전·업데이트 저장소 */
object BuildInfo {
    val VERSION: String get() = BuildConfig.VERSION_NAME
    const val REPO: String = BuildConfig.REPO
}

/** 화면 밖(저장소·모델)에서 쓰는 앱 Context */
@SuppressLint("StaticFieldLeak")
object Platform {
    lateinit var app: Context
        private set

    /** 지금 떠 있는 화면(파일 고르기·저장 창을 띄울 때 쓴다) */
    @Volatile var activity: MainActivity? = null

    fun init(context: Context) {
        app = context.applicationContext
    }
}

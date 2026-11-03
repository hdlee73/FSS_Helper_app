import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersion: String = (findProperty("appVersion") as String?) ?: "0.1.0"

/** 1.2.3 → 10203 */
fun versionCodeOf(v: String): Int {
    val p = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    return p[0] * 10000 + p[1] * 100 + p[2]
}

/**
 * 비밀 값은 저장소에 넣지 않는다. GitHub Actions는 Secrets를 환경 변수로 넘기고,
 * 내 PC에서는 local.properties(커밋 안 됨)에 같은 이름으로 적는다.
 */
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String = (System.getenv(name) ?: localProps.getProperty(name) ?: "").trim()
fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.github.hdlee73.fsshelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hdlee73.fsshelper"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        resourceConfigurations += listOf("ko")

        buildConfigField("String", "LAW_OC", quoted(secret("LAW_OC")))
        buildConfigField("String", "DART_API_KEY", quoted(secret("DART_API_KEY")))
        buildConfigField("String", "LIBRARY_KEY", quoted(secret("LIBRARY_KEY")))
        buildConfigField("String", "REPO", quoted("hdlee73/FSS_Helper_app"))
    }

    signingConfigs {
        // 배포 서명 키: 저장소 주인의 GitHub Secrets에만 있다. 다른 사람이 만든 APK는 서명이 달라 업데이트로 설치되지 않는다.
        val ks = secret("RELEASE_KEYSTORE_FILE")
        if (ks.isNotBlank() && file(ks).isFile) {
            create("release") {
                storeFile = file(ks)
                storePassword = secret("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 배포 키가 없으면(테스트 빌드) 디버그 키로 서명한다.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += listOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/**", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }

    // 매뉴얼·서식 묶음은 암호화한 파일(secure/library.bin)만 저장소에 있고, 그대로 앱에 넣는다.
    sourceSets["main"].assets.srcDir(rootProject.file("secure/assets"))
}

dependencies {
    implementation(project(":law-core"))
    val bom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

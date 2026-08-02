plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.gridea.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gridea.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 解决 org.jetbrains:annotations 与 org.jetbrains:annotations-java5 的类冲突
// （Markwon 间接引入了 annotations-java5:17.0.0，与 Kotlin 自带的 annotations:23.0.0 重复）
configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Markwon（Markdown 渲染）
    implementation(libs.markwon.core)
    implementation(libs.markwon.editor)
    implementation(libs.markwon.image)
    implementation(libs.markwon.image.coil)
    implementation(libs.markwon.html)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.simple.ext)
    implementation(libs.markwon.linkify)
    // Markwon 图片解码：SVG 与 GIF 支持
    implementation(libs.androidsvg)
    implementation(libs.android.gif.drawable)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // 部署功能
    implementation(libs.okhttp)
    implementation(libs.jsch)

    // Pebble 模板引擎
    implementation(libs.pebble)
    implementation(libs.slf4j.nop)

    // 图片加载
    implementation(libs.coil.compose)

    // 文件访问（SAF DocumentFile）
    implementation(libs.documentfile)

    // 性能优化：Baseline Profile 运行时安装
    // 在 Release 构建中配合 R8 AOT 编译关键路径，消除首次加载 JIT 开销
    implementation(libs.androidx.profileinstaller)

    // 测试
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gleoplay.app"
    compileSdk = 34

    // AAOS Car API (CarUxRestrictionsManager, CarPropertyManager)
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.gleoplay.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-gleoplay"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 시연 도구(T1)를 켠다. 에뮬레이터에 차속·내비 경로가 없을 때 영상 촬영용.
            buildConfigField("boolean", "DEMO_TOOLS", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "DEMO_TOOLS", "false")
            // 공모전 제출용 APK는 디버그 키로 서명한다(정식 배포 키 아님).
            signingConfig = signingConfigs.getByName("debug")
        }
        create("demo") {
            initWith(getByName("release"))
            buildConfigField("boolean", "DEMO_TOOLS", "true")
            matchingFallbacks += listOf("release")
        }
    }
    // 시연용 녹음 대답(PCM)은 debug·demo 빌드에만 넣는다(배포 빌드 제외)
    sourceSets {
        getByName("debug").assets.srcDir("src/demoAssets")
        getByName("demo").assets.srcDir("src/demoAssets")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }
    }
}

dependencies {
    // Pleos Connect SDK — 버전은 Pleos Playground 문서 기준
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    // 화면 스크린샷 검증(에뮬레이터 없이 JVM 에서 Compose 화면을 그려 본다)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

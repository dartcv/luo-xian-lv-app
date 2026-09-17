plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.autoplaymusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.autoplaymusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"

        // 本地模拟器构建指向宿主机上的 Rust 服务；
        // Release 构建用 -PupdateBaseUrl=https://... 覆盖
        val updateBaseUrl = project.findProperty("updateBaseUrl") as String? ?: "http://10.0.2.2:8787"
        buildConfigField("String", "UPDATE_BASE_URL", "\"$updateBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            if (project.hasProperty("releaseStoreFile")) {
                storeFile = file(project.property("releaseStoreFile")!!)
                storePassword = project.property("releaseStorePassword") as String?
                keyAlias = project.property("releaseKeyAlias") as String?
                keyPassword = project.property("releaseKeyPassword") as String?
            }
        }
    }

    buildTypes {
        release {
            if (project.hasProperty("releaseStoreFile")) {
                signingConfig = signingConfigs.getByName("release")
            } else if (project.findProperty("useDebugSigning") == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.10.0")
    implementation("dev.atsushieno:ktmidi-jvm:0.8.2")

    // Compose (M1)：主界面迁移用；material:1.10 暂保留给悬浮窗 View
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

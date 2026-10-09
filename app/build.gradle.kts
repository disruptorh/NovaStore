import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.novastore.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.novastore.fork"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "7.3.1"
    }

    signingConfigs {
        val props = Properties().apply {
            listOf("keystore.properties", "local.properties").forEach { name ->
                rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
            }
        }
        // keystore.properties / local.properties locally, NOVA_* environment variables on CI.
        fun prop(key: String, env: String): String? =
            props.getProperty(key)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }
        val store = prop("storeFile", "NOVA_STORE_FILE")
        if (!store.isNullOrBlank() && file(store).exists()) {
            create("nova") {
                storeFile = file(store)
                storePassword = prop("storePassword", "NOVA_STORE_PASSWORD")
                keyAlias = prop("keyAlias", "NOVA_KEY_ALIAS")
                keyPassword = prop("keyPassword", "NOVA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 + resource shrinking keep the release APK small. Workers,
            // serializers, JNI and the Play API are kept in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.findByName("nova") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }

    testOptions {
        unitTests.all { it.useJUnit() }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    applicationVariants.configureEach {
        if (name == "release") {
            val version = defaultConfig.versionName
            outputs.configureEach {
                val out = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                val abi = out.filters.firstOrNull { it.filterType == com.android.build.OutputFile.ABI }?.identifier
                out.outputFileName = if (abi != null) "NovaStore-$abi-v$version.apk" else "NovaStore-v$version.apk"
            }
        }
    }
}

dependencies {
    // Modules
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:ui"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:security"))
    implementation(project(":core:downloader"))
    implementation(project(":core:installer"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:details"))
    implementation(project(":feature:installed"))
    implementation(project(":feature:updates"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:account"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Network
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil
    implementation(libs.coil.compose)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

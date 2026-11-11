import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gomobileBind by tasks.registering(Exec::class) {
    val goModule = rootProject.layout.projectDirectory.dir("datapath")
    val outputAar = layout.buildDirectory.file("gomobile/datapath.aar")
    inputs.dir(goModule).withPropertyName("goSources")
    outputs.file(outputAar).withPropertyName("aar")
    val goBin = File(System.getProperty("user.home"), "go/bin")
    val ndk = File(System.getenv("ANDROID_NDK_HOME") ?: error("ANDROID_NDK_HOME is required"))
    workingDir = goModule.asFile
    environment("PATH", "${goBin.absolutePath}:${System.getenv("PATH")}")
    environment("ANDROID_NDK_HOME", ndk.absolutePath)
    environment("ANDROID_HOME", System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "")
    doFirst {
        val gomobile = File(goBin, "gomobile")
        check(gomobile.exists()) { "gomobile is required to build the embedded shizzi datapath" }
        outputAar.get().asFile.parentFile.mkdirs()
    }
    commandLine(File(goBin, "gomobile").absolutePath, "bind", "-target=android/arm64", "-androidapi", "26", "-o", outputAar.get().asFile.absolutePath, ".")
}

android {
    namespace = "com.proxyplatform.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.proxyplatform.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "2.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${providers.gradleProperty("API_BASE_URL").orElse("https://api.kun.publicvm.com:10059/api/v1").get()}\"",
        )
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { applicationIdSuffix = ".debug" }
    }

    ndkVersion = "27.0.12077973"
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    signingConfigs {
        create("release") {
            val keystorePath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
            val keystorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
            val keyAliasValue = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
            val keyPasswordValue = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
            if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() && !keyAliasValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }
    buildTypes.getByName("release") {
        signingConfig = signingConfigs.getByName("release")
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(files(layout.buildDirectory.file("gomobile/datapath.aar")))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(files("libs/libbox.aar"))

    // Embedded Wireless ADB client: pairing, mDNS discovery, connect, and shell.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // libadb uses the app-scoped Conscrypt provider on current Android releases.
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

tasks.named("preBuild") { dependsOn(gomobileBind) }

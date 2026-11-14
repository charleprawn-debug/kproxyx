plugins {
    id("com.android.library")
}

android {
    namespace = "com.proxyplatform.embeddedshizuku"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { aidl = true; buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    compileOnly("dev.rikka.shizuku:api:13.1.5")
    compileOnly("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.annotation:annotation:1.3.0")
    implementation("androidx.core:core:1.16.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("dev.rikka.rikkax.parcelablelist:parcelablelist:2.0.1")
    implementation("dev.rikka.hidden:compat:4.4.0")
    implementation("dev.rikka.tools.refine:runtime:4.4.0")
    compileOnly("dev.rikka.hidden:stub:4.4.0")
}

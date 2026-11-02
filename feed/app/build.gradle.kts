plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNo = (System.getenv("PRISMA_BUILD") ?: "1").toInt()
val pw = System.getenv("PRISMA_PW") ?: "prismaRobbie2026"

android {
    namespace = "it.robbie.prisma.feed"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.robbie.prisma.feed"
        minSdk = 34
        targetSdk = 35
        versionCode = buildNo
        versionName = "1.$buildNo"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("../prisma-release.jks")
            storePassword = pw
            keyAlias = "prisma"
            keyPassword = pw
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { aidl = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

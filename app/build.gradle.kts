import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is applied only when keystore.properties exists at the repo root.
// F-Droid builds without it: its unsigned build must match the signed APK on the GitHub
// release byte for byte, and F-Droid then ships that signed APK. A local build with the
// file present is signed with the release key. keystore.properties and the keystore
// itself are gitignored.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) load(FileInputStream(keystorePropertiesFile))
}

// CI 用运行序号覆盖 versionCode，保证每次产出的 APK 都能覆盖安装。
val versionCodeOverride = providers.gradleProperty("versionCodeOverride").orNull?.toIntOrNull()
val versionNameOverride = providers.gradleProperty("versionNameOverride").orNull

android {
    namespace = "com.astroloop.game"
    compileSdk = 36

    // F-Droid / reproducible builds: don't embed AGP's "dependency metadata"
    // block (an opaque Google-signed blob in the APK signing block), which
    // F-Droid's scanner rejects as a non-transparent extra signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.astroloop.game"
        minSdk = 24
        targetSdk = 36
        versionCode = versionCodeOverride ?: 6
        versionName = versionNameOverride ?: "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("game") {
            dimension = "edition"
            buildConfigField("boolean", "LAB", "false")
        }
        create("tuning") {
            dimension = "edition"
            applicationIdSuffix = ".tuning"
            versionCode = versionCodeOverride ?: 2
            versionName = versionNameOverride?.let { "$it-lab" } ?: "1.5-lab.2"
            minSdk = 26
            buildConfigField("boolean", "LAB", "true")
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // CI 的固定签名使用 PKCS12；本地 JKS 不填时保持默认。
                storeType = keystoreProperties.getProperty("storeType", "JKS")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Reproducible builds: don't embed git commit metadata
            // (META-INF/version-control-info.textproto), so F-Droid's from-source
            // build matches our published, self-signed APK byte-for-byte.
            vcsInfo {
                include = false
            }
        }
        debug {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

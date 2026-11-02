import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Source unique de la version : le fichier VERSION à la racine du dépôt (X.Y.Z).
val appVersionName: String = rootProject.file("VERSION").readText().trim()
require(Regex("^\\d+\\.\\d+\\.\\d+$").matches(appVersionName)) { "VERSION doit être au format X.Y.Z" }

// versionCode = github.run_number en CI (toujours croissant), 1 en local.
val appVersionCode: Int = providers.environmentVariable("EXIT_VERSION_CODE").orNull?.toIntOrNull() ?: 1

// Dépôt GitHub dont l'appli suit les releases (mises à jour in-app). Vide = désactivé.
val updateRepo: String = (
    providers.gradleProperty("exitUpdateRepo").orNull
        ?: providers.environmentVariable("EXIT_UPDATE_REPO").orNull
        ?: "Karelisio/Exit"
    ).trim()

// Signature release : fournie par la CI (secrets GitHub) ou en local via les mêmes variables.
val keystorePath: String? = providers.environmentVariable("EXIT_KEYSTORE_FILE").orNull

android {
    namespace = "com.karelisio.exit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.karelisio.exit"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("EXIT_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("EXIT_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("EXIT_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // Tests Robolectric : rendu réel des écrans Compose sur la JVM.
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.documentfile)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    // Robolectric manipule des descripteurs de fichiers internes au JDK.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    // Rendu natif des écrans (captures) : plus de mémoire que la valeur par défaut.
    maxHeapSize = "2g"
    // Captures d'écran (rendu Robolectric) : -PexitScreenshots=true.
    systemProperty("exit.screenshots", providers.gradleProperty("exitScreenshots").orNull ?: "false")
    systemProperty("exit.screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    // Miroir Maven optionnel pour les jars Android téléchargés par Robolectric.
    providers.gradleProperty("robolectricRepoUrl").orNull?.let { systemProperty("robolectric.dependency.repo.url", it) }
}

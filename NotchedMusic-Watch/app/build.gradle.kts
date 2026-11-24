plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "de.notchedmusic.watch"
  compileSdk = 37

  defaultConfig {
    // Die Wear Data Layer identifiziert Apps über Paketname + Signatur. Phone und Uhr müssen
    // deshalb denselben applicationId tragen; der Namespace bleibt de.notchedmusic.watch.
    applicationId = "de.notchedmusic.phone"
    // Zielgerät ist die TicWatch 5 Pro (Wear OS 5 = API 34). Bewusst einen Schritt tiefer:
    // 33 = Wear OS 4, damit auch der Wear-OS-4-Emulator die App installiert.
    minSdk = 33
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose (Foundation/UI aus dem BOM); Material-Design kommt von Wear Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Wear OS Material 3 + Foundation (AppScaffold, ScreenScaffold, …)
  implementation(libs.androidx.wear.compose.material3)
  implementation(libs.androidx.wear.compose.foundation)
  implementation(libs.androidx.wear.compose.ui.tooling)
  debugImplementation(libs.androidx.wear.tooling.preview)

  // Navigation (Navigation 3, Wear-Szenen inkl. Swipe-to-Dismiss)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.wear.compose.navigation3)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // MP3-Wiedergabe; DSP wird als AudioProcessor in die AudioSink-Pipeline gehängt
  implementation(libs.media3.common)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  // Filter-/DSP-Einstellungen: lokal speichern und per Data Layer von der Phone-App empfangen
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.datastore.preferences)
  implementation(libs.play.services.wearable)

  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}

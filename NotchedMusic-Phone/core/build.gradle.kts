plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  // JSON-Format für Presets und das v3-Sync-Paket: reine JVM-Abhängigkeit.
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  testImplementation(kotlin("test"))
  testImplementation(libs.kotlinx.coroutines.test)
}

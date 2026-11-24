// Wurzel-Build: nur Plugin-Deklarationen, die Module bringen ihre eigenen Abhängigkeiten mit.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

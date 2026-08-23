plugins {
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.spotless)
}

spotless {
  val ktLintVersion = libs.versions.kt.lint.get()
  val ktLintOptions =
    mapOf(
      "indent_size" to "2",
      "continuation_indent_size" to "2",
      "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    )

  kotlin {
    target("**/*.kt")
    targetExclude("**/build/")
    ktlint(ktLintVersion).editorConfigOverride(ktLintOptions)
    ktfmt().googleStyle()
    licenseHeaderFile(
      "${project.rootProject.projectDir}/license-header.txt",
      "^(package|//startfile)|import|class|object|sealed|open|interface|abstract",
    )
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    ktlint(ktLintVersion).editorConfigOverride(ktLintOptions)
    ktfmt().googleStyle()
  }
}

// Every module must resolve the SAME fhir-model as the catalog's local open-code-123 build —
// Gradle's qualifier ordering between "-open123-SNAPSHOT" and the "-rc0x" that fhir-path pulls
// transitively is otherwise ambiguous. Drop together with the catalog pin when rc03 ships.
allprojects {
  configurations.configureEach {
    resolutionStrategy {
      force(
        "dev.ohs.fhir:fhir-model:${libs.versions.ohs.fhir.model.get()}",
        "dev.ohs.fhir:fhir-model-r4:${libs.versions.ohs.fhir.model.get()}",
        "dev.ohs.fhir:fhir-model-r4b:${libs.versions.ohs.fhir.model.get()}",
        "dev.ohs.fhir:fhir-model-r5:${libs.versions.ohs.fhir.model.get()}",
      )
    }
  }
}

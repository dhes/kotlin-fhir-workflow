plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.ohs.fhir.workflow"
    compileSdk = 36
    minSdk = 26
  }

  jvm("desktop")
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  targets.configureEach {
    compilations.configureEach {
      compilerOptions.configure {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.ohs.fhir.model)
        implementation(libs.ohs.fhir.path)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.datetime)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotest.assertions.core)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
  }
}

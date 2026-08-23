import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "dev.ohs.fhir.chw.demo"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.ohs.fhir.chw.demo"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1"
  }

  buildTypes { getByName("release") { isMinifyEnabled = false } }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      // httpclient5 (transitive via the CQL engine) and friends each ship these.
      excludes += "/META-INF/DEPENDENCIES"
      excludes += "/META-INF/LICENSE*"
      excludes += "/META-INF/NOTICE*"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

// The CQL engine (compiled against antlr-kotlin 1.0.3) breaks on the 1.0.10 that
// fhir-path pulls in (binary-incompatible Interval accessors). This demo compiles CQL but
// never invokes the FHIRPath parser, so pin the engine's version. Revisit if a later phase
// evaluates FHIRPath expressions through :workflow.
configurations.configureEach {
  resolutionStrategy.force("com.strumenta:antlr-kotlin-runtime:1.0.3")
}

kotlin {
  jvmToolchain(21)

  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_21)
      optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
  }

  sourceSets {
    // Pinned to the PR #1815 branch build (KMP FHIR providers). Published to Sonatype
    // snapshots and mavenLocal; expect to advance this as engine v5 moves toward release.
    val cqlEngineVersion = "5.1.0-kmp-fhir-providers-84476e31-SNAPSHOT"
    androidMain.dependencies {
      implementation(project(":workflow"))
      implementation(libs.ohs.fhir.model)
      implementation("org.cqframework:engine:$cqlEngineVersion")
      implementation("org.cqframework:cql-to-elm:$cqlEngineVersion")
      implementation("org.cqframework:engine-fhir:$cqlEngineVersion")
      implementation(libs.kotlinx.serialization.json)
      implementation("org.slf4j:slf4j-api:2.0.16")
      runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.androidx.activity.compose)
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.ui)
    }
  }
}

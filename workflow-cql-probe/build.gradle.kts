// Probe only, not for upstream: a release (non-debuggable) app that times workflow-cql on WHO's
// immunization test cases at launch. WHO's files are packed from -PwhoDir=<dir with plandefinition/
// and package/>, never committed.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.application)
}

android {
  namespace = "dev.ohs.fhir.workflow.cql.probe"
  compileSdk = 36
  defaultConfig {
    applicationId = "dev.ohs.fhir.workflow.cql.probe"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = (findProperty("minify") as String?) == "true"
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  packaging {
    resources {
      excludes +=
        setOf(
          "META-INF/LICENSE*",
          "META-INF/NOTICE*",
          "META-INF/DEPENDENCIES",
          "META-INF/*.md",
          "META-INF/INDEX.LIST",
          "/META-INF/{AL2.0,LGPL2.1}",
        )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  (findProperty("whoDir") as String?)?.let { sourceSets.getByName("main").assets.srcDir(it) }
}

kotlin {
  jvmToolchain(21)
  androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
  sourceSets {
    androidMain {
      // -PfhirpathOnly=true: the same app without CQL, for the size baseline.
      val fhirpathOnly = (findProperty("fhirpathOnly") as String?) == "true"
      kotlin.srcDir(if (fhirpathOnly) "src/fhirpathOnly/kotlin" else "src/cql/kotlin")
      dependencies {
        if (fhirpathOnly) {
          implementation(project(":workflow"))
        } else {
          implementation(project(":workflow-cql"))
          implementation(libs.cqframework.engine)
          implementation(libs.cqframework.quick)
        }
        implementation(libs.ohs.fhir.model)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.datetime)
        implementation(libs.kotlinx.serialization.json)
      }
    }
  }
}

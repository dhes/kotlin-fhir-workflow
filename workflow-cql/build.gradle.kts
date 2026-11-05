plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.maven.publish)
}

val mavenGroupId: String by project
val mavenVersion: String by project

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.ohs.fhir.workflow.cql"
    compileSdk = 36
    minSdk = 26
    // Runs the desktop tests, WHO's test cases included, on an Android device or emulator.
    withDeviceTestBuilder { sourceSetTreeName = "test" }
      .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    packaging {
      resources {
        excludes +=
          setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
            "META-INF/*.md",
            "META-INF/INDEX.LIST",
          )
      }
    }
  }

  jvm("desktop")

  // No iOS targets: cqframework publishes no Kotlin/Native artifacts. No Wasm target yet: reading
  // kotlin-fhir choice values without reflection needs kotlin-fhir 1.0.0-rc04.

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
        api(project(":workflow"))
        implementation(libs.ohs.fhir.model)
        implementation(libs.cqframework.cql.to.elm)
        implementation(libs.cqframework.engine)
        implementation(libs.kotlinx.datetime)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotest.assertions.core)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    androidMain { dependencies { implementation(libs.cqframework.ucum) } }
    named("desktopMain") { dependencies { implementation(libs.cqframework.ucum) } }
    // The FHIR 4.0.1 model info and FHIRHelpers, read from cqframework's quick jar in the tests.
    named("desktopTest") { dependencies { implementation(libs.cqframework.quick) } }
    named("androidDeviceTest") {
      kotlin.srcDir("src/desktopTest/kotlin")
      dependencies {
        implementation(libs.cqframework.quick)
        implementation("androidx.test:runner:1.6.2")
        implementation("androidx.test.ext:junit:1.2.1")
      }
    }
  }
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(mavenGroupId, "fhir-workflow-cql", mavenVersion)

  pom {
    name = "Kotlin FHIR Workflow CQL"
    description = "CQL evaluation for kotlin-fhir-workflow's PlanDefinition/\$apply"
    inceptionYear = "2026"
    url = "https://github.com/ohs-foundation/kotlin-fhir-workflow"
    licenses {
      license {
        name = "The Apache License, Version 2.0"
        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
      }
    }
    developers {
      developer {
        id = "ohs-foundation"
        name = "Open Health Stack Foundation"
        url = "https://ohs.dev/"
      }
    }
    scm {
      url = "https://github.com/ohs-foundation/kotlin-fhir-workflow/"
      connection = "scm:git:git://github.com/ohs-foundation/kotlin-fhir-workflow.git"
      developerConnection = "scm:git:ssh://git@github.com/ohs-foundation/kotlin-fhir-workflow.git"
    }
  }
}

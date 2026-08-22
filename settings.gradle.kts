pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    // CQL engine v5 KMP snapshots (chw-demo): locally built PR #1815 artifacts first,
    // then the published snapshots.
    mavenLocal()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
  }
}

rootProject.name = "kotlin-fhir-workflow"

include(":workflow")

include(":workflow-demo")
include(":chw-demo")

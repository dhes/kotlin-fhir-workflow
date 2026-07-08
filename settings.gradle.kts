import java.util.Properties

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  val localProps = Properties().apply {
    rootDir.resolve("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
  }
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/LZRS/kotlin-fhir-engine")
      credentials {
        username = localProps.getProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
        password = localProps.getProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
      }
    }
  }
}

rootProject.name = "kotlin-fhir-workflow"
include(":workflow")
include(":workflow-demo")

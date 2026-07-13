package dev.ohs.fhir.workflow.demo.flow

/**
 * A selectable activity flow: the knowledge artifacts to install and the patient to run them
 * against. Mirrors android-fhir's workflow demo Configuration; the overflow menu picks one.
 */
data class DemoConfiguration(
  val id: String,
  val description: String,
  val patientId: String,
  val patientPath: String,
  val planDefinitionPath: String,
  val planDefinitionCanonical: String,
  val activityDefinitionPath: String,
)

/** "An apple a day" — the CPG example flow, same artifacts android-fhir's demo ships. */
val MEDICATION_DISPENSE = DemoConfiguration(
  id = "id_medication_dispense",
  description = "Apple a day",
  patientId = "active_apple_guy",
  patientPath = "files/patient/ActiveAppleGuy.json",
  planDefinitionPath = "files/pd/DailyAppleRecommendation.json",
  planDefinitionCanonical =
    "http://fhir.org/guides/cqf/cpg/example/PlanDefinition/DailyAppleRecommendation",
  activityDefinitionPath = "files/ad/DailyAppleActivity.json",
)

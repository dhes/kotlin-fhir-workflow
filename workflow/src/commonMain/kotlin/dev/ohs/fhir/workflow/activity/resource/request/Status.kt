package dev.ohs.fhir.workflow.activity.resource.request

sealed interface Status {
  data object DRAFT : Status
  data object ACTIVE : Status
  data object ONHOLD : Status
  data object REVOKED : Status
  data object COMPLETED : Status
  data object ENTEREDINERROR : Status
  class OTHER(val code: String?) : Status
}

package dev.ohs.fhir.workflow.activity.resource.event

sealed interface EventStatus {
  data object PREPARATION : EventStatus
  data object INPROGRESS : EventStatus
  data object NOTDONE : EventStatus
  data object ONHOLD : EventStatus
  data object COMPLETED : EventStatus
  data object ENTEREDINERROR : EventStatus
  data object STOPPED : EventStatus
  data object UNKNOWN : EventStatus
  class OTHER(val code: String?) : EventStatus
}

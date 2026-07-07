package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.*

interface EventStatusCodeMapper {
  fun mapCodeToStatus(code: String?): EventStatus
  fun mapStatusToCode(status: EventStatus): String?
}

open class EventStatusCodeMapperImpl : EventStatusCodeMapper {
  override fun mapCodeToStatus(code: String?): EventStatus = when (code) {
    "preparation" -> PREPARATION
    "in-progress" -> INPROGRESS
    "not-done" -> NOTDONE
    "on-hold" -> ONHOLD
    "completed" -> COMPLETED
    "entered-in-error" -> ENTEREDINERROR
    "stopped" -> STOPPED
    "unknown" -> UNKNOWN
    else -> OTHER(code)
  }

  override fun mapStatusToCode(status: EventStatus): String? = when (status) {
    PREPARATION -> "preparation"
    INPROGRESS -> "in-progress"
    NOTDONE -> "not-done"
    ONHOLD -> "on-hold"
    COMPLETED -> "completed"
    ENTEREDINERROR -> "entered-in-error"
    STOPPED -> "stopped"
    UNKNOWN -> "unknown"
    is OTHER -> status.code
  }
}

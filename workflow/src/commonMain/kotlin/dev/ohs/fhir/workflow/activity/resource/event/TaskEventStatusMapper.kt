package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.*

/**
 * EventStatus <-> Task status codes. A performed Task uses the Task status vocabulary, which lacks
 * `preparation`/`stopped`/`not-done`; map to the nearest Task states (`ready`/`cancelled`/`failed`).
 */
object TaskEventStatusMapper : EventStatusCodeMapper {
  override fun mapCodeToStatus(code: String?): EventStatus = when (code) {
    "draft", "requested", "received", "accepted", "ready" -> PREPARATION
    "in-progress" -> INPROGRESS
    "on-hold" -> ONHOLD
    "completed" -> COMPLETED
    "cancelled", "rejected" -> STOPPED
    "failed" -> NOTDONE
    "entered-in-error" -> ENTEREDINERROR
    else -> OTHER(code)
  }

  override fun mapStatusToCode(status: EventStatus): String? = when (status) {
    PREPARATION -> "ready"
    INPROGRESS -> "in-progress"
    NOTDONE -> "failed"
    ONHOLD -> "on-hold"
    COMPLETED -> "completed"
    STOPPED -> "cancelled"
    ENTEREDINERROR -> "entered-in-error"
    UNKNOWN -> "ready"
    is OTHER -> status.code
  }
}

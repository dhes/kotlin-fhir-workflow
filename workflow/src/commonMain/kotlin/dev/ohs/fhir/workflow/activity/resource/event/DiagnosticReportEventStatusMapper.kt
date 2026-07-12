package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.*

/**
 * EventStatus <-> DiagnosticReport status codes. A report event uses the diagnostic-report-status
 * vocabulary (registered/partial/final/…), which lacks the event pattern's `preparation`/`stopped`.
 */
object DiagnosticReportEventStatusMapper : EventStatusCodeMapper {
  override fun mapCodeToStatus(code: String?): EventStatus = when (code) {
    "registered" -> PREPARATION
    "partial", "preliminary" -> INPROGRESS
    "final", "amended", "corrected", "appended" -> COMPLETED
    "cancelled" -> STOPPED
    "entered-in-error" -> ENTEREDINERROR
    "unknown" -> UNKNOWN
    else -> OTHER(code)
  }

  override fun mapStatusToCode(status: EventStatus): String? = when (status) {
    PREPARATION -> "registered"
    INPROGRESS -> "partial"
    NOTDONE -> "cancelled"
    ONHOLD -> "registered"
    COMPLETED -> "final"
    STOPPED -> "cancelled"
    ENTEREDINERROR -> "entered-in-error"
    UNKNOWN -> "unknown"
    is OTHER -> status.code
  }
}

/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.COMPLETED
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.ENTEREDINERROR
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.INPROGRESS
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.NOTDONE
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.ONHOLD
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.OTHER
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.PREPARATION
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.STOPPED
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.UNKNOWN

/**
 * EventStatus <-> DiagnosticReport status codes. A report event uses the diagnostic-report-status
 * vocabulary (registered/partial/final/…), which lacks the event pattern's `preparation`/`stopped`.
 */
object DiagnosticReportEventStatusMapper : EventStatusCodeMapper {
  override fun mapCodeToStatus(code: String?): EventStatus =
    when (code) {
      "registered" -> PREPARATION

      "partial",
      "preliminary" -> INPROGRESS

      "final",
      "amended",
      "corrected",
      "appended" -> COMPLETED

      "cancelled" -> STOPPED

      "entered-in-error" -> ENTEREDINERROR

      "unknown" -> UNKNOWN

      else -> OTHER(code)
    }

  override fun mapStatusToCode(status: EventStatus): String? =
    when (status) {
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

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

import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.DiagnosticReport
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import kotlin.uuid.Uuid

/**
 * The perform-phase event for a [ServiceRequest][dev.ohs.fhir.model.r4.ServiceRequest] flow — a
 * [DiagnosticReport] `basedOn` the request, carrying the order's code/subject. Uses
 * [DiagnosticReportEventStatusMapper] (report status vocabulary differs from the event pattern).
 */
class CPGServiceReportEvent(resource: DiagnosticReport) :
  CPGEventResource<DiagnosticReport>(DiagnosticReportEventStatusMapper) {

  override var resource: DiagnosticReport = resource

  override fun setStatus(status: EventStatus, reason: String?) {
    resource =
      resource.copy(
        status =
          Enumeration(
            value =
              DiagnosticReport.DiagnosticReportStatus.fromCode(
                mapper.mapStatusToCode(status) ?: "registered"
              )
          )
      )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()

  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(basedOn = resource.basedOn + reference)
  }

  override fun getBasedOn(): Reference? = resource.basedOn.lastOrNull()

  override fun copy(): CPGEventResource<DiagnosticReport> = CPGServiceReportEvent(resource.copy())

  companion object {
    fun from(request: CPGServiceRequest): CPGServiceReportEvent {
      val src = request.resource
      return CPGServiceReportEvent(
        DiagnosticReport(
          id = Uuid.random().toString(),
          status = Enumeration(value = DiagnosticReport.DiagnosticReportStatus.Registered),
          code = src.code ?: CodeableConcept(),
          subject = src.subject,
          basedOn = listOf(request.asReference()),
        )
      )
    }
  }
}

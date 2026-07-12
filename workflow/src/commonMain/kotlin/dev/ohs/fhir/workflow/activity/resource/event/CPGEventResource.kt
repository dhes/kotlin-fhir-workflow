package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Communication
import dev.ohs.fhir.model.r4.DiagnosticReport
import dev.ohs.fhir.model.r4.MedicationDispense
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.logicalId
import dev.ohs.fhir.workflow.resourceTypeName

/**
 * A wrapper around an event-type resource (Communication, MedicationDispense) that exposes the
 * event pattern (status/basedOn) uniformly. The wrapped resource is immutable, so every setter
 * reassigns [resource] via `.copy(...)`.
 */
sealed class CPGEventResource<R : Resource>(internal val mapper: EventStatusCodeMapper) {
  abstract var resource: R
    protected set

  val resourceType: String get() = resource.resourceTypeName()
  val logicalId: String? get() = resource.logicalId

  abstract fun setStatus(status: EventStatus, reason: String? = null)
  fun getStatus(): EventStatus = mapper.mapCodeToStatus(getStatusCode())
  abstract fun getStatusCode(): String?
  abstract fun setBasedOn(reference: Reference)
  abstract fun getBasedOn(): Reference?
  abstract fun copy(): CPGEventResource<R>

  companion object {
    internal fun from(from: CPGRequestResource<*>, eventClassName: String): CPGEventResource<*> =
      when (from) {
        is CPGCommunicationRequest -> CPGCommunicationEvent.from(from)
        is CPGMedicationRequest -> CPGOrderMedicationEvent.from(from, eventClassName)
        is CPGTaskRequest -> CPGTaskEvent.from(from)
        is CPGServiceRequest -> CPGServiceReportEvent.from(from)
      }

    fun of(event: Resource): CPGEventResource<*> = when (event) {
      is Communication -> CPGCommunicationEvent(event)
      is MedicationDispense -> CPGMedicationDispenseEvent(event)
      is Task -> CPGTaskEvent(event)
      is DiagnosticReport -> CPGServiceReportEvent(event)
      else -> throw IllegalArgumentException("Unknown CPG event type ${event::class}.")
    }
  }
}

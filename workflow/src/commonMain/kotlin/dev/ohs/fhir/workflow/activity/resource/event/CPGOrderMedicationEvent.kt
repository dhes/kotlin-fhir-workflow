package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest

abstract class CPGOrderMedicationEvent<R : Resource>(
  mapper: EventStatusCodeMapper,
) : CPGEventResource<R>(mapper) {
  companion object {
    fun from(request: CPGMedicationRequest, eventClassName: String): CPGEventResource<*> =
      when (eventClassName) {
        "CPGMedicationDispenseEvent" -> CPGMedicationDispenseEvent.from(request)
        else -> throw IllegalArgumentException("Unknown Event type $eventClassName")
      }
  }
}

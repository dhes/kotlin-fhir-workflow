package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Communication
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import kotlin.uuid.Uuid

class CPGCommunicationEvent(resource: Communication) : CPGEventResource<Communication>(EventStatusCodeMapperImpl()) {

  override var resource: Communication = resource

  override fun setStatus(status: EventStatus, reason: String?) {
    resource = resource.copy(
      status = Enumeration(value = Communication.EventStatus.fromCode(mapper.mapStatusToCode(status) ?: "unknown")),
      statusReason = reason?.let { CodeableConcept(coding = listOf(Coding(code = Code(value = it)))) },
    )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()
  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(basedOn = resource.basedOn + reference)
  }
  override fun getBasedOn(): Reference? = resource.basedOn.lastOrNull()
  override fun copy(): CPGEventResource<Communication> = CPGCommunicationEvent(resource.copy())

  companion object {
    fun from(request: CPGCommunicationRequest): CPGCommunicationEvent {
      val src = request.resource
      return CPGCommunicationEvent(
        Communication(
          id = Uuid.random().toString(),
          status = Enumeration(value = Communication.EventStatus.Preparation),
          category = src.category,
          medium = src.medium,
          subject = src.subject,
          about = src.about,
          encounter = src.encounter,
          recipient = src.recipient,
          sender = src.sender,
          reasonCode = src.reasonCode,
          reasonReference = src.reasonReference,
        ),
      )
    }
  }
}

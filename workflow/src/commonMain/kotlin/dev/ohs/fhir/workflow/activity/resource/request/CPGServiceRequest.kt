package dev.ohs.fhir.workflow.activity.resource.request

import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest

/** CPG request wrapper for a lab/service order ([ServiceRequest]); status uses the request pattern. */
class CPGServiceRequest(resource: ServiceRequest) :
  CPGRequestResource<ServiceRequest>(StatusCodeMapperImpl()) {

  override var resource: ServiceRequest = resource

  override fun setId(id: String) {
    resource = resource.copy(id = id)
  }

  override fun setIntent(intent: Intent) {
    resource = resource.copy(intent = Enumeration(value = ServiceRequest.RequestIntent.fromCode(intent.code ?: "order")))
  }

  override fun getIntent(): Intent = Intent.of(resource.intent.value?.getCode())

  override fun setStatus(status: Status, reason: String?) {
    resource = resource.copy(
      status = Enumeration(value = ServiceRequest.RequestStatus.fromCode(mapper.mapStatusToCode(status) ?: "unknown")),
    )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()

  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(basedOn = resource.basedOn + reference)
  }

  override fun getBasedOn(): Reference? = resource.basedOn.lastOrNull()

  override fun copy(): CPGRequestResource<ServiceRequest> = CPGServiceRequest(resource.copy())
}

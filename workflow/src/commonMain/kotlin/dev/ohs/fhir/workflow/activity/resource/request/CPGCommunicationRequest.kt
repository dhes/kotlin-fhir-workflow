package dev.ohs.fhir.workflow.activity.resource.request

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString

private const val REQUEST_INTENT_URL = "http://hl7.org/fhir/StructureDefinition/request-intent"

class CPGCommunicationRequest(resource: CommunicationRequest) :
  CPGRequestResource<CommunicationRequest>(StatusCodeMapperImpl()) {

  override var resource: CommunicationRequest = resource

  override fun setId(id: String) {
    resource = resource.copy(id = id)
  }

  override fun setIntent(intent: Intent) {
    val others = resource.extension.filterNot { it.url == REQUEST_INTENT_URL }
    val intentExt = Extension(
      url = REQUEST_INTENT_URL,
      value = Extension.Value.String(FhirString(value = intent.code)),
    )
    resource = resource.copy(extension = others + intentExt)
  }

  override fun getIntent(): Intent {
    val ext = resource.extension.firstOrNull { it.url == REQUEST_INTENT_URL }
    val code = (ext?.value as? Extension.Value.String)?.value?.value
    return Intent.of(code)
  }

  override fun setStatus(status: Status, reason: String?) {
    resource = resource.copy(
      status = Enumeration(
        value = CommunicationRequest.RequestStatus.fromCode(
          mapper.mapStatusToCode(status) ?: "unknown",
        ),
      ),
      statusReason = reason?.let { CodeableConcept(coding = listOf(Coding(code = Code(value = it)))) },
    )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()

  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(basedOn = resource.basedOn + reference)
  }

  override fun getBasedOn(): Reference? = resource.basedOn.lastOrNull()

  override fun copy(): CPGRequestResource<CommunicationRequest> =
    CPGCommunicationRequest(resource.copy())
}

package dev.ohs.fhir.workflow

import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString

internal fun fhirString(value: String?): FhirString? = value?.let { FhirString(value = it) }

internal fun reference(ref: String): Reference = Reference(reference = FhirString(value = ref))

internal val Reference.ref: String?
  get() = reference?.value

/** Resource type name as used in references, e.g. "CommunicationRequest". */
internal fun Resource.resourceTypeName(): String = this::class.simpleName ?: error("no type")

internal val Resource.logicalId: String?
  get() = id

package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.Address
import dev.ohs.fhir.model.r4.Annotation
import dev.ohs.fhir.model.r4.Attachment
import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Id
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Markdown
import dev.ohs.fhir.model.r4.Oid
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.PositiveInt
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Time
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.Url
import dev.ohs.fhir.model.r4.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val elementJson = Json { encodeDefaults = false; explicitNulls = false }

/**
 * Converts an evaluated FHIRPath result into a [JsonElement] for dynamicValue write-back.
 *
 * The `when` dispatches per FHIR datatype rather than resolving a serializer generically because KMP
 * has no reflection to do `value::class.serializer()` on wasm/native. The datatype set is finite, so
 * this is bounded, mechanical boilerplate, kept self-contained so the library depends only on
 * `fhir-model` + `fhir-path`. An unsupported result type throws rather than being silently coerced.
 */
internal fun evaluatedValueToJson(value: Any): JsonElement =
  primitiveOrNull(value)
    ?: quantityOrNull(value)
    ?: structuredOrNull(value)
    ?: throw IllegalStateException("Unsupported dynamicValue result type ${value::class.simpleName}")

private fun primitiveOrNull(value: Any): JsonElement? =
  when (value) {
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is FhirPathDate -> JsonPrimitive(value.toString())
    is FhirPathDateTime -> JsonPrimitive(value.toString())
    is FhirPathTime -> JsonPrimitive(value.toString())
    is FhirString -> JsonPrimitive(value.value)
    is FhirBoolean -> JsonPrimitive(value.value)
    is Integer -> JsonPrimitive(value.value)
    is PositiveInt -> JsonPrimitive(value.value)
    is Decimal -> JsonPrimitive(value.value?.toString())
    is Date -> JsonPrimitive(value.value?.toString())
    is DateTime -> JsonPrimitive(value.value?.toString())
    is Time -> JsonPrimitive(value.value?.toString())
    is Uri -> JsonPrimitive(value.value)
    is Url -> JsonPrimitive(value.value)
    is Canonical -> JsonPrimitive(value.value)
    is Code -> JsonPrimitive(value.value)
    is Markdown -> JsonPrimitive(value.value)
    is Id -> JsonPrimitive(value.value)
    is Oid -> JsonPrimitive(value.value)
    is Uuid -> JsonPrimitive(value.value)
    else -> null
  }

private fun quantityOrNull(value: Any): JsonElement? =
  when (value) {
    is FhirPathQuantity ->
      buildJsonObject {
        value.value?.let { put("value", JsonPrimitive(it.toString())) }
        value.unit?.let {
          put("code", JsonPrimitive(it))
          put("unit", JsonPrimitive(it))
        }
      }
    else -> null
  }

private fun structuredOrNull(value: Any): JsonElement? =
  when (value) {
    is Quantity -> elementJson.encodeToJsonElement(Quantity.serializer(), value)
    is Coding -> elementJson.encodeToJsonElement(Coding.serializer(), value)
    is CodeableConcept -> elementJson.encodeToJsonElement(CodeableConcept.serializer(), value)
    is Reference -> elementJson.encodeToJsonElement(Reference.serializer(), value)
    is Attachment -> elementJson.encodeToJsonElement(Attachment.serializer(), value)
    is Identifier -> elementJson.encodeToJsonElement(Identifier.serializer(), value)
    is HumanName -> elementJson.encodeToJsonElement(HumanName.serializer(), value)
    is Address -> elementJson.encodeToJsonElement(Address.serializer(), value)
    is ContactPoint -> elementJson.encodeToJsonElement(ContactPoint.serializer(), value)
    is Period -> elementJson.encodeToJsonElement(Period.serializer(), value)
    is Annotation -> elementJson.encodeToJsonElement(Annotation.serializer(), value)
    is Resource -> elementJson.encodeToJsonElement(Resource.serializer(), value)
    else -> null
  }

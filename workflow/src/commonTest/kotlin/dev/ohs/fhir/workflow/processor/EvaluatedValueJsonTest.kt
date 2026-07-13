package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.String as FhirString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EvaluatedValueJsonTest {
  @Test
  fun shouldConvertKotlinStringWhenPrimitive() {
    assertEquals(JsonPrimitive("routine"), evaluatedValueToJson("routine"))
  }

  @Test
  fun shouldConvertCodeableConceptWhenComplex() {
    val cc = CodeableConcept(coding = listOf(Coding(code = Code(value = "BCG"))))
    val json = evaluatedValueToJson(cc)
    assertTrue(json is JsonObject)
    val code =
      (json as JsonObject)["coding"]!!.jsonArray.first().jsonObject["code"]!!.jsonPrimitive.content
    assertEquals("BCG", code)
  }

  @Test
  fun shouldThrowWhenUnsupportedType() {
    assertFailsWith<IllegalStateException> { evaluatedValueToJson(FhirString(value = "x") to 1) }
  }
}

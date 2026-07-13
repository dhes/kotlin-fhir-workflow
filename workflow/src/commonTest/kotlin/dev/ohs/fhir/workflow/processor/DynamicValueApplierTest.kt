package dev.ohs.fhir.workflow.processor

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicValueApplierTest {
  private val empty = buildJsonObject {}

  @Test
  fun shouldSetTopLevelFieldWhenSimplePath() {
    val result = DynamicValueApplier.set(empty, "priority", JsonPrimitive("routine"))
    assertEquals(buildJsonObject { put("priority", "routine") }, result)
  }

  @Test
  fun shouldStripResourceTypePrefixWhenPresent() {
    val result = DynamicValueApplier.set(empty, "MedicationRequest.priority", JsonPrimitive("routine"))
    assertEquals(buildJsonObject { put("priority", "routine") }, result)
  }

  @Test
  fun shouldCreateIntermediatesWhenNestedIndexedPath() {
    val result =
      DynamicValueApplier.set(empty, "dosageInstruction[0].timing.repeat.frequency", JsonPrimitive(1))
    val expected = buildJsonObject {
      putJsonArray("dosageInstruction") {
        add(buildJsonObject {
          putJsonObject("timing") { putJsonObject("repeat") { put("frequency", 1) } }
        })
      }
    }
    assertEquals(expected, result)
  }

  @Test
  fun shouldOverwriteExistingValueWhenPathPresent() {
    val start = buildJsonObject { put("priority", "stat") }
    val result = DynamicValueApplier.set(start, "priority", JsonPrimitive("routine"))
    assertEquals(buildJsonObject { put("priority", "routine") }, result)
  }
}

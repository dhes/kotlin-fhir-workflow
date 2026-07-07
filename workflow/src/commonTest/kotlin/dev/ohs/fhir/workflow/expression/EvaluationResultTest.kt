package dev.ohs.fhir.workflow.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvaluationResultTest {
  @Test
  fun `asBoolean returns value for Bool and null otherwise`() {
    assertEquals(true, EvaluationResult.Bool(true).asBoolean())
    assertNull(EvaluationResult.Failure("x").asBoolean())
    assertNull(EvaluationResult.Values(listOf(1)).asBoolean())
  }
}

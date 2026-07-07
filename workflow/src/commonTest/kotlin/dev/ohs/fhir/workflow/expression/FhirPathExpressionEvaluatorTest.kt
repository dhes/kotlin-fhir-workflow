package dev.ohs.fhir.workflow.expression

import dev.ohs.fhir.model.r4.Patient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FhirPathExpressionEvaluatorTest {
  private val evaluator = FhirPathExpressionEvaluator()
  private val ctx = EvaluationContext(subject = Patient(id = "p1", active = boolTrue()), today = LocalDate(2026, 7, 7))

  @Test
  fun `evaluates a true boolean condition`() = runTest {
    val r = evaluator.evaluate(ProtocolExpression.FhirPath("Patient.active = true"), ctx)
    assertEquals(true, r.asBoolean())
  }

  @Test
  fun `evaluates a false boolean condition`() = runTest {
    val r = evaluator.evaluate(ProtocolExpression.FhirPath("Patient.active = false"), ctx)
    assertEquals(false, r.asBoolean())
  }

  private fun boolTrue() = dev.ohs.fhir.model.r4.Boolean(value = true)
}

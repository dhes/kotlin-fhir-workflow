package dev.ohs.fhir.workflow.expression

import dev.ohs.fhir.model.r4.Patient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExpressionEvaluatorRouterTest {
  private val router = ExpressionEvaluatorRouter()
  private val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))

  @Test
  fun `routes fhirpath to the fhirpath evaluator`() = runTest {
    val r = router.evaluate(ProtocolExpression.FhirPath("Patient.id = 'p1'"), ctx)
    assertTrue(r is EvaluationResult.Bool)
  }

  @Test
  fun shouldThrowNotImplementedWhenElmExpression() = runTest {
    assertFailsWith<NotImplementedError> {
      router.evaluate(ProtocolExpression.Elm("{}"), ctx)
    }
  }
}

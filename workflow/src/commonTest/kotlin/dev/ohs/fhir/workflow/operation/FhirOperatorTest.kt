package dev.ohs.fhir.workflow.operation

import dev.ohs.fhir.model.r4.*
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FhirOperatorTest {
  @Test
  fun `generateCarePlan applies a plan definition`() = runTest {
    val pd = PlanDefinition(
      id = "epi",
      status = Enumeration(value = PublicationStatus.Active),
      action = listOf(
        PlanDefinition.Action(
          id = "bcg",
          title = FhirString(value = "BCG"),
          condition = listOf(
            PlanDefinition.Action.Condition(
              kind = Enumeration(value = PlanDefinition.ActionConditionKind.Applicability),
              expression = Expression(
                language = Enumeration(value = Expression.ExpressionLanguage.Text_Fhirpath),
                expression = FhirString(value = "Patient.active = true"),
              ),
            ),
          ),
        ),
      ),
    )
    val operator = FhirOperator(InMemoryWorkflowRepository())
    val carePlan = operator.generateCarePlan(
      planDefinition = pd,
      subject = Patient(id = "p1", active = dev.ohs.fhir.model.r4.Boolean(value = true)),
      today = LocalDate(2026, 7, 7),
    )
    assertEquals(1, carePlan.contained.filterIsInstance<RequestGroup>().single().action.size)
  }

  @Test
  fun `evaluateMeasure and evaluateLibrary are unsupported`() {
    val operator = FhirOperator(InMemoryWorkflowRepository())
    assertFailsWith<WorkflowOperationNotSupportedException> { operator.evaluateMeasure("Measure/x") }
    assertFailsWith<WorkflowOperationNotSupportedException> { operator.evaluateLibrary("Library/x") }
  }
}

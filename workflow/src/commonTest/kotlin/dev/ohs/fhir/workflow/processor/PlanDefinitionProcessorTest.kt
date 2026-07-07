package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.model.r4.*
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import dev.ohs.fhir.workflow.knowledge.CanonicalResolver
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanDefinitionProcessorTest {
  @Test
  fun `applicable action yields a request group action`() = runTest {
    // ActivityDefinition to resolve
    val adUrl = "http://example.org/ActivityDefinition/bcg"
    val repo = InMemoryWorkflowRepository().apply {
      registerUriIndex("ActivityDefinition", "url") { listOf((it as ActivityDefinition).url?.value ?: "") }
      create(
        ActivityDefinition(
          id = "bcg",
          url = Uri(value = adUrl),
          status = Enumeration(value = PublicationStatus.Active),
          kind = Enumeration(value = ActivityDefinition.RequestResourceType.ServiceRequest),
          title = FhirString(value = "BCG vaccine"),
        ),
      )
    }
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
          definition = PlanDefinition.Action.Definition.Canonical(Canonical(value = adUrl)),
        ),
      ),
    )
    val processor = PlanDefinitionProcessor(ExpressionEvaluatorRouter(), CanonicalResolver(repo))
    val ctx = EvaluationContext(
      subject = Patient(id = "p1", active = dev.ohs.fhir.model.r4.Boolean(value = true)),
      today = LocalDate(2026, 7, 7),
    )
    val carePlan = processor.apply(pd, ctx)

    val requestGroup = carePlan.contained.filterIsInstance<RequestGroup>().single()
    assertEquals(1, requestGroup.action.size)
    assertEquals("BCG", requestGroup.action.single().title?.value)
  }

  @Test
  fun `non-applicable action is omitted`() = runTest {
    val repo = InMemoryWorkflowRepository()
    val pd = PlanDefinition(
      id = "epi",
      status = Enumeration(value = PublicationStatus.Active),
      action = listOf(
        PlanDefinition.Action(
          id = "bcg",
          condition = listOf(
            PlanDefinition.Action.Condition(
              kind = Enumeration(value = PlanDefinition.ActionConditionKind.Applicability),
              expression = Expression(
                language = Enumeration(value = Expression.ExpressionLanguage.Text_Fhirpath),
                expression = FhirString(value = "Patient.active = false"),
              ),
            ),
          ),
        ),
      ),
    )
    val processor = PlanDefinitionProcessor(ExpressionEvaluatorRouter(), CanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1", active = dev.ohs.fhir.model.r4.Boolean(value = true)), today = LocalDate(2026, 7, 7))
    val carePlan = processor.apply(pd, ctx)
    assertEquals(0, carePlan.contained.filterIsInstance<RequestGroup>().single().action.size)
  }
}

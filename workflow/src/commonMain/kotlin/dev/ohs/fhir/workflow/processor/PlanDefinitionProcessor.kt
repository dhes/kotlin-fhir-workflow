package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.model.r4.CarePlan
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.ExpressionEvaluator
import dev.ohs.fhir.workflow.expression.ProtocolExpression
import dev.ohs.fhir.workflow.knowledge.CanonicalResolver
import dev.ohs.fhir.workflow.resourceTypeName

/**
 * FHIRPath-based `PlanDefinition/$apply`. Composes an [ExpressionEvaluator] to check
 * action applicability and a [CanonicalResolver] to resolve action titles from referenced
 * ActivityDefinitions. Produces a [CarePlan] carrying a contained [RequestGroup] whose
 * actions mirror the applicable PlanDefinition actions.
 */
class PlanDefinitionProcessor(
  private val evaluator: ExpressionEvaluator,
  private val resolver: CanonicalResolver,
) {
  suspend fun apply(planDefinition: PlanDefinition, context: EvaluationContext): CarePlan {
    val applicableActionIds = mutableSetOf<String>()
    val groupActions = mutableListOf<RequestGroup.Action>()

    for (action in planDefinition.action) {
      // relatedAction: skip if a prerequisite action wasn't applicable this run. This is an
      // order-sensitive, relationship-agnostic gate: it only sees prerequisites declared
      // earlier in `action` and ignores the actual relationship semantics (before/after/etc.).
      // A prerequisite declared after its dependent is silently treated as unmet. Full
      // relatedAction semantics are a follow-up.
      val prerequisitesMet = action.relatedAction.all { rel ->
        applicableActionIds.contains(rel.actionId.value)
      }
      if (!prerequisitesMet) continue

      if (!isApplicable(action, context)) continue
      action.id?.let { applicableActionIds.add(it) }

      val title = action.title ?: resolveTitle(action)
      groupActions.add(
        RequestGroup.Action(
          id = action.id,
          title = title,
          description = action.description,
        ),
      )
    }

    val requestGroup = RequestGroup(
      id = "rg-${planDefinition.id}",
      status = Enumeration(value = RequestGroup.RequestStatus.Active),
      intent = Enumeration(value = RequestGroup.RequestIntent.Proposal),
      action = groupActions,
    )

    return CarePlan(
      id = "careplan-${planDefinition.id}",
      status = Enumeration(value = CarePlan.RequestStatus.Active),
      intent = Enumeration(value = CarePlan.CarePlanIntent.Plan),
      subject = subjectReference(context),
      contained = listOf(requestGroup),
      activity = if (groupActions.isEmpty()) {
        emptyList()
      } else {
        listOf(CarePlan.Activity(reference = Reference(reference = FhirString(value = "#${requestGroup.id}"))))
      },
    )
  }

  private suspend fun isApplicable(action: PlanDefinition.Action, context: EvaluationContext): Boolean {
    val applicabilityConditions = action.condition.filter {
      it.kind.value == PlanDefinition.ActionConditionKind.Applicability
    }
    if (applicabilityConditions.isEmpty()) return true
    // A Failure or non-boolean evaluation result is treated as `false` (silent skip); surfacing
    // eval failures to the consumer is deferred to a follow-up.
    return applicabilityConditions.all { condition ->
      val expr = condition.expression ?: return@all false
      if (expr.language.value != Expression.ExpressionLanguage.Text_Fhirpath) return@all false
      val fhirPath = expr.expression?.value ?: return@all false
      evaluator.evaluate(ProtocolExpression.FhirPath(fhirPath), context).asBoolean() ?: false
    }
  }

  private suspend fun resolveTitle(action: PlanDefinition.Action): FhirString? {
    val canonical = action.definition?.asCanonical()?.value?.value ?: return null
    return resolver.resolveActivityDefinition(canonical)?.title
  }

  private fun subjectReference(context: EvaluationContext): Reference {
    val id = context.subject.id ?: "unknown"
    return Reference(reference = FhirString(value = "${context.subject.resourceTypeName()}/$id"))
  }
}

package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.model.r4.CarePlan
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
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
    val tasks = mutableListOf<Task>()
    val groupActions = processActions(planDefinition, planDefinition.action, context, tasks)

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
      contained = listOf(requestGroup) + tasks,
      activity = if (groupActions.isEmpty()) {
        emptyList()
      } else {
        listOf(CarePlan.Activity(reference = Reference(reference = FhirString(value = "#${requestGroup.id}"))))
      },
    )
  }

  /**
   * Processes a sibling list of actions into [RequestGroup.Action]s, recursing into nested
   * `action.action` (e.g. an ANC contact bundling sub-activities). `relatedAction` prerequisites are
   * gated within the sibling level. Each applicable action with a resolvable
   * [ActivityDefinition][dev.ohs.fhir.model.r4.ActivityDefinition] instantiates a [Task] (added to
   * [tasks]) referenced from the emitted action; group actions carry their processed children.
   */
  private suspend fun processActions(
    planDefinition: PlanDefinition,
    actions: List<PlanDefinition.Action>,
    context: EvaluationContext,
    tasks: MutableList<Task>,
  ): List<RequestGroup.Action> {
    val applicableActionIds = mutableSetOf<String>()
    val groupActions = mutableListOf<RequestGroup.Action>()
    for (action in actions) {
      val prerequisitesMet =
        action.relatedAction.all { rel -> applicableActionIds.contains(rel.actionId.value) }
      if (!prerequisitesMet) continue
      if (!isApplicable(action, context)) continue
      action.id?.let { applicableActionIds.add(it) }

      val title = action.title ?: resolveTitle(action)
      val task = instantiateTask(planDefinition, action, context)
      task?.let { tasks.add(it) }
      val children = processActions(planDefinition, action.action, context, tasks)
      groupActions.add(
        RequestGroup.Action(
          id = action.id,
          title = title,
          description = action.description,
          extension = action.extension,
          resource = task?.let { Reference(reference = FhirString(value = "#${it.id}")) },
          action = children,
        ),
      )
    }
    return groupActions
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

  /**
   * Instantiates a concrete [Task] from the action's referenced [ActivityDefinition][dev.ohs.fhir.model.r4.ActivityDefinition],
   * copying its static `code`/`description`. `dynamicValue` write-back is not yet applied. Returns
   * null when the action has no resolvable definition.
   */
  private suspend fun instantiateTask(
    planDefinition: PlanDefinition,
    action: PlanDefinition.Action,
    context: EvaluationContext,
  ): Task? {
    val canonical = action.definition?.asCanonical()?.value?.value ?: return null
    val activityDefinition = resolver.resolveActivityDefinition(canonical) ?: return null
    return Task(
      id = "task-${planDefinition.id}-${action.id ?: activityDefinition.id}",
      status = Enumeration(value = Task.TaskStatus.Requested),
      intent = Enumeration(value = Task.TaskIntent.Order),
      code = activityDefinition.code,
      description = action.description ?: activityDefinition.description,
      `for` = subjectReference(context),
      basedOn = listOf(Reference(reference = FhirString(value = "#rg-${planDefinition.id}"))),
    )
  }

  private fun subjectReference(context: EvaluationContext): Reference {
    val id = context.subject.id ?: "unknown"
    return Reference(reference = FhirString(value = "${context.subject.resourceTypeName()}/$id"))
  }
}

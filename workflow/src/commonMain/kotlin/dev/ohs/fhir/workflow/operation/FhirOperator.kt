/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.workflow.operation

import dev.ohs.fhir.model.r4.CarePlan
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.ExpressionEvaluator
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import dev.ohs.fhir.workflow.knowledge.CanonicalResolver
import dev.ohs.fhir.workflow.processor.PlanDefinitionProcessor
import dev.ohs.fhir.workflow.repository.WorkflowRepository
import kotlinx.datetime.LocalDate

/**
 * Parity facade for android-fhir's FhirOperator. generateCarePlan runs FHIRPath $apply;
 * evaluateMeasure/evaluateLibrary are CQL-only and remain unsupported (server-side).
 */
class FhirOperator(
  private val repository: WorkflowRepository,
  evaluator: ExpressionEvaluator = ExpressionEvaluatorRouter(),
) {
  private val resolver = CanonicalResolver(repository)
  private val processor = PlanDefinitionProcessor(evaluator, resolver)

  suspend fun generateCarePlan(
    planDefinition: PlanDefinition,
    subject: Resource,
    variables: Map<String, Any?> = emptyMap(),
    today: LocalDate,
  ): CarePlan =
    processor.apply(
      planDefinition,
      EvaluationContext(subject = subject, variables = variables, today = today),
    )

  suspend fun generateCarePlan(
    planDefinitionCanonical: String,
    subject: Resource,
    variables: Map<String, Any?> = emptyMap(),
    today: LocalDate,
  ): CarePlan {
    val pd =
      resolver.resolvePlanDefinition(planDefinitionCanonical)
        ?: throw IllegalArgumentException("PlanDefinition not found: $planDefinitionCanonical")
    return generateCarePlan(pd, subject, variables, today)
  }

  fun evaluateMeasure(measureUrl: String): Nothing =
    UnsupportedOperations.evaluateMeasure(measureUrl)

  fun evaluateLibrary(libraryUrl: String): Nothing =
    UnsupportedOperations.evaluateLibrary(libraryUrl)
}

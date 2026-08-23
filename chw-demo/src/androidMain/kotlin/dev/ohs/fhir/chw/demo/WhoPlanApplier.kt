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
package dev.ohs.fhir.chw.demo

import android.content.res.AssetManager
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.EvaluationResult
import dev.ohs.fhir.workflow.expression.ExpressionEvaluator
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import dev.ohs.fhir.workflow.expression.ProtocolExpression
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * Applies WHO's published PlanDefinition (IMMZ.D2.DT.Measles, ongoing transmission) with the
 * :workflow module's $apply, evaluating its text/cql-identifier conditions through the on-device
 * CQL engine. The generated MedicationRequest proposal — WHO's own ActivityDefinition IMMZD2DTMR
 * is kind MedicationRequest / intent proposal — seeds the ActivityFlow cycle.
 *
 * Requires kotlin-fhir >= rc03 semantics for Expression.language (ohs-foundation/kotlin-fhir#123
 * / PR #128): this branch runs against a local open-code-123 build of fhir-model.
 */
class WhoPlanApplier(assets: AssetManager, private val engine: MeaslesEngine) {

  private val knowledge: Map<String, Resource> =
    assets.list("knowledge")!!.associate { name ->
      val resource =
        fhirJson.decodeFromString(
          Resource.serializer(),
          assets.open("knowledge/$name").bufferedReader().readText(),
        )
      val url =
        when (resource) {
          is dev.ohs.fhir.model.r4.PlanDefinition -> resource.url?.value
          is dev.ohs.fhir.model.r4.ActivityDefinition -> resource.url?.value
          else -> null
        } ?: error("knowledge/$name has no canonical url")
      url to
        if (resource is dev.ohs.fhir.model.r4.PlanDefinition) demoScope(resource) else resource
    }

  /**
   * Demo scope-cut on the otherwise-verbatim WHO PlanDefinition:
   * - Keeps only the two vaccination actions (definition IMMZD2DTMR). The guidance action's
   *   CommunicationRequest needs list-path dynamicValue support the current DynamicValueApplier
   *   contract doesn't offer (un-indexed `category.coding`); guidance still reaches the UI
   *   straight from the CQL library.
   * - Drops the actions' dynamicValues: they duplicate what instantiateRequest already copies
   *   from the ActivityDefinition (status/intent/medication), use Terser-style paths (bare
   *   `medication` for a choice type), and are authored in text/cql-expression, which the
   *   expression router does not route yet. All three are :workflow gaps worth closing upstream
   *   before WHO PDs run fully verbatim.
   */
  private fun demoScope(
    pd: dev.ohs.fhir.model.r4.PlanDefinition
  ): dev.ohs.fhir.model.r4.PlanDefinition =
    pd.copy(
      action =
        pd.action
          .filter {
            it.definition?.asCanonical()?.value?.value?.endsWith("IMMZD2DTMR") == true
          }
          .map { it.copy(dynamicValue = emptyList()) }
    )

  private val resolver = CanonicalResolver { _, canonical ->
    knowledge[canonical.substringBefore("|")]
  }

  /**
   * Runs $apply for the patient against the given chart bundle and returns the generated
   * MedicationRequest proposal, or null when no vaccination action was applicable.
   */
  suspend fun generateProposal(patientId: String, bundleJson: String): MedicationRequest? {
    // The CQL conditions read the chart from the engine's bundle; the subject contributes
    // only the evaluation context id.
    val subject = Patient(id = patientId)
    val operator =
      FhirOperator(
        repository = NoOpRepository,
        evaluator = ExpressionEvaluatorRouter(elm = CqlEvaluator(engine, bundleJson)),
        resolver = resolver,
      )
    val carePlan =
      operator.generateCarePlan(
        planDefinitionCanonical = PLAN_DEFINITION_CANONICAL,
        subject = subject,
        today = TODAY,
      )
    return carePlan.contained.filterIsInstance<MedicationRequest>().firstOrNull()
  }

  /**
   * Bridges the workflow's ELM expressions to the CQL engine: the WHO idiom puts a define name
   * in the expression text and the Library canonical in Expression.reference; the engine holds
   * that library compiled, so the define is evaluated against the current chart.
   */
  private class CqlEvaluator(
    private val engine: MeaslesEngine,
    private val bundleJson: String,
  ) : ExpressionEvaluator {
    override suspend fun evaluate(
      expression: ProtocolExpression,
      context: EvaluationContext,
    ): EvaluationResult {
      val elm = expression as ProtocolExpression.Elm
      val patientId =
        context.subject.id ?: return EvaluationResult.Failure("subject has no id")
      return when (val value = engine.evaluateRaw(patientId, bundleJson, elm.elmJson)) {
        null -> EvaluationResult.Bool(false)
        is org.opencds.cqf.cql.engine.runtime.Boolean -> EvaluationResult.Bool(value.value)
        else -> EvaluationResult.Values(listOf(value))
      }
    }
  }

  /** $apply resolves knowledge through [resolver]; nothing is read from the repository. */
  private object NoOpRepository : WorkflowRepository {
    override suspend fun read(type: String, id: String): Resource? = null

    override suspend fun create(resource: Resource): String =
      resource.id ?: error("resource has no id")

    override suspend fun update(resource: Resource) {}

    override suspend fun delete(type: String, id: String) {}

    override suspend fun searchByReferenceParam(
      type: String,
      param: String,
      referenceValue: String,
    ): List<Resource> = emptyList()

    override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> =
      emptyList()
  }

  companion object {
    const val PLAN_DEFINITION_CANONICAL =
      "http://smart.who.int/immunizations/PlanDefinition/IMMZD2DTMeaslesOngoingTransmission"

    // MeaslesEngine.TODAY as the $apply evaluation date.
    private val TODAY = LocalDate(2026, 8, 18)

    private val fhirJson = Json {
      ignoreUnknownKeys = true
      encodeDefaults = false
      explicitNulls = false
    }
  }
}

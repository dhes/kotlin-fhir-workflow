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
package dev.ohs.fhir.workflow.cql

import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.EvaluationResult
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import dev.ohs.fhir.workflow.expression.ProtocolExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

class CqlExpressionEvaluatorTest {
  private val json = Json {
    encodeDefaults = false
    explicitNulls = false
  }

  private val patient = decode("""{"resourceType": "Patient", "id": "p1"}""")

  private val immunization =
    decode(
      """
      {
        "resourceType": "Immunization",
        "id": "i1",
        "status": "completed",
        "vaccineCode": {"coding": [{"system": "http://snomed.info/sct", "code": "1119349007"}]},
        "patient": {"reference": "Patient/p1"},
        "occurrenceDateTime": "2026-01-15"
      }
      """
    )

  private val planDefinition =
    decode(
      """
      {
        "resourceType": "PlanDefinition",
        "id": "immunizations",
        "status": "active",
        "library": ["http://example.org/Library/Immunizations"],
        "action": [{
          "id": "follow-up",
          "condition": [{
            "kind": "applicability",
            "expression": {"language": "text/cql", "expression": "Has Immunization"}
          }]
        }]
      }
      """
    )
      as PlanDefinition

  private val evaluator =
    FhirCqlEvaluator(
      listOf(LIBRARY, resource("org/hl7/fhir/FHIRHelpers-4.0.1.cql")),
      resource("org/hl7/fhir/fhir-modelinfo-4.0.1.xml"),
    )

  @Test
  fun appliesTheActionWhenTheCqlDefinitionIsTrue() = runTest {
    assertEquals(1, actions(listOf(patient, immunization)))
  }

  @Test
  fun skipsTheActionWhenTheCqlDefinitionIsFalse() = runTest {
    assertEquals(0, actions(listOf(patient)))
  }

  @Test
  fun evaluatesACqlExpressionThatIsNotADefinition() = runTest {
    val result =
      cqlEvaluator(listOf(patient, immunization))
        .evaluate(
          ProtocolExpression.Elm("Immunizations.\"Immunization Count\" = 1"),
          EvaluationContext(subject = patient, today = TODAY),
        )
    assertEquals(EvaluationResult.Bool(true), result)
  }

  @Test
  fun readsEachPatientsDataFromTheRepositoryOnEachApply() = runTest {
    val repository = ListWorkflowRepository()
    val operator =
      FhirOperator(
        repository,
        ExpressionEvaluatorRouter(
          elm = CqlExpressionEvaluator(evaluator, "Immunizations", repository)
        ),
        noResolver,
      )
    suspend fun actions(subject: Resource) =
      operator
        .generateCarePlan(planDefinition, subject, today = TODAY)
        .contained
        .filterIsInstance<RequestGroup>()
        .single()
        .action
        .size
    val otherPatient = decode("""{"resourceType": "Patient", "id": "p2"}""")

    assertEquals(0, actions(patient))
    repository.create(immunization)
    assertEquals(1, actions(patient))
    assertEquals(0, actions(otherPatient))
  }

  private suspend fun actions(resources: List<Resource>): Int =
    FhirOperator(NoRepository, ExpressionEvaluatorRouter(elm = cqlEvaluator(resources)), noResolver)
      .generateCarePlan(planDefinition, patient, today = TODAY)
      .contained
      .filterIsInstance<RequestGroup>()
      .single()
      .action
      .size

  private fun cqlEvaluator(resources: List<Resource>) =
    CqlExpressionEvaluator(evaluator, "Immunizations", { _, _ -> resources - patient })

  private fun decode(text: String): Resource = json.decodeFromString(Resource.serializer(), text)

  private fun resource(path: String): String =
    javaClass.classLoader.getResource(path)?.readText() ?: error("$path not on the classpath")

  private val noResolver = CanonicalResolver { _, _ -> null }

  private object NoRepository : WorkflowRepository {
    override suspend fun read(type: String, id: String): Resource? = null

    override suspend fun create(resource: Resource): String = error("not used")

    override suspend fun update(resource: Resource) = error("not used")

    override suspend fun delete(type: String, id: String) = error("not used")

    override suspend fun searchByReferenceParam(
      type: String,
      param: String,
      referenceValue: String,
    ): List<Resource> = emptyList()

    override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> =
      emptyList()
  }

  private companion object {
    val TODAY = LocalDate(2026, 7, 7)

    val LIBRARY =
      """
      library Immunizations
      using FHIR version '4.0.1'
      include FHIRHelpers version '4.0.1'
      context Patient
      define "Has Immunization": exists [Immunization]
      define "Immunization Count": Count([Immunization])
      """
        .trimIndent()
  }
}

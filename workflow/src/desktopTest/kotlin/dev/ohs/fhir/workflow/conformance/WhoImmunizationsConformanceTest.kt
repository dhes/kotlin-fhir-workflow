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
package dev.ohs.fhir.workflow.conformance

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ValueSet
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.conformance.cql.CqlExpressionEvaluator
import dev.ohs.fhir.workflow.conformance.cql.FhirCqlEvaluator
import dev.ohs.fhir.workflow.conformance.cql.InMemoryTerminologyProvider
import dev.ohs.fhir.workflow.conformance.cql.InlineCqlLibraries
import dev.ohs.fhir.workflow.conformance.cql.KotlinFhirToCql
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import dev.ohs.fhir.workflow.fhirJson
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opencds.cqf.cql.engine.runtime.Date
import org.opencds.cqf.cql.engine.runtime.TemporalHelper

/**
 * Applies the PlanDefinition of each of WHO's immunization test cases to its patient and compares
 * the CarePlan with what the test case expects. CQL is evaluated by cqframework's CQL engine over
 * the package's libraries and value sets.
 *
 * WHO's files are not in this repository. Set `WHO_IMMUNIZATIONS_FEATURES` to the
 * `input/testing/features/plandefinition` directory of a
 * WorldHealthOrganization/smart-immunizations checkout, and `WHO_IMMUNIZATIONS_PACKAGE` to the
 * `package` directory of the unpacked `smart.who.int.immunizations` package; without both, the test
 * does nothing.
 */
class WhoImmunizationsConformanceTest {
  @Test
  fun planDefinitionApply() =
    runTest(timeout = 10.minutes) {
      val suite = suite() ?: return@runTest
      val counts = suite.counts { suite.definitions }
      assertEquals(expectedOutcomes, counts)
    }

  /**
   * As [planDefinitionApply], with the PlanDefinitions' `text/cql-identifier` and
   * `text/cql-expression` expressions given as `text/cql`, the CQL language that `$apply` routes.
   */
  @Test
  fun planDefinitionApplyWithCqlLanguage() =
    runTest(timeout = 10.minutes) {
      val suite = suite() ?: return@runTest
      val counts = suite.counts { suite.definitionsWithCqlLanguage }
      assertEquals(expectedOutcomesWithCqlLanguage, counts)
    }

  private fun suite(): Suite? {
    val featuresDir = System.getenv("WHO_IMMUNIZATIONS_FEATURES")?.let(::File)
    val packageDir = System.getenv("WHO_IMMUNIZATIONS_PACKAGE")?.let(::File)
    if (featuresDir == null || packageDir == null) {
      println("WHO_IMMUNIZATIONS_FEATURES or WHO_IMMUNIZATIONS_PACKAGE not set; nothing run")
      return null
    }
    return Suite(featuresDir, packageDir)
  }

  private class Suite(featuresDir: File, private val packageDir: File) {
    val testCases = WhoImmunizationsTestCases.read(featuresDir)

    /** The package's PlanDefinitions and ActivityDefinitions as JSON, by canonical url. */
    private val definitionsJson: Map<String, JsonObject> =
      packageJson("PlanDefinition-", "ActivityDefinition-").associateBy {
        it.getValue("url").jsonPrimitive.content
      }

    val definitions: Map<String, Resource> = definitionsJson.mapValues { decode(it.value) }

    val definitionsWithCqlLanguage: Map<String, Resource> =
      definitionsJson.mapValues { decode(withCqlLanguage(it.value) as JsonObject) }

    /** The package's Library resources as JSON. */
    private val libraries: List<JsonObject> = packageJson("Library-")

    private val libraryNames: Map<String, String> =
      libraries.associate {
        it.getValue("url").jsonPrimitive.content to it.getValue("name").jsonPrimitive.content
      }

    private val cqlSources: Map<String, String> =
      libraries
        .mapNotNull { library ->
          val cql =
            library["content"]
              ?.jsonArray
              ?.map { it.jsonObject }
              ?.firstOrNull { it["contentType"]?.jsonPrimitive?.content == "text/cql" }
              ?: return@mapNotNull null
          library.getValue("name").jsonPrimitive.content to
            Base64.getDecoder().decode(cql.getValue("data").jsonPrimitive.content).decodeToString()
        }
        .toMap() + ("FHIRHelpers" to resource("org/hl7/fhir/FHIRHelpers-4.0.1.cql"))

    private val inlineLibraries = InlineCqlLibraries()

    /** One evaluator for every test case, so that each library is compiled once. */
    private val cqlEvaluator =
      FhirCqlEvaluator(
          { name: String -> cqlSources[name] },
          resource("org/hl7/fhir/fhir-modelinfo-4.0.1.xml"),
          InMemoryTerminologyProvider(packageJson("ValueSet-").map { decode(it) as ValueSet }),
        )
        .apply { libraryManager.librarySourceLoader.registerProvider(inlineLibraries) }

    suspend fun counts(definitions: () -> Map<String, Resource>): Map<String, Int> {
      val resolved = definitions()
      val resolver = CanonicalResolver { _, canonical -> resolved[canonical.substringBefore("|")] }
      val byOutcome = testCases.groupBy { outcome(it, resolved, resolver) }.toSortedMap()
      println(
        "${testCases.size} test cases, ${testCases.map { it.planDefinition }.toSet().size} PlanDefinitions"
      )
      byOutcome.forEach { (outcome, cases) ->
        println("  ${cases.size}  $outcome")
        if (outcome != PASS && cases.size <= 5) cases.forEach { println("      ${it.id}") }
      }
      return byOutcome.mapValues { it.value.size }
    }

    private suspend fun outcome(
      testCase: WhoImmunizationsTestCase,
      definitions: Map<String, Resource>,
      resolver: CanonicalResolver,
    ): String {
      val plan =
        definitions["http://smart.who.int/immunizations/PlanDefinition/${testCase.planDefinition}"]
          as? PlanDefinition ?: return "PlanDefinition not in the package"
      val resources =
        try {
          (fhirJson.decodeFromString(Resource.serializer(), testCase.bundle.readText()) as Bundle)
            .entry
            .mapNotNull { it.resource }
        } catch (e: Exception) {
          return "Test bundle not decoded: ${e::class.simpleName}"
        }
      val patient =
        resources.filterIsInstance<Patient>().singleOrNull { it.id == testCase.patient }
          ?: return "Patient not in the test bundle"
      val library =
        plan.library.firstOrNull()?.value?.let { libraryNames[it] }
          ?: return "PlanDefinition library not in the package"
      val today = testCase.today
      val evaluationDateTime =
        ZonedDateTime.of(
          today.year,
          today.monthNumber,
          today.dayOfMonth,
          12,
          0,
          0,
          0,
          ZoneId.systemDefault(),
        )
      val converter = KotlinFhirToCql(TemporalHelper.zoneToOffset(evaluationDateTime.offset))
      val cql =
        CqlExpressionEvaluator(
          cqlEvaluator,
          inlineLibraries,
          library,
          testCase.patient,
          resources.map { converter.convert(it) },
          mapOf("Today" to Date(today.year, today.monthNumber, today.dayOfMonth)),
          evaluationDateTime,
        )
      val carePlan =
        try {
          FhirOperator(InMemoryWorkflowRepository(), ExpressionEvaluatorRouter(elm = cql), resolver)
            .generateCarePlan(plan, patient, today = today)
        } catch (e: Throwable) {
          return "${e::class.simpleName}: ${e.message?.lineSequence()?.first()}"
        }
      val guidance =
        carePlan.contained.filterIsInstance<CommunicationRequest>().firstNotNullOfOrNull {
          it.payload.firstOrNull()?.content?.asString()?.value?.value
        }
      return when {
        testCase.expectedContained != null &&
          testCase.expectedContained != carePlan.contained.size ->
          "Contained resource count differs from WHO's"

        testCase.expectedGuidance != null && testCase.expectedGuidance != guidance ->
          "Guidance differs from WHO's"

        else -> PASS
      }
    }

    private fun packageJson(vararg prefixes: String): List<JsonObject> =
      packageDir
        .listFiles { file ->
          prefixes.any { file.name.startsWith(it) } && file.name.endsWith(".json")
        }!!
        .sortedBy { it.name }
        .map { fhirJson.parseToJsonElement(it.readText()).jsonObject }

    private fun decode(json: JsonObject): Resource =
      fhirJson.decodeFromJsonElement(Resource.serializer(), json)

    private fun resource(path: String): String =
      javaClass.classLoader.getResource(path)?.readText() ?: error("$path not on the classpath")
  }

  private companion object {
    const val PASS = "Passes"

    /** Gives every `text/cql-identifier` and `text/cql-expression` expression as `text/cql`. */
    fun withCqlLanguage(element: JsonElement): JsonElement =
      when (element) {
        is JsonArray -> JsonArray(element.map(::withCqlLanguage))

        is JsonObject ->
          JsonObject(
            element.mapValues { (key, value) ->
              val language = (value as? JsonPrimitive)?.content
              if (
                key == "language" &&
                  (language == "text/cql-identifier" || language == "text/cql-expression")
              ) {
                JsonPrimitive("text/cql")
              } else {
                withCqlLanguage(value)
              }
            }
          )

        else -> element
      }

    /**
     * The number of test cases with each outcome, at smart-immunizations 12ec2fc54 and package
     * 0.2.0.
     *
     * N.B. This should be kept in sync with the test conformance table in `docs/conformance.md`.
     */
    val expectedOutcomes: Map<String, Int> =
      sortedMapOf(
        "IllegalStateException: Unsupported expression language: text/cql-identifier" to 1020,
        "Test bundle not decoded: SerializationException" to 1,
      )

    /** As [expectedOutcomes], for [planDefinitionApplyWithCqlLanguage]. */
    val expectedOutcomesWithCqlLanguage: Map<String, Int> =
      sortedMapOf(
        PASS to 1004,
        "Contained resource count differs from WHO's" to 16,
        "Test bundle not decoded: SerializationException" to 1,
      )
  }
}

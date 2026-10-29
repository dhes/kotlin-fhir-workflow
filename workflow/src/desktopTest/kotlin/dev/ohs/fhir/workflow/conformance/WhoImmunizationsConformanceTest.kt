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
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.fhirJson
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Applies the PlanDefinition of each of WHO's immunization test cases to its patient and compares
 * the CarePlan with what the test case expects.
 *
 * WHO's files are not in this repository. Set `WHO_IMMUNIZATIONS_FEATURES` to the
 * `input/testing/features/plandefinition` directory of a
 * WorldHealthOrganization/smart-immunizations checkout, and `WHO_IMMUNIZATIONS_PACKAGE` to the
 * `package` directory of the unpacked `smart.who.int.immunizations` package; without both, the test
 * does nothing.
 */
class WhoImmunizationsConformanceTest {
  @Test
  fun planDefinitionApply() = runTest {
    val featuresDir = System.getenv("WHO_IMMUNIZATIONS_FEATURES")?.let(::File)
    val packageDir = System.getenv("WHO_IMMUNIZATIONS_PACKAGE")?.let(::File)
    if (featuresDir == null || packageDir == null) {
      println("WHO_IMMUNIZATIONS_FEATURES or WHO_IMMUNIZATIONS_PACKAGE not set; nothing run")
      return@runTest
    }
    val definitions = definitions(packageDir)
    val resolver = CanonicalResolver { _, canonical -> definitions[canonical.substringBefore("|")] }
    val testCases = WhoImmunizationsTestCases.read(featuresDir)
    val outcomes = testCases.associate { it.id to outcome(it, definitions, resolver) }

    val counts = outcomes.values.groupingBy { it }.eachCount().toSortedMap()
    println(
      "${testCases.size} test cases, ${testCases.map { it.planDefinition }.toSet().size} PlanDefinitions"
    )
    counts.forEach { (outcome, count) -> println("  $count  $outcome") }
    assertEquals(expectedOutcomes, counts)
  }

  /** The PlanDefinitions and ActivityDefinitions of the package, by canonical url. */
  private fun definitions(packageDir: File): Map<String, Resource> =
    packageDir
      .listFiles { file ->
        (file.name.startsWith("PlanDefinition-") || file.name.startsWith("ActivityDefinition-")) &&
          file.name.endsWith(".json")
      }!!
      .associate { file ->
        val text = file.readText()
        fhirJson.parseToJsonElement(text).jsonObject.getValue("url").jsonPrimitive.content to
          fhirJson.decodeFromString(Resource.serializer(), text)
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
    val carePlan =
      try {
        FhirOperator(InMemoryWorkflowRepository(), resolver = resolver)
          .generateCarePlan(plan, patient, today = testCase.today)
      } catch (e: Exception) {
        return "${e::class.simpleName}: ${e.message?.lineSequence()?.first()}"
      }
    val guidance =
      carePlan.contained.filterIsInstance<CommunicationRequest>().firstNotNullOfOrNull {
        it.payload.firstOrNull()?.content?.asString()?.value?.value
      }
    return when {
      testCase.expectedContained != null && testCase.expectedContained != carePlan.contained.size ->
        "Contained resource count differs from WHO's"

      testCase.expectedGuidance != null && testCase.expectedGuidance != guidance ->
        "Guidance differs from WHO's"

      else -> PASS
    }
  }

  private companion object {
    const val PASS = "Passes"

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
  }
}

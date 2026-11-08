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
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ValueSet
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.cql.CqlExpressionEvaluator
import dev.ohs.fhir.workflow.cql.EmptyWorkflowRepository
import dev.ohs.fhir.workflow.cql.FhirCqlEvaluator
import dev.ohs.fhir.workflow.cql.InMemoryTerminologyProvider
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opencds.cqf.cql.engine.runtime.Date

/** Probe: start-up cost apart from the cost of each `$apply`, on WHO's PlanDefinitions. */
class CqlTimingProbeTest {
  private val json = Json {
    encodeDefaults = false
    explicitNulls = false
  }

  @Test
  fun timing() =
    runTest(timeout = 30.minutes) {
      val features = setting("WHO_IMMUNIZATIONS_FEATURES")?.let(::File) ?: return@runTest
      val packageDir = setting("WHO_IMMUNIZATIONS_PACKAGE")?.let(::File) ?: return@runTest
      fun log(step: String, d: Duration) = println("TIMING | $step | ${d.inWholeMilliseconds} ms")
      fun <T> timed(step: String, block: () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        return block().also { log(step, mark.elapsedNow()) }
      }

      val modelInfo = timed("read model info XML (3.4 MB)") {
        (System.getenv("FHIR_MODELINFO")?.let { java.io.File(it).readText() } ?: resource("org/hl7/fhir/fhir-modelinfo-4.0.1.xml"))
      }
      val fhirHelpers = resource("org/hl7/fhir/FHIRHelpers-4.0.1.cql")
      fun packageJson(prefix: String) =
        packageDir
          .listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".json") }!!
          .sortedBy { it.name }
          .map { json.parseToJsonElement(it.readText()).jsonObject }
      fun decode(o: JsonObject) = json.decodeFromJsonElement(Resource.serializer(), o)

      val definitions = timed("decode PlanDefinitions and ActivityDefinitions") {
        (packageJson("PlanDefinition-") + packageJson("ActivityDefinition-")).associate {
          it.getValue("url").jsonPrimitive.content to decode(it)
        }
      }
      val libraries = timed("read Library resources") { packageJson("Library-") }
      val libraryNames =
        libraries.associate {
          it.getValue("url").jsonPrimitive.content to it.getValue("name").jsonPrimitive.content
        }
      val cqlSources =
        libraries
          .mapNotNull { library ->
            val cql =
              library["content"]?.jsonArray?.map { it.jsonObject }?.firstOrNull {
                it["contentType"]?.jsonPrimitive?.content == "text/cql"
              } ?: return@mapNotNull null
            library.getValue("name").jsonPrimitive.content to
              Base64.getDecoder().decode(cql.getValue("data").jsonPrimitive.content).decodeToString()
          }
          .toMap() + ("FHIRHelpers" to fhirHelpers)
      val valueSets = timed("decode ValueSets") { packageJson("ValueSet-").map { decode(it) as ValueSet } }
      val evaluator = timed("create FhirCqlEvaluator (parses the model info)") {
        FhirCqlEvaluator({ name: String -> cqlSources[name] }, modelInfo, InMemoryTerminologyProvider(valueSets))
      }
      val resolver = CanonicalResolver { _, canonical -> definitions[canonical.substringBefore("|")] }

      val byPlan = WhoImmunizationsTestCases.read(features).groupBy { it.planDefinition }
      val plans = byPlan.entries.sortedByDescending { it.value.size }.take(3)
      for ((planName, cases) in plans) {
        val plan =
          definitions["http://smart.who.int/immunizations/PlanDefinition/$planName"] as PlanDefinition
        val times = mutableListOf<Duration>()
        var failures = 0
        var actions = 0
        for (testCase in cases) {
          val resources =
            runCatching {
                (json.decodeFromString(Resource.serializer(), testCase.bundle.readText()) as Bundle)
                  .entry
                  .mapNotNull { it.resource }
              }
              .getOrNull() ?: continue
          val patient =
            resources.filterIsInstance<Patient>().singleOrNull { it.id == testCase.patient } ?: continue
          val today = testCase.today
          val cql =
            CqlExpressionEvaluator(
              evaluator,
              { _, _ -> resources.filter { it !== patient } },
              mapOf("Today" to Date(today.year, today.monthNumber, today.dayOfMonth)),
              libraryNames::get,
            )
          val operator =
            FhirOperator(EmptyWorkflowRepository, ExpressionEvaluatorRouter(elm = cql), resolver)
          val mark = TimeSource.Monotonic.markNow()
          runCatching { operator.generateCarePlan(plan, patient, today = today) }
            .onSuccess { actions += it.contained.size }
            .onFailure { failures++ }
          times += mark.elapsedNow()
        }
        log("$planName: first \$apply (compiles its libraries)", times.first())
        val rest = times.drop(1).sorted()
        println(
          "TIMING | $planName: next ${rest.size} \$apply | median ${rest[rest.size / 2].inWholeMilliseconds} ms," +
            " mean ${rest.map { it.inWholeMilliseconds }.average().toLong()} ms," +
            " max ${rest.last().inWholeMilliseconds} ms; $failures failed, $actions contained resources in all"
        )
      }
    }

  private fun resource(path: String): String =
    javaClass.classLoader.getResource(path)?.readText() ?: error("$path not on the classpath")

  /** An environment variable, or on Android an instrumentation argument of the same name. */
  private fun setting(name: String): String? =
    System.getenv(name)
      ?: runCatching {
          val arguments =
            Class.forName("androidx.test.platform.app.InstrumentationRegistry")
              .getMethod("getArguments")
              .invoke(null)
          arguments.javaClass.getMethod("getString", String::class.java).invoke(arguments, name)
            as String?
        }
        .getOrNull()
}

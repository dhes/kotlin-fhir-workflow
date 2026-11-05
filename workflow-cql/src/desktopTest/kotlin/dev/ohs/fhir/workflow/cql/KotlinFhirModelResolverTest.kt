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

import dev.ohs.fhir.model.r4.Resource
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.opencds.cqf.cql.engine.runtime.Integer as CqlInteger

class KotlinFhirModelResolverTest {
  private val evaluator =
    FhirCqlEvaluator(
      listOf(LIBRARY, resource("org/hl7/fhir/FHIRHelpers-4.0.1.cql")),
      resource("org/hl7/fhir/fhir-modelinfo-4.0.1.xml"),
    )

  @Test
  fun givesTheElementThatLinksAResourceToItsPatient() {
    val resolver = evaluator.modelResolver
    assertEquals("patient", resolver.getContextPath("Patient", "Immunization"))
    assertEquals("subject", resolver.getContextPath("Patient", "Condition"))
    assertEquals("for", resolver.getContextPath("Patient", "Task"))
    assertEquals("participant.actor", resolver.getContextPath("Patient", "Appointment"))
    assertEquals("id", resolver.getContextPath("Patient", "Patient"))
    assertEquals(null, resolver.getContextPath("Patient", "ValueSet"))
    assertEquals(null, resolver.getContextPath("Practitioner", "Condition"))
  }

  @Test
  fun retrievesByAnElementInsideABackboneElement() {
    val converter = KotlinFhirToCql(BigDecimal.ZERO)
    val appointments =
      listOf("p1", "p2").mapIndexed { i, patient ->
        decode(
          """
          {
            "resourceType": "Appointment",
            "id": "a$i",
            "status": "booked",
            "participant": [{"actor": {"reference": "Patient/$patient"}, "status": "accepted"}]
          }
          """
        )
      }
    val count =
      evaluator
        .evaluate("Appointments", "p1", appointments.map { converter.convert(it) })[
          "Appointment Count",
        ]
    assertEquals(1, (count as CqlInteger).value)
  }

  private fun decode(text: String): Resource = json.decodeFromString(Resource.serializer(), text)

  private fun resource(path: String): String =
    javaClass.classLoader.getResource(path)?.readText() ?: error("$path not on the classpath")

  private companion object {
    val json = Json {
      encodeDefaults = false
      explicitNulls = false
    }

    val LIBRARY =
      """
      library Appointments
      using FHIR version '4.0.1'
      include FHIRHelpers version '4.0.1'
      context Patient
      define "Appointment Count": Count([Appointment])
      """
        .trimIndent()
  }
}

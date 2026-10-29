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

import java.io.File
import kotlinx.datetime.LocalDate

/**
 * One scenario of WHO's immunization test cases: a patient, a PlanDefinition and what `$apply`
 * should return.
 */
data class WhoImmunizationsTestCase(
  val id: String,
  val feature: String,
  val title: String,
  val planDefinition: String,
  val today: LocalDate,
  val patient: String,
  val bundle: File,
  val expectedGuidance: String?,
  val expectedContained: Int?,
)

/**
 * Reads the Karate feature files under WHO's `input/testing/features/plandefinition`
 * (WorldHealthOrganization/smart-immunizations), one test case per scenario that applies a
 * PlanDefinition.
 */
object WhoImmunizationsTestCases {
  fun read(featuresDir: File): List<WhoImmunizationsTestCase> =
    featuresDir
      .walkTopDown()
      .filter { it.isFile && it.extension == "feature" && it.parentFile != featuresDir }
      .sortedBy { it.relativeTo(featuresDir).path }
      .flatMap { read(featuresDir, it) }
      .toList()

  private fun read(featuresDir: File, feature: File): List<WhoImmunizationsTestCase> {
    val text = feature.readText()
    if (text.trimStart().startsWith("@ignore")) return emptyList()
    val background = text.substringBefore("Scenario:")
    val today =
      Regex("""parameter\[0]\.valueDate = "(\d{4}-\d{2}-\d{2})"""")
        .find(background)
        ?.groupValues
        ?.get(1) ?: defaultToday(feature, background) ?: error("No Today date for ${feature.name}")
    return text.split(Regex("""\n(?=\s*@)""")).drop(1).mapNotNull { block ->
      val tags = block.trim().lineSequence().first()
      if ("Scenario" !in block || "@ignore" in tags) return@mapNotNull null
      val patient = Regex("""@patient=(\S+)""").find(tags)?.groupValues?.get(1)
      val bundle =
        Regex("""IMMZ\.feature@create'\) \{ "file": "\./data/([^"]+)" }""")
          .find(block)
          ?.groupValues
          ?.get(1)
      val plan =
        Regex("""path 'PlanDefinition/([^/]+)/\${'$'}apply'""").find(block)?.groupValues?.get(1)
      if (patient == null || bundle == null || plan == null) return@mapNotNull null
      WhoImmunizationsTestCase(
        id = "${feature.nameWithoutExtension}/$patient",
        feature = feature.relativeTo(featuresDir).path,
        title = Regex("""Scenario: (.*)""").find(block)?.groupValues?.get(1)?.trim().orEmpty(),
        planDefinition = plan,
        today = LocalDate.parse(today),
        patient = patient,
        bundle = File(feature.parentFile, "data/$bundle"),
        expectedGuidance =
          Regex(""".payload\.contentString = "((?:[^"\\]|\\.)*)"""")
            .find(block)
            ?.groupValues
            ?.get(1)
            ?.let(::unescape),
        expectedContained =
          Regex("""match response\.contained == '#\[(\d+)]'""")
            .find(block)
            ?.groupValues
            ?.get(1)
            ?.toInt(),
      )
    }
  }

  /** The Today date set by the `@defaults` scenario that the feature's background calls. */
  private fun defaultToday(feature: File, background: String): String? {
    val parent =
      Regex("""call read\('(\.\./[^@']+)@defaults'\)""").find(background)?.groupValues?.get(1)
    val defaults = parent?.let { File(feature.parentFile, it).normalize() }?.takeIf { it.isFile }
    return defaults?.readText()?.let {
      Regex(""""Today",\s*"valueDate":\s*"(\d{4}-\d{2}-\d{2})"""").find(it)?.groupValues?.get(1)
    }
  }

  /** A double-quoted string as Karate reads it. */
  private fun unescape(s: String): String =
    Regex("""\\(.)""").replace(s) {
      when (val c = it.groupValues[1]) {
        "n" -> "\n"
        "t" -> "\t"
        "r" -> "\r"
        else -> c
      }
    }
}

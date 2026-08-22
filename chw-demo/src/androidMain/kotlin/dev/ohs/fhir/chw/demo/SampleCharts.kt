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

/**
 * Synthetic charts aged against MeaslesEngine.TODAY (2026-08-18), one per demo scenario of
 * the ongoing-transmission schedule (MCV1 at 9 months, MCV2 at 15-18 months).
 *
 * Data requirements from the DAK's own logic: vaccineCode must be in IMMZ.Z.DE9
 * "Measles-containing vaccines" (ICD-11 XM28X5 is a member), and primary-series dose
 * counting requires protocolApplied.series = 'Primary series' (IMMZCommon.seriesPrimary()).
 */
data class DemoPatient(val id: String, val label: String, val detail: String)

val DEMO_PATIENTS =
  listOf(
    DemoPatient("amara", "Amara", "girl · 10 months · no doses"),
    DemoPatient("kofi", "Kofi", "boy · 16 months · MCV1 given"),
    DemoPatient("zuri", "Zuri", "girl · 24 months · MCV1 + MCV2"),
  )

private const val MEASLES_CODING =
  """{"coding":[{"system":"http://id.who.int/icd/release/11/mms","code":"XM28X5"}]}"""

val PATIENT_BUNDLE =
  """
  {"resourceType":"Bundle","type":"collection","entry":[
    {"resource":{"resourceType":"Patient","id":"amara","gender":"female","birthDate":"2025-10-15"}},

    {"resource":{"resourceType":"Patient","id":"kofi","gender":"male","birthDate":"2025-04-10"}},
    {"resource":{"resourceType":"Immunization","id":"kofi-mcv1","status":"completed",
      "vaccineCode":$MEASLES_CODING,
      "patient":{"reference":"Patient/kofi"},"occurrenceDateTime":"2026-01-15",
      "protocolApplied":[{"series":"Primary series","doseNumberPositiveInt":1}]}},

    {"resource":{"resourceType":"Patient","id":"zuri","gender":"female","birthDate":"2024-08-01"}},
    {"resource":{"resourceType":"Immunization","id":"zuri-mcv1","status":"completed",
      "vaccineCode":$MEASLES_CODING,
      "patient":{"reference":"Patient/zuri"},"occurrenceDateTime":"2025-05-05",
      "protocolApplied":[{"series":"Primary series","doseNumberPositiveInt":1}]}},
    {"resource":{"resourceType":"Immunization","id":"zuri-mcv2","status":"completed",
      "vaccineCode":$MEASLES_CODING,
      "patient":{"reference":"Patient/zuri"},"occurrenceDateTime":"2025-11-20",
      "protocolApplied":[{"series":"Primary series","doseNumberPositiveInt":2}]}}
  ]}
  """
    .trimIndent()

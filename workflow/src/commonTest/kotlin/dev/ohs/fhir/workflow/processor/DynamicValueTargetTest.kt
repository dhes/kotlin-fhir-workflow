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
package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicValueTargetTest {
  private val communicationRequest = CommunicationRequest.serializer().descriptor
  private val medicationRequest = MedicationRequest.serializer().descriptor
  private val coding =
    Coding(
      system = Uri(value = "http://hl7.org/fhir/request-priority"),
      code = Code(value = "routine"),
    )
  private val concept = CodeableConcept(text = FhirString(value = "BCG vaccines"))

  @Test
  fun shouldKeepPathWhenAlreadyInContract() {
    assertEquals(
      DynamicValueTarget("category[0].coding[0]", isPrimitive = false),
      dynamicValueTarget(communicationRequest, "category[0].coding[0]", coding),
    )
    assertEquals(
      DynamicValueTarget("medicationCodeableConcept", isPrimitive = false),
      dynamicValueTarget(medicationRequest, "medicationCodeableConcept", concept),
    )
  }

  @Test
  fun shouldIndexListElementsWhenIndexMissing() {
    assertEquals(
      DynamicValueTarget("category[0].coding[0]", isPrimitive = false),
      dynamicValueTarget(communicationRequest, "CommunicationRequest.category.coding", coding),
    )
    assertEquals(
      DynamicValueTarget("payload[0].contentString", isPrimitive = true),
      dynamicValueTarget(communicationRequest, "payload.contentString", "Guidance text"),
    )
  }

  @Test
  fun shouldNameChoiceElementByValueType() {
    assertEquals(
      DynamicValueTarget("medicationCodeableConcept", isPrimitive = false),
      dynamicValueTarget(medicationRequest, "MedicationRequest.medication", concept),
    )
    assertEquals(
      DynamicValueTarget("medicationReference", isPrimitive = false),
      dynamicValueTarget(medicationRequest, "medication", Reference()),
    )
  }

  @Test
  fun shouldReportPrimitiveWhenElementIsCode() {
    assertEquals(
      DynamicValueTarget("priority", isPrimitive = true),
      dynamicValueTarget(communicationRequest, "CommunicationRequest.priority", coding),
    )
  }

  @Test
  fun shouldKeepPathWhenElementUnknown() {
    assertEquals(
      DynamicValueTarget("notAnElement.x", isPrimitive = false),
      dynamicValueTarget(communicationRequest, "notAnElement.x", "x"),
    )
  }
}

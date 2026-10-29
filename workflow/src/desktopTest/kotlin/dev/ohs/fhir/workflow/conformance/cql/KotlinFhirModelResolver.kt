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
package dev.ohs.fhir.workflow.conformance.cql

import org.cqframework.cql.cql2elm.model.Model
import org.cqframework.cql.shared.QName
import org.opencds.cqf.cql.engine.elm.executing.EquivalentEvaluator
import org.opencds.cqf.cql.engine.model.ModelResolver
import org.opencds.cqf.cql.engine.runtime.Boolean as CqlBoolean
import org.opencds.cqf.cql.engine.runtime.ClassInstance
import org.opencds.cqf.cql.engine.runtime.String as CqlString
import org.opencds.cqf.cql.engine.runtime.Value
import org.opencds.cqf.cql.engine.runtime.anyTypeName

/**
 * A [ModelResolver] for FHIR R4 values that follow the FHIR 4.0.1 model info, such as those made by
 * [KotlinFhirToCql]. It answers type questions from the model info itself, where cqframework's
 * HAPI-based `R4FhirModelResolver` maps type names to HAPI's Java classes; it uses no kotlin-fhir
 * classes.
 */
class KotlinFhirModelResolver(private val model: Model) : ModelResolver {

  override fun getContextPath(contextType: String?, targetType: String?): String? {
    if (contextType == null || targetType == null) return null
    if (contextType in setOf("Unfiltered", "Unspecified", "Population")) return null
    if (targetType == contextType) return "id"
    if (contextType != "Patient") return null
    return PATIENT_CONTEXT_PATHS[targetType]
  }

  override fun `is`(valueType: String, type: QName): Boolean? {
    if (type == anyTypeName) return true
    if (type.getNamespaceURI() != FHIR_MODEL_URI) return false
    val valueDataType = model.resolveTypeName(valueType) ?: return null
    val dataType = model.resolveTypeName(type.getLocalPart()) ?: return null
    return valueDataType.isSubTypeOf(dataType)
  }

  override fun createInstance(typeName: String?): Value =
    ClassInstance(fhirType(typeName!!), mutableMapOf())

  override fun objectEquivalent(
    left: ClassInstance,
    right: ClassInstance,
    equivalent: (l: Value?, r: Value?) -> CqlBoolean,
  ): CqlBoolean {
    // "id" is not compared for FHIR.Resource and FHIR.Element instances
    // (https://hl7.org/fhir/fhirpath.html#changes).
    val isResourceOrElement =
      `is`(left.type.getLocalPart(), fhirType("Resource")) == true ||
        `is`(left.type.getLocalPart(), fhirType("Element")) == true
    return if (isResourceOrElement) {
      EquivalentEvaluator.structuredValueElementsEquivalent(
        left.elements.filterKeys { it != "id" },
        right.elements.filterKeys { it != "id" },
        equivalent,
      )
    } else {
      EquivalentEvaluator.structuredValueElementsEquivalent(
        left.elements,
        right.elements,
        equivalent,
      )
    }
  }

  override fun resolveId(target: Value?): String? {
    if (target !is ClassInstance || target.type.getNamespaceURI() != FHIR_MODEL_URI) return null
    val id = target.elements["id"] as? ClassInstance ?: return null
    return (id.elements["value"] as? CqlString)?.value
  }

  companion object {
    /**
     * The element that links each resource type to its patient, for the types the WHO immunization
     * measles logic retrieves. The cqframework HAPI resolver derives this from HAPI's resource
     * definitions; there is no equivalent in the model info.
     */
    val PATIENT_CONTEXT_PATHS =
      mapOf(
        "Immunization" to "patient",
        "Condition" to "subject",
        "Observation" to "subject",
        "MedicationRequest" to "subject",
        "Encounter" to "subject",
        "Procedure" to "subject",
        "AllergyIntolerance" to "patient",
      )
  }
}

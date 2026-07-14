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
package dev.ohs.fhir.workflow.operation

class WorkflowOperationNotSupportedException(message: String) : Exception(message)

/**
 * Measure $evaluate and Library $evaluate are CQL-native operations that the android-fhir
 * FhirOperator delegated to cqframework. They are intentionally NOT migrated to KMP (no CQL
 * runtime). These stubs preserve the API surface and fail with a clear message. Run these
 * server-side on the JVM/HAPI stack if needed.
 */
object UnsupportedOperations {
  fun evaluateMeasure(measureUrl: String): Nothing =
    throw WorkflowOperationNotSupportedException(
      "Measure \$evaluate ($measureUrl) is not supported in kotlin-fhir-workflow. " +
        "It requires CQL; run it on the server-side HAPI/cqframework stack."
    )

  fun evaluateLibrary(libraryUrl: String): Nothing =
    throw WorkflowOperationNotSupportedException(
      "Library \$evaluate ($libraryUrl) is not supported in kotlin-fhir-workflow. " +
        "It requires CQL; run it on the server-side HAPI/cqframework stack."
    )
}

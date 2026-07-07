package dev.ohs.fhir.workflow.operation

class WorkflowOperationNotSupportedException(message: String) : Exception(message)

/**
 * Measure $evaluate and Library $evaluate are CQL-native operations that the
 * android-fhir FhirOperator delegated to cqframework. They are intentionally NOT
 * migrated to KMP (no CQL runtime). These stubs preserve the API surface and fail
 * with a clear message. Run these server-side on the JVM/HAPI stack if needed.
 */
object UnsupportedOperations {
  fun evaluateMeasure(measureUrl: String): Nothing =
    throw WorkflowOperationNotSupportedException(
      "Measure \$evaluate ($measureUrl) is not supported in kotlin-fhir-workflow. " +
        "It requires CQL; run it on the server-side HAPI/cqframework stack.",
    )

  fun evaluateLibrary(libraryUrl: String): Nothing =
    throw WorkflowOperationNotSupportedException(
      "Library \$evaluate ($libraryUrl) is not supported in kotlin-fhir-workflow. " +
        "It requires CQL; run it on the server-side HAPI/cqframework stack.",
    )
}

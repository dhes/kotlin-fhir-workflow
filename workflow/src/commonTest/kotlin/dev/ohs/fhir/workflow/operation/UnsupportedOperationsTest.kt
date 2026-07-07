package dev.ohs.fhir.workflow.operation

import kotlin.test.Test
import kotlin.test.assertFailsWith

class UnsupportedOperationsTest {
  @Test
  fun `evaluateMeasure throws not-supported`() {
    assertFailsWith<WorkflowOperationNotSupportedException> {
      UnsupportedOperations.evaluateMeasure("Measure/x")
    }
  }

  @Test
  fun `evaluateLibrary throws not-supported`() {
    assertFailsWith<WorkflowOperationNotSupportedException> {
      UnsupportedOperations.evaluateLibrary("Library/x")
    }
  }
}

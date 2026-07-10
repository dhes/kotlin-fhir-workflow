package dev.ohs.fhir.workflow.demo.data

import dev.ohs.fhir.workflow.repository.WorkflowRepository

actual fun demoWorkflowRepository(platformContext: Any): WorkflowRepository =
  InMemoryDemoRepository()

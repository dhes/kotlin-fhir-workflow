package dev.ohs.fhir.workflow.demo.data

import dev.ohs.fhir.workflow.repository.WorkflowRepository

/**
 * Returns the demo's [WorkflowRepository]: engine-backed on platforms with
 * `dev.ohs.fhir:fhir-engine` (nonWeb), in-memory on web (no engine target).
 *
 * @param platformContext Platform-specific context (e.g. Android `Context`). Ignored where unused.
 */
expect fun demoWorkflowRepository(platformContext: Any = Unit): WorkflowRepository

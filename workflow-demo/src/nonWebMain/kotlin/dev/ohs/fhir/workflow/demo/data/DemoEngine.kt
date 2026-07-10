package dev.ohs.fhir.workflow.demo.data

import dev.ohs.fhir.FhirEngine
import dev.ohs.fhir.FhirEngineConfiguration
import dev.ohs.fhir.FhirEngineProvider

/**
 * Returns the shared [FhirEngine] instance, initializing [FhirEngineProvider] on first call.
 *
 * @param platformContext Platform-specific context (e.g. Android `Context`). Ignored on
 *   desktop/iOS.
 */
fun fhirEngine(platformContext: Any = Unit): FhirEngine {
  if (FhirEngineProvider.isNotInitialized()) {
    FhirEngineProvider.init(
      FhirEngineConfiguration(enableEncryptionIfSupported = false),
      platformContext,
    )
  }
  return FhirEngineProvider.getInstance(platformContext)
}

package dev.ohs.fhir.workflow

import dev.ohs.fhir.model.r4.Reference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FhirModelExtensionsTest {
  @Test
  fun `reference round trips`() {
    val r = reference("Patient/pat-01")
    assertEquals("Patient/pat-01", r.ref)
  }

  @Test
  fun `fhirString wraps and null passes through`() {
    assertEquals("x", fhirString("x")?.value)
    assertNull(fhirString(null))
  }
}

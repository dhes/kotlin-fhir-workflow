package dev.ohs.fhir.workflow

import dev.ohs.fhir.model.r4.Patient
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
  @Test
  fun `fhir model resolves and constructs`() {
    val patient = Patient(id = "p1")
    assertEquals("p1", patient.id)
  }
}

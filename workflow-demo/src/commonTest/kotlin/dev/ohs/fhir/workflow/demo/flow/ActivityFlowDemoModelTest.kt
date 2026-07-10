package dev.ohs.fhir.workflow.demo.flow

import dev.ohs.fhir.workflow.demo.data.InMemoryDemoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ActivityFlowDemoModelTest {

  private fun newModel() = ActivityFlowDemoModel(InMemoryDemoRepository())

  private fun cardsByName(model: ActivityFlowDemoModel) = model.phaseCards().associateBy { it.name }

  @Test
  fun `createProposal starts an active proposal phase`() = runTest {
    val model = newModel()
    model.createProposal("apple-guy-${Uuid.random()}")

    val cards = cardsByName(model)
    val proposalCard = requireNotNull(cards["PROPOSAL"])
    assertTrue(proposalCard.isActive)
    assertTrue(proposalCard.details.contains("Intent: proposal"))
    assertTrue(proposalCard.details.contains("Status: ACTIVE"))

    assertEquals("—", cards.getValue("PLAN").details)
    assertTrue(!cards.getValue("PLAN").isActive)
  }

  @Test
  fun `advance walks proposal through plan, order and a completed perform`() = runTest {
    val model = newModel()
    model.createProposal("apple-guy-${Uuid.random()}")

    model.advance()
    var cards = cardsByName(model)
    val planCard = requireNotNull(cards["PLAN"])
    assertTrue(planCard.isActive)
    assertTrue(planCard.details.contains("Intent: plan"))
    assertTrue(planCard.details.contains("Status: DRAFT"))
    assertTrue(cards.getValue("PROPOSAL").details.contains("Status: COMPLETED"))

    model.advance()
    cards = cardsByName(model)
    val orderCard = requireNotNull(cards["ORDER"])
    assertTrue(orderCard.isActive)
    assertTrue(orderCard.details.contains("Intent: order"))
    assertTrue(orderCard.details.contains("Status: DRAFT"))
    assertTrue(cards.getValue("PLAN").details.contains("Status: COMPLETED"))

    model.advance()
    cards = cardsByName(model)
    val performCard = requireNotNull(cards["PERFORM"])
    assertTrue(performCard.isActive)
    assertTrue(performCard.details.contains("Status: COMPLETED"))
    assertTrue(cards.getValue("ORDER").details.contains("Status: COMPLETED"))
  }

  @Test
  fun `restart clears the flow back to no proposal`() = runTest {
    val model = newModel()
    model.createProposal("apple-guy-${Uuid.random()}")
    model.advance()

    model.restart()

    val cards = cardsByName(model)
    cards.values.forEach { card ->
      assertEquals("—", card.details)
      assertTrue(!card.isActive)
    }
  }
}

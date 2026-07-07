package dev.ohs.fhir.workflow.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleEvaluatorTest {
  @Test
  fun `six weeks after birth with plus-minus window`() {
    val birth = LocalDate(2026, 1, 1)
    val range = ScheduleEvaluator.evaluate(
      ScheduleExpression.OffsetFromAnchor(
        anchor = birth,
        offset = DatePeriod(days = 42),
        before = DatePeriod(days = 7),
        after = DatePeriod(days = 14),
      ),
    )
    assertEquals(LocalDate(2026, 2, 12), range.ideal) // Jan 1 + 42d
    assertTrue(range.isDue(LocalDate(2026, 2, 12)))
    assertTrue(range.isOverdue(LocalDate(2026, 3, 1)))
    assertTrue(range.isUpcoming(LocalDate(2026, 2, 1)))
  }
}

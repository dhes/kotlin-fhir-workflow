package dev.ohs.fhir.workflow.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

sealed class ScheduleExpression {
  data class OffsetFromAnchor(
    val anchor: LocalDate,
    val offset: DatePeriod,
    val before: DatePeriod = DatePeriod(),
    val after: DatePeriod = DatePeriod(),
  ) : ScheduleExpression()
}

object ScheduleEvaluator {
  fun evaluate(schedule: ScheduleExpression): DateRange =
    when (schedule) {
      is ScheduleExpression.OffsetFromAnchor -> {
        val ideal = schedule.anchor.plus(schedule.offset)
        DateRange(
          earliest = ideal.minus(schedule.before),
          ideal = ideal,
          latest = ideal.plus(schedule.after),
        )
      }
    }
}

package dev.ohs.fhir.workflow.schedule

import kotlinx.datetime.LocalDate

data class DateRange(val earliest: LocalDate, val ideal: LocalDate, val latest: LocalDate) {
  fun isDue(today: LocalDate) = today in earliest..latest
  fun isOverdue(today: LocalDate) = today > latest
  fun isUpcoming(today: LocalDate) = today < earliest
}

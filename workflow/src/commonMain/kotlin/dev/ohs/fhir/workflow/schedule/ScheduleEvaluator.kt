/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

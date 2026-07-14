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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate

class ScheduleEvaluatorTest {
  @Test
  fun shouldReturnWindowAroundSixWeeksWhenScheduledAfterBirth() {
    val birth = LocalDate(2026, 1, 1)
    val range =
      ScheduleEvaluator.evaluate(
        ScheduleExpression.OffsetFromAnchor(
          anchor = birth,
          offset = DatePeriod(days = 42),
          before = DatePeriod(days = 7),
          after = DatePeriod(days = 14),
        )
      )
    assertEquals(LocalDate(2026, 2, 12), range.ideal) // Jan 1 + 42d
    assertTrue(range.isDue(LocalDate(2026, 2, 12)))
    assertTrue(range.isOverdue(LocalDate(2026, 3, 1)))
    assertTrue(range.isUpcoming(LocalDate(2026, 2, 1)))
  }
}

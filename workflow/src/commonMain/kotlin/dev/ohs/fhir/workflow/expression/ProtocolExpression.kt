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
package dev.ohs.fhir.workflow.expression

sealed class ProtocolExpression {
  /** text/fhirpath — the reference language. */
  data class FhirPath(val expression: String) : ProtocolExpression()

  /**
   * application/elm+json — evaluated only when an ELM evaluator is available (deferred).
   *
   * [reference] carries `Expression.reference` — the canonical URL of the Library the expression
   * belongs to — so an evaluator holding several libraries can address the right one. Null when
   * the authored Expression names no library; evaluators then use their own default.
   */
  data class Elm(val elmJson: String, val reference: String? = null) : ProtocolExpression()
}

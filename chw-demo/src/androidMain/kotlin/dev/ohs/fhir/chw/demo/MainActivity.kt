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
package dev.ohs.fhir.chw.demo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope

/**
 * CHW Home Visit demo: an Ona-style task list whose Immunizations row is computed on-device
 * by WHO's published measles CQL (see MeaslesEngine).
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        var engine by remember { mutableStateOf<MeaslesEngine?>(null) }
        var engineError by remember { mutableStateOf<String?>(null) }
        var selected by remember { mutableStateOf(DEMO_PATIENTS.first()) }
        var statuses by
          remember { mutableStateOf(mapOf<String, MeaslesEngine.MeaslesStatus>()) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
          try {
            val built =
              withContext(Dispatchers.Default) { MeaslesEngine.create(assets, PATIENT_BUNDLE) }
            engine = built
          } catch (t: Throwable) {
            Log.e(TAG, "engine init failed", t)
            engineError = "WHO CQL failed to load: ${t.message?.take(120)}"
          }
        }

        // Evaluate the selected patient once the engine is up; cache per patient.
        LaunchedEffect(engine, selected) {
          val e = engine ?: return@LaunchedEffect
          if (selected.id in statuses) return@LaunchedEffect
          val status = withContext(Dispatchers.Default) { e.evaluate(selected.id) }
          Log.i(
            TAG,
            "[${selected.id}] due MCV1=${status.dueMcv1} due MCV2=${status.dueMcv2} " +
              "complete=${status.seriesComplete} (${status.evalMillis} ms)",
          )
          statuses = statuses + (selected.id to status)
        }

        val rowState =
          when {
            engineError != null -> ImmunizationRowState.Failed(engineError!!)
            else ->
              statuses[selected.id]?.let { ImmunizationRowState.Ready(it) }
                ?: ImmunizationRowState.Compiling
          }

        HomeVisitScreen(
          selectedPatient = selected,
          onSelectPatient = { patient -> scope.launch { selected = patient } },
          immunizationState = rowState,
        )
      }
    }
  }

  private companion object {
    const val TAG = "ChwDemo"
  }
}

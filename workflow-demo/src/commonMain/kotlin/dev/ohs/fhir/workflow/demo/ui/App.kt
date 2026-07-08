package dev.ohs.fhir.workflow.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ohs.fhir.workflow.demo.data.EngineWorkflowRepository
import dev.ohs.fhir.workflow.demo.data.fhirEngine
import dev.ohs.fhir.workflow.demo.flow.ActivityFlowDemoModel
import kotlinx.coroutines.launch

/**
 * The demo's single screen: seeds a medication proposal and walks it through the
 * [dev.ohs.fhir.workflow.activity.ActivityFlow] phases, rendering each phase's resource.
 */
@Composable
fun App(platformContext: Any = Unit) {
  DemoTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      val repository = remember { EngineWorkflowRepository(fhirEngine(platformContext)) }
      val model = remember { ActivityFlowDemoModel(repository) }
      val scope = rememberCoroutineScope()

      var cards by remember { mutableStateOf(model.phaseCards()) }
      var started by remember { mutableStateOf(false) }

      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("ActivityFlow Demo", style = MaterialTheme.typography.headlineSmall)
        Text(
          "Proposal created directly; CQL/\$apply generation deferred.",
          style = MaterialTheme.typography.bodySmall,
        )
        Button(
          onClick = {
            scope.launch {
              model.restart()
              started = false
              cards = model.phaseCards()
            }
          }
        ) {
          Text("Restart")
        }

        cards.forEach { card ->
          Card(modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text("Phase: ${card.name}", style = MaterialTheme.typography.titleMedium)
              SelectionContainer {
                Text(card.details, fontFamily = FontFamily.Monospace)
              }
              Button(
                enabled = card.isActive,
                onClick = {
                  scope.launch {
                    if (!started) {
                      model.createProposal("apple-guy")
                      started = true
                    } else {
                      model.advance()
                    }
                    cards = model.phaseCards()
                  }
                },
              ) {
                Text("Advance")
              }
            }
          }
        }
      }
    }
  }
}

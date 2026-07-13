package dev.ohs.fhir.workflow.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ohs.fhir.workflow.demo.data.demoWorkflowRepository
import dev.ohs.fhir.workflow.demo.flow.ActivityFlowDemoModel
import dev.ohs.fhir.workflow.demo.flow.FlowPhase
import dev.ohs.fhir.workflow.demo.flow.PhaseCard
import kotlinx.coroutines.launch

/**
 * The demo's single screen: installs the DailyApple knowledge artifacts, generates a proposal from
 * them with `PlanDefinition/$apply`, and walks it through the
 * [dev.ohs.fhir.workflow.activity.ActivityFlow] phases.
 */
@Composable
fun App(platformContext: Any = Unit) {
  DemoTheme {
    val repository = remember { demoWorkflowRepository(platformContext) }
    val model = remember { ActivityFlowDemoModel(repository) }
    val scope = rememberCoroutineScope()

    val cards by model.cards.collectAsState()
    val progress by model.progress.collectAsState()
    val initialized by model.initialized.collectAsState()

    LaunchedEffect(Unit) { model.refresh() }

    Scaffold(topBar = { DemoTopBar() }) { insets ->
      Box(modifier = Modifier.fillMaxSize().padding(insets)) {
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          PatientCard()

          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
              enabled = !initialized && !progress,
              onClick = { scope.launch { model.installDependencies() } },
            ) {
              Text("Initialize")
            }
            OutlinedButton(
              enabled = !progress,
              onClick = { scope.launch { model.restart() } },
            ) {
              Text("Restart Flow")
            }
          }

          PhaseSection(
            cards = cards,
            enabled = !progress,
            onStart = { started -> scope.launch { model.start(started) } },
          )
        }

        if (progress) {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
      }
    }
  }
}

@Composable
private fun DemoTopBar() {
  var menuOpen by remember { mutableStateOf(false) }
  TopAppBar(
    title = { Text("WorkflowDemo") },
    actions = {
      IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Activity flow types")
      }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        // The only flow the demo ships, as upstream: an apple a day, dispensed.
        DropdownMenuItem(text = { Text("Order Medication") }, onClick = { menuOpen = false })
      }
    },
  )
}

@Composable
private fun PatientCard() {
  Card(modifier = Modifier.fillMaxWidth()) {
    Text(
      "Mr. John Doe Sr.",
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(16.dp),
    )
  }
}

/**
 * The four phase cards. Upstream is phone-only and pages through them one at a time, so narrow
 * windows get the same pager and dot indicators; wide ones lay all four out side by side instead of
 * stretching a single card across a desktop window.
 */
@Composable
private fun PhaseSection(
  cards: List<PhaseCard>,
  enabled: Boolean,
  onStart: (FlowPhase) -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    if (maxWidth < 600.dp) {
      val pagerState = rememberPagerState(pageCount = { cards.size })
      val activeIndex = cards.indexOfFirst { it.isActive }

      LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) pagerState.animateScrollToPage(activeIndex)
      }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
          PhaseCardView(cards[page], enabled, onStart, Modifier.fillMaxWidth().padding(4.dp))
        }
        PagerDots(count = cards.size, selected = pagerState.currentPage)
      }
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.forEach { card ->
          PhaseCardView(card, enabled, onStart, Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun PhaseCardView(
  card: PhaseCard,
  enabled: Boolean,
  onStart: (FlowPhase) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier) {
    Column(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        "Phase: ${card.phase.name}",
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        enabled = enabled && card.isActive,
        onClick = { onStart(card.phase) },
        modifier = Modifier.align(Alignment.End),
      ) {
        Text("Start")
      }
      SelectionContainer {
        Text(
          card.details,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
        )
      }
    }
  }
}

/** Upstream's TabLayout page indicator: an outlined ring per page, filled for the current one. */
@Composable
private fun PagerDots(count: Int, selected: Int) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
  ) {
    repeat(count) { page ->
      val dot = Modifier.size(10.dp)
      Box(
        modifier = if (page == selected) {
          dot.background(MaterialTheme.colorScheme.primary, CircleShape)
        } else {
          dot.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
        },
      )
    }
  }
}

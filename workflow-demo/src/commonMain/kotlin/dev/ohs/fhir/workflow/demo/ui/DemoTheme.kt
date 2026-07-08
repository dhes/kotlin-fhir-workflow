package dev.ohs.fhir.workflow.demo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** The demo app's theme wrapper; a plain Material 3 theme is sufficient here. */
@Composable
fun DemoTheme(content: @Composable () -> Unit) {
  MaterialTheme(content = content)
}

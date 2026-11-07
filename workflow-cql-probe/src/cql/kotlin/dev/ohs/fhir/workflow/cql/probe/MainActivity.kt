package dev.ohs.fhir.workflow.cql.probe

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.widget.TextView
import java.io.File
import kotlin.concurrent.thread

/** Probe only: copies WHO's files out of the APK once, then runs [Probe] and logs each step. */
class MainActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val text = TextView(this).apply { text = "Running; see logcat tag CQLPROBE" }
    setContentView(text)
    thread(name = "probe") {
      val who = File(filesDir, "who")
      if (!File(who, "done").exists()) {
        copyAssets("who", who)
        File(who, "done").writeText("")
      }
      val lines = mutableListOf<String>()
      fun log(line: String) {
        Log.i("CQLPROBE", line)
        lines += line
      }
      try {
        Probe(File(who, "plandefinition"), File(who, "package"), ::log).run()
      } catch (e: Throwable) {
        log("FAILED ${e::class.simpleName}: ${e.message}")
      }
      val runtime = Runtime.getRuntime()
      log("MEMORY | Java heap used ${(runtime.totalMemory() - runtime.freeMemory()) / 1_000_000} MB, " +
        "max ${runtime.maxMemory() / 1_000_000} MB; PSS ${Debug.getPss() / 1000} MB")
      log("DONE")
      runOnUiThread { text.text = lines.joinToString("\n") }
    }
  }

  private fun copyAssets(path: String, target: File) {
    val children = assets.list(path).orEmpty()
    if (children.isEmpty()) {
      target.parentFile?.mkdirs()
      assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
    } else {
      target.mkdirs()
      children.forEach { copyAssets("$path/$it", File(target, it)) }
    }
  }
}

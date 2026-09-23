package com.treg.llmpersonalization.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.treg.llmpersonalization.AppState

/**
 * Phase D2 test harness receiver. Accepts broadcasts of the form:
 *   adb shell am broadcast -a com.treg.llmpersonalization.TEST_QUERY \
 *     --es id "C1-01" --es query "What is my dog's name?" --es user "treg" --ei category 1
 *
 * Runs the query through ChatOrchestrator and emits a result line to logcat
 * under tag TestQuery. This receiver should NOT ship to production — it opens
 * the app to external broadcast input. Disable for release builds.
 */
class TestQueryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val query = intent.getStringExtra("q64")?.let {
            try {
                String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e("TestQuery", "id=$id ERROR bad base64: ${e.message}")
                return
            }
        } ?: intent.getStringExtra("query") ?: return
        val user = intent.getStringExtra("user") ?: "treg"
        val category = intent.getIntExtra("category", 0)

        val pending = goAsync()
        Thread {
            try {
                val state = AppState.instance
                if (state == null) {
                    Log.e("TestQuery", "id=$id cat=$category ERROR no AppState")
                    return@Thread
                }
                val l = state.llama
                val e = state.extractor
                val b = state.beliefs
                if (l == null || e == null || b == null) {
                    Log.e("TestQuery", "id=$id cat=$category ERROR bridges not ready")
                    return@Thread
                }

                val orch = ChatOrchestrator(l, e, b, state.memoryStore, state.embedder, state.reranker)
                val t0 = System.currentTimeMillis()
                val trace = orch.chat(
                    query, user,
                    softGate = state.softGate,
                    strictGate = state.strictGate,
                    useBeliefs = state.useBeliefs,
                    useMining = state.useMining
                )
                val ms = System.currentTimeMillis() - t0

                val resp = trace.response.replace("\n", " ").replace("'", "`").take(250)
                val top = trace.topMemory ?: "-"
                val score = trace.topScore?.let { "%.3f".format(it) } ?: "-"
                val tools = trace.toolResults?.size ?: 0

                Log.i("TestQuery",
                    "id=$id cat=$category ms=$ms path=${trace.path} " +
                    "top=$top score=$score tools=$tools resp='$resp'"
                )
            } catch (t: Throwable) {
                Log.e("TestQuery", "id=$id cat=$category ERROR ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
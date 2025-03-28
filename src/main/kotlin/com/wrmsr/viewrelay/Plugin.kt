/*
TODO:
 - multiple carets
 - honor columns in visible
 - server

==

https://plugins.jetbrains.com/plugin/17669-flora-beta-
https://github.com/dkandalov/live-plugin
*/
package com.wrmsr.viewrelay

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.*
import kotlinx.serialization.json.*

//

@Service
class ViewRelayService : Disposable {
    override fun dispose() {
        shutdown()
    }

    //

    private val logger = PrintlnLogger()

    @Volatile private var hasSetup = false

    private val thinker = ViewRelayThinker(logger = logger)
    private val listeners = ViewRelayListeners({ updateState(it) }, this)
    private val server = ViewRelayServer(8081, logger = logger)

    @Synchronized
    fun setup() {
        if (hasSetup) {
            return
        }
        hasSetup = true

        thinker.start()

        listeners.install()

        server.start()
    }

    @Synchronized
    fun shutdown() {
        thinker.stop()
    }

    //

    private val lastState = AtomicReference<ViewRelayState?>(null)

    private fun updateState(editor: Editor) {
        val newState = ViewRelayState.fromEditor(editor) ?: return

        val prevState = lastState.getAndSet(newState)

        if (newState != prevState) {
            reportState(newState)
        }
    }

    private fun reportState(state: ViewRelayState) {
        val json = Json { prettyPrint = true }
        val js = json.encodeToString(state)
        println(js)
    }
}

//

class ViewRelayProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            service<ViewRelayService>().setup()
        }
    }
}

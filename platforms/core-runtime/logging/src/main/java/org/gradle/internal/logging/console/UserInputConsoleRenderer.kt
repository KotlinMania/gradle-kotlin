/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.logging.console

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.RenderableOutputEvent

class UserInputConsoleRenderer(delegate: OutputEventListener, private val console: Console, userInput: GlobalUserInputReceiver, temporaryFileProvider: TemporaryFileProvider) :
    AbstractUserInputRenderer(delegate, userInput, temporaryFileProvider) {
    override fun startInput() {
        toggleBuildProgressAreaVisibility(false)
        flushConsole()
    }

    override fun handlePrompt(event: RenderableOutputEvent) {
        event.render(console.buildOutputArea!!)
        flushConsole()
    }

    override fun finishInput(event: RenderableOutputEvent) {
        event.render(console.buildOutputArea!!)
        toggleBuildProgressAreaVisibility(true)
        flushConsole()
    }

    private fun toggleBuildProgressAreaVisibility(visible: Boolean) {
        console.buildProgressArea!!.setVisible(visible)
    }

    private fun flushConsole() {
        console.flush()
    }
}

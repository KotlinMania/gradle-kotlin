/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.base.CharMatcher
import org.apache.commons.lang3.StringUtils
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.PromptOutputEvent
import org.gradle.internal.logging.events.ReadStdInEvent
import org.gradle.internal.logging.events.UserInputValidationProblemEvent
import java.util.concurrent.atomic.AtomicReference

class DefaultUserInputReceiver : GlobalUserInputReceiver {
    private val delegate = AtomicReference<UserInputReceiver>()
    private var console: OutputEventListener? = null

    /**
     * This instance requires access to the "console" pipeline in order to prompt the user when there are validation problems.
     * However, the console pipeline also needs access to this instance in order to handle user input prompt requests.
     * Break this cycle for now using a non-final field.
     */
    fun attachConsole(console: OutputEventListener) {
        this.console = console
    }

    override fun readAndForwardText(event: PromptOutputEvent) {
        val userInput = getDelegate()
        userInput.readAndForwardText(object : UserInputReceiver.Normalizer {
            override fun normalize(text: String): String? {
                val result = event.convert(CharMatcher.javaIsoControl().removeFrom(StringUtils.trim(text)))
                if (result.newPrompt != null) {
                    // Need to prompt the user again
                    console!!.onOutput(UserInputValidationProblemEvent(event.timestamp, result.newPrompt))
                    return null
                } else {
                    // Send result
                    return result.response.toString()
                }
            }
        })
    }

    override fun readAndForwardStdin(event: ReadStdInEvent) {
        val userInput = getDelegate()
        userInput.readAndForwardStdin()
    }

    private fun getDelegate(): UserInputReceiver {
        val userInput = delegate.get()
        checkNotNull(userInput) { "User input has not been initialized." }
        return userInput
    }

    override fun dispatchTo(userInput: UserInputReceiver) {
        check(delegate.compareAndSet(null, userInput)) { "User input has already been initialized." }
    }

    override fun stopDispatching() {
        delegate.set(null)
    }
}

/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.text

import org.gradle.api.logging.StandardOutputListener
import java.io.Closeable
import java.io.IOException

/**
 * A [StyledTextOutput] implementation which writes text to some char stream. Ignores any
 * styling information.
 */
class StreamingStyledTextOutput private constructor(target: Any, private val listener: StandardOutputListener) : AbstractStyledTextOutput(), Closeable {
    private val closeable: Closeable?

    /**
     * Creates a text output which forwards text to the given listener.
     * @param listener The listener.
     */
    constructor(listener: StandardOutputListener) : this(listener, listener)

    /**
     * Creates a text output which writes text to the given appendable.
     * @param appendable The appendable.
     */
    constructor(appendable: Appendable) : this(appendable, StreamBackedStandardOutputListener(appendable))

    init {
        closeable = if (target is Closeable) target else null
    }

    override fun doAppend(text: String?) {
        listener.onOutput(text)
    }

    /**
     * Closes the target object if it implements [Closeable].
     */
    @Throws(IOException::class)
    override fun close() {
        if (closeable != null) {
            closeable.close()
        }
    }
}

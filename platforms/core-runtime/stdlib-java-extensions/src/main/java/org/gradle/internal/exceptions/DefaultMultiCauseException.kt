/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.exceptions

import org.gradle.api.GradleException
import org.gradle.internal.Factory
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.util.Arrays
import java.util.concurrent.CopyOnWriteArrayList

open class DefaultMultiCauseException : GradleException, MultiCauseException, NonGradleCauseExceptionsHolder {
    private val causes: MutableList<Throwable> = CopyOnWriteArrayList<Throwable>()

    @Transient
    private var hideCause: ThreadLocal<Boolean> = threadLocal()

    @Transient
    private var messageFactory: Factory<String?>? = null
    private var messageValue: String? = null

    constructor(message: String?) : super(message) {
        this.messageValue = message
    }

    constructor(message: String?, vararg causes: Throwable?) : super(message) {
        this.messageValue = message
        for (cause in causes) {
            if (cause != null) {
                this.causes.add(cause)
            }
        }
    }

    constructor(message: String?, causes: Iterable<out Throwable?>) : super(message) {
        this.messageValue = message
        initCauses(causes)
    }

    constructor(messageFactory: Factory<String?>?) {
        this.messageFactory = messageFactory
    }

    constructor(messageFactory: Factory<String?>?, vararg causes: Throwable?) : this(messageFactory) {
        for (cause in causes) {
            if (cause != null) {
                this.causes.add(cause)
            }
        }
    }

    constructor(messageFactory: Factory<String?>?, causes: Iterable<out Throwable?>) : this(messageFactory) {
        initCauses(causes)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(inputStream: ObjectInputStream) {
        inputStream.defaultReadObject()
        hideCause = threadLocal()
    }

    @Throws(IOException::class)
    private fun writeObject(out: ObjectOutputStream) {
        // Ensure fields are initialized before serialization
        val ignored = messageValue
        out.defaultWriteObject()
    }

    private fun threadLocal(): ThreadLocal<Boolean> {
        return HideStacktrace()
    }

    override fun getResolutions(): MutableList<String?> {
        val resolutions: MutableList<String?> = ArrayList<String?>(causes.size) // Typical case is 0 or 1 resolutions/cause
        for (cause in causes) {
            if (cause is ResolutionProvider) {
                resolutions.addAll((cause as ResolutionProvider).getResolutions())
            }
        }
        return resolutions
    }

    private class HideStacktrace : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean {
            return false
        }
    }

    override fun getCauses(): MutableList<out Throwable> {
        return causes
    }

    @Synchronized
    override fun initCause(throwable: Throwable?): Throwable {
        causes.clear()
        causes.add(throwable!!)
        return this
    }

    fun initCauses(causes: Iterable<out Throwable?>) {
        this.causes.clear()
        for (cause in causes) {
            if (cause != null) {
                this.causes.add(cause)
            }
        }
    }

    @get:Synchronized
    override val cause: Throwable?
        get() {
            if (hideCause.get()) {
                return null
            }
            return if (causes.isEmpty()) null else causes.get(0)
        }

    override fun printStackTrace(printStream: PrintStream) {
        val writer = PrintWriter(printStream)
        printStackTrace(writer)
        writer.flush()
    }

    override fun printStackTrace(printWriter: PrintWriter) {
        if (causes.isEmpty()) {
            super.printStackTrace(printWriter)
            return
        }

        hideCause.set(true)
        try {
            super.printStackTrace(printWriter)

            if (causes.size == 1) {
                printSingleCauseStackTrace(printWriter)
            } else {
                printMultiCauseStackTrace(printWriter)
            }
        } finally {
            hideCause.set(false)
        }
    }

    private fun printSingleCauseStackTrace(printWriter: PrintWriter) {
        val cause: Throwable = causes.get(0)
        printWriter.print("Caused by: ")
        cause.printStackTrace(printWriter)
    }

    private fun printMultiCauseStackTrace(printWriter: PrintWriter) {
        for (i in causes.indices) {
            val cause: Throwable = causes.get(i)
            printWriter.format("Cause %s: ", i + 1)
            cause.printStackTrace(printWriter)
        }
    }

    override val message: String?
        get() {
        if (messageFactory != null) {
            messageValue = messageFactory!!.create()
            messageFactory = null
            return messageValue
        }
        return messageValue
    }

    override fun hasCause(type: Class<*>?): Boolean {
        for (cause in getCauses()) {
            if (cause == null) {
                continue
            }
            if (cause is NonGradleCauseExceptionsHolder) {
                val hasCauseOfType = (cause as NonGradleCauseExceptionsHolder).hasCause(type)
                if (hasCauseOfType) {
                    return true
                }
            } else if (type != null && type.isInstance(cause)) {
                return true
            }
        }
        return false
    }
}

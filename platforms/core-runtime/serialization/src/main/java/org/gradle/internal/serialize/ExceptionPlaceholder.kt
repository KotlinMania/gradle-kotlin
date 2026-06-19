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
package org.gradle.internal.serialize

import org.gradle.api.JavaVersion
import org.gradle.internal.UncheckedException
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.io.StreamByteBuffer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.lang.reflect.Constructor
import java.util.Arrays
import java.util.function.Function

open class ExceptionPlaceholder(original: Throwable, objectOutputStreamCreator: Function<OutputStream?, ExceptionReplacingObjectOutputStream>, dejaVu: MutableSet<Throwable>) : Serializable {
    private val type: String
    private var serializedException: ByteArray? = null
    private var message: String? = null
    private var toString: String? = null
    private val contextual: Boolean
    private val assertionError: Boolean
    private val causes: MutableList<ExceptionPlaceholder>
    private val suppressed: MutableList<ExceptionPlaceholder>
    private var stackTrace: MutableList<StackTraceElementPlaceholder> = mutableListOf()
    private var toStringRuntimeExec: Throwable? = null
    private var getMessageExec: Throwable? = null

    init {
        val hasCycle = !dejaVu.add(original)
        var throwable = original
        type = throwable.javaClass.getName()
        contextual = throwable.javaClass.getAnnotation<Contextual?>(Contextual::class.java) != null
        assertionError = throwable is AssertionError
        try {
            stackTrace = convertStackTrace(throwable.stackTrace)
        } catch (ignored: Throwable) {
// TODO:ADAM - switch the logging back on. Need to make sending messages from daemon to client async wrt log event generation
//                LOGGER.debug("Ignoring failure to extract throwable stack trace.", ignored);
            stackTrace = mutableListOf()
        }

        try {
            message = throwable.message
        } catch (failure: Throwable) {
            getMessageExec = failure
        }

        if (isJava14) {
            throwable = Java14NullPointerExceptionUsefulMessageSupport.maybeReplaceUsefulNullPointerMessage(throwable)
        }

        try {
            toString = throwable.toString()
        } catch (failure: Throwable) {
            toStringRuntimeExec = failure
        }

        val causes: MutableList<Throwable>
        val suppressed: MutableList<Throwable>
        if (hasCycle) {
            // Ignore causes and suppressed in case of cycle
            causes = mutableListOf()
            suppressed = mutableListOf()
        } else {
            causes = ExceptionSerializationUtil.extractCauses(throwable)
            suppressed = extractSuppressed(throwable)
        }

        val buffer = StreamByteBuffer()
        val oos = objectOutputStreamCreator.apply(buffer.outputStream)
        oos.objectTransformer = object : Function<Any?, Any?> {
            var seenFirst: Boolean = false

            override fun apply(obj: Any?): Any? {
                if (!seenFirst) {
                    seenFirst = true
                    return obj
                }
                // Don't serialize the causes - we'll serialize them separately later
                val causeIndex = causes.indexOf(obj)
                if (causeIndex >= 0) {
                    return NestedExceptionPlaceholder(NestedExceptionPlaceholder.Kind.cause, causeIndex)
                }
                val suppressedIndex = suppressed.indexOf(obj)
                if (suppressedIndex >= 0) {
                    return NestedExceptionPlaceholder(NestedExceptionPlaceholder.Kind.suppressed, suppressedIndex)
                }
                return obj
            }
        }

        try {
            oos.writeObject(throwable)
            oos.close()
            serializedException = buffer.readAsByteArray()
        } catch (ignored: Throwable) {
// TODO:ADAM - switch the logging back on.
//                LOGGER.debug("Ignoring failure to serialize throwable.", ignored);
        }

        this.causes = convertToExceptionPlaceholderList(causes, objectOutputStreamCreator, dejaVu)
        this.suppressed = convertToExceptionPlaceholderList(suppressed, objectOutputStreamCreator, dejaVu)
    }

    private fun convertStackTrace(stackTrace: Array<StackTraceElement>): MutableList<StackTraceElementPlaceholder> {
        val placeholders: MutableList<StackTraceElementPlaceholder> = ArrayList<StackTraceElementPlaceholder>(stackTrace.size)
        for (stackTraceElement in stackTrace) {
            placeholders.add(StackTraceElementPlaceholder(stackTraceElement))
        }
        return placeholders
    }

    private fun convertStackTrace(placeholders: MutableList<StackTraceElementPlaceholder>): Array<StackTraceElement> {
        val stackTrace = arrayOfNulls<StackTraceElement>(placeholders.size)
        for (i in placeholders.indices) {
            stackTrace[i] = placeholders[i].toStackTraceElement()
        }
        return stackTrace.requireNoNulls()
    }

    @Throws(IOException::class)
    fun read(classNameTransformer: Function<String, Class<*>?>, objectInputStreamCreator: Function<InputStream?, ExceptionReplacingObjectInputStream>): Throwable {
        val causes: MutableList<Throwable> = recreateExceptions(this.causes, classNameTransformer, objectInputStreamCreator)
        val suppressed: MutableList<Throwable> = recreateExceptions(this.suppressed, classNameTransformer, objectInputStreamCreator)

        if (serializedException != null) {
            // try to deserialize the original exception
            val ois = objectInputStreamCreator.apply(ByteArrayInputStream(serializedException))
            ois.objectTransformer = Function { obj: Any? ->
                if (obj is NestedExceptionPlaceholder) {
                    val placeholder = obj
                    val index = placeholder.index
                    when (placeholder.kind) {
                        NestedExceptionPlaceholder.Kind.cause -> causes[index]
                        NestedExceptionPlaceholder.Kind.suppressed -> suppressed[index]
                        else -> obj
                    }
                } else {
                    obj
                }
            }

            try {
                return ois.readObject() as Throwable
            } catch (ignored: ClassNotFoundException) {
                // Don't log
            } catch (failure: Throwable) {
                LOGGER.debug("Ignoring failure to de-serialize throwable.", failure)
            }
        }

        try {
            // try to reconstruct the exception
            val clazz = classNameTransformer.apply(type)
            if (clazz != null && causes.size <= 1) {
                val constructor: Constructor<*> = clazz.getConstructor(String::class.java)
                val reconstructed = constructor.newInstance(message) as Throwable
                if (!causes.isEmpty()) {
                    reconstructed.initCause(causes.get(0))
                }
                reconstructed.stackTrace = convertStackTrace(stackTrace)
                registerSuppressedExceptions(suppressed, reconstructed)
                return reconstructed
            }
        } catch (ignore: UncheckedException) {
            // Don't log
        } catch (ignored: NoSuchMethodException) {
            // Don't log
        } catch (ignored: Throwable) {
            LOGGER.debug("Ignoring failure to recreate throwable.", ignored)
        }

        val placeholder: Throwable
        if (causes.size <= 1) {
            if (contextual) {
                // there are no @Contextual assertion errors in Gradle so we're safe to use this type only
                placeholder = ContextualPlaceholderException(type, message, getMessageExec, toString, toStringRuntimeExec, if (causes.isEmpty()) null else causes.get(0))
            } else {
                if (assertionError) {
                    placeholder = PlaceholderAssertionError(type, message, getMessageExec, toString, toStringRuntimeExec, if (causes.isEmpty()) null else causes.get(0))
                } else {
                    placeholder = PlaceholderException(type, message, getMessageExec, toString, toStringRuntimeExec, if (causes.isEmpty()) null else causes.get(0))
                }
            }
        } else {
            placeholder = DefaultMultiCauseException(message, causes)
        }
        placeholder.stackTrace = convertStackTrace(stackTrace)
        registerSuppressedExceptions(suppressed, placeholder)
        return placeholder
    }

    /**
     * A support utility which will replace the message of NullPointerException
     * thrown in Java 14+ with the "useful" one when it's not using a custom
     * message.
     *
     * We have to do this because Java 14 will not serialize the required context
     * and therefore when the exception is sent back to the daemon, it loses
     * information required to create a "useful message".
     */
    private object Java14NullPointerExceptionUsefulMessageSupport {
        fun maybeReplaceUsefulNullPointerMessage(throwable: Throwable): Throwable {
            var throwable = throwable
            if (throwable is NullPointerException) {
                val stackTrace = throwable.getStackTrace()
                try {
                    throwable = NullPointerException(throwable.message)
                } catch (e: Exception) {
                    // if calling `getMessage()` fails for whatever reason, just ignore
                    // the replacement
                    return throwable
                }
                throwable.setStackTrace(stackTrace)
            }
            return throwable
        }
    }

    companion object {
        private const val serialVersionUID = 1L
        private val LOGGER: Logger = LoggerFactory.getLogger(ExceptionPlaceholder::class.java)
        private fun extractSuppressed(throwable: Throwable): MutableList<Throwable> {
            if (isJava7) {
                return Arrays.asList(*throwable.suppressed)
            }
            return mutableListOf()
        }

        // TODO Use only immutable collections
        private fun convertToExceptionPlaceholderList(
            throwables: MutableList<Throwable>,
            objectOutputStreamCreator: Function<OutputStream?, ExceptionReplacingObjectOutputStream>,
            dejaVu: MutableSet<Throwable>
        ): MutableList<ExceptionPlaceholder> {
            if (throwables.isEmpty()) {
                return mutableListOf()
            } else if (throwables.size == 1) {
                return mutableListOf(ExceptionPlaceholder(throwables[0], objectOutputStreamCreator, dejaVu))
            } else {
                val placeholders: MutableList<ExceptionPlaceholder> = ArrayList<ExceptionPlaceholder>(throwables.size)
                for (cause in throwables) {
                    placeholders.add(ExceptionPlaceholder(cause, objectOutputStreamCreator, dejaVu))
                }
                return placeholders
            }
        }

        private val isJava7: Boolean
            get() = JavaVersion.current().isJava7Compatible

        private val isJava14: Boolean
            get() = JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14)

        private fun registerSuppressedExceptions(suppressed: MutableList<Throwable>, reconstructed: Throwable) {
            if (!suppressed.isEmpty()) {
                for (throwable in suppressed) {
                    reconstructed.addSuppressed(throwable)
                }
            }
        }

        @Throws(IOException::class)  // TODO Use only immutable collections
        private fun recreateExceptions(
            exceptions: MutableList<ExceptionPlaceholder>,
            classNameTransformer: Function<String, Class<*>?>,
            objectInputStreamCreator: Function<InputStream?, ExceptionReplacingObjectInputStream>
        ): MutableList<Throwable> {
            if (exceptions.isEmpty()) {
                return mutableListOf()
            } else if (exceptions.size == 1) {
                return mutableListOf(exceptions[0].read(classNameTransformer, objectInputStreamCreator))
            }
            val result: MutableList<Throwable> = ArrayList<Throwable>()
            for (placeholder in exceptions) {
                result.add(placeholder.read(classNameTransformer, objectInputStreamCreator))
            }
            return result
        }
    }
}

/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.internal.Cast
import org.gradle.internal.exceptions.MultiCauseException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Collections

/**
 * Static util class containing helper methods for exception serialization.
 */
object ExceptionSerializationUtil {
    // It would be nice to use Guava's immutable collections here, if we could get them on the proper classpath
    val CANDIDATE_GET_CAUSES: MutableSet<String> = Collections.unmodifiableSet<String>(HashSet<String>(mutableListOf("getCauses", "getFailures")))

    @JvmStatic
    fun extractCauses(throwable: Throwable): MutableList<Throwable> {
        if (throwable is MultiCauseException) {
            return throwable.getCauses().filterNotNull().toMutableList()
        } else {
            val causes = tryExtractMultiCauses(throwable)
            if (causes != null) {
                return causes
            }
            var causeTmp: Throwable?
            try {
                causeTmp = throwable.cause
            } catch (ignored: Throwable) {
                // TODO:ADAM - switch the logging back on.
                //                LOGGER.debug("Ignoring failure to extract throwable cause.", ignored);
                causeTmp = null
            }
            return if (causeTmp == null) mutableListOf() else mutableListOf(causeTmp)
        }
    }

    /**
     * Does best effort to find a method which potentially returns multiple causes
     * for an exception. This is for classes of external projects which actually do
     * something similar to what we do in Gradle with [DefaultMultiCauseException].
     * It is, in particular, the case for opentest4j.
     */
    private fun tryExtractMultiCauses(throwable: Throwable): MutableList<Throwable>? {
        val causesMethod = findCandidateGetCausesMethod(throwable)
        if (causesMethod != null) {
            val causes: MutableCollection<*>?
            try {
                causes = Cast.uncheckedCast<MutableCollection<*>?>(causesMethod.invoke(throwable))
            } catch (e: IllegalAccessException) {
                return null
            } catch (e: InvocationTargetException) {
                return null
            }
            if (causes == null || causes.isEmpty()) {
                return null
            }
            for (cause in causes) {
                if (cause !is Throwable) {
                    return null
                }
            }
            val result: MutableList<Throwable> = ArrayList<Throwable>(causes.size)
            for (cause in causes) {
                result.add(Cast.uncheckedNonnullCast<Throwable>(cause))
            }
            return result
        }
        return null
    }

    private fun findCandidateGetCausesMethod(throwable: Throwable): Method? {
        val declaredMethods = throwable.javaClass.getDeclaredMethods()
        for (method in declaredMethods) {
            if (CANDIDATE_GET_CAUSES.contains(method.getName())) {
                val returnType = method.getReturnType()
                if (MutableCollection::class.java.isAssignableFrom(returnType)) {
                    return method
                }
            }
        }
        return null
    }
}

/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.problems.internal

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import com.google.common.collect.Multimaps
import org.gradle.api.Incubating
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Holds references to exceptions reported via [org.gradle.problems.buildtree.ProblemReporter] and their associated problem reports.
 *
 * @since 8.12
 */
@Incubating
@ServiceScope(Scope.BuildSession::class)
class ExceptionProblemRegistry {
    private val problemsForThrowables = Multimaps.synchronizedMultimap<Throwable, ProblemInternal>(MultimapBuilder.linkedHashKeys().linkedHashSetValues().build<Throwable, ProblemInternal>())

    fun onProblem(exception: Throwable, problem: ProblemInternal) {
        problemsForThrowables.put(exception, problem)
    }

    val problemLocator: ProblemLocator
        get() = DefaultProblemLocator(problemsForThrowables)

    /*
     * Workaround for the fact that the exception thrown by the worker is not the same instance as the one that was thrown by the build. With the lookup we can find the original exception by comparing
     * the stack frames. The comparison is expensive, so we only do when it's necessary (when the original exception does not contain the target and there's a matching class name and message).
     */
    private class DefaultProblemLocator(problemsForThrowables: Multimap<Throwable, ProblemInternal>) : ProblemLocator {
        private val problemsForThrowables: Multimap<Throwable, ProblemInternal>
        private var exceptionLookup: Multimap<String, Throwable>? = null

        init {
            this.problemsForThrowables = ImmutableMultimap.copyOf<Throwable, ProblemInternal>(problemsForThrowables)
        }

        fun exceptionLookup(): Multimap<String, Throwable> {
            if (exceptionLookup == null) {
                exceptionLookup = initLookup(this.problemsForThrowables.keySet())
            }
            return exceptionLookup!!
        }

        override fun findAll(t: Throwable): MutableCollection<ProblemInternal> {
            val throwable = find(t)
            return if (throwable == null) ImmutableList.of<ProblemInternal>() else ImmutableList.copyOf<ProblemInternal>(problemsForThrowables.get(throwable))
        }

        fun find(t: Throwable): Throwable? {
            try {
                if (problemsForThrowables.keySet().contains(t)) {
                    return t
                }
                val candidates = exceptionLookup().get(key(t))
                for (candidate in candidates) {
                    if (deepEquals(candidate, t, ArrayList<Throwable>())) {
                        return candidate
                    }
                }
            } catch (ignore: RuntimeException) {
                return null
            }
            return null
        }

        fun deepEquals(t1: Throwable, t2: Throwable?, seen: MutableList<Throwable>): Boolean {
            if (seen.contains(t1) || seen.contains(t2)) {
                return false // drop self-references to avoid infinite recursion
            }

            if (t1 == null && t2 == null) {
                return true // equals if both null
            } else if (t1 == null || t2 == null) {
                return false // either t1 or t2 is null
            }

            if (t1.javaClass != t2.javaClass || messageOf(t1) != messageOf(t2)) {
                return false
            }
            val s1 = t1.getStackTrace()
            val s2 = t2.getStackTrace()
            var i = 0
            while (i < s1.size && i < s2.size) {
                if (!isStackTraceElementEquals(s1[i], s2[i])) {
                    return false
                }
                i++
            }
            seen.add(t1)
            seen.add(t2)
            return deepEquals(t1.cause!!, t2.cause, seen)
        }

        fun isStackTraceElementEquals(s1: StackTraceElement, s2: StackTraceElement): Boolean {
            if (s1.getClassName() != s2.getClassName()) {
                return false
            }
            val s1File = s1.getFileName()
            val s2File = s2.getFileName()
            if ((s1File == null && s2File != null) || (s1File != null && s2File == null)) {
                return false
            } else if (s1File != null && s2File != null && (s1File != s2File)) {
                return false
            } else if (s1.getLineNumber() != s2.getLineNumber()) {
                return false
            }

            return true
        }

        companion object {
            private fun initLookup(exceptions: MutableSet<Throwable>): Multimap<String, Throwable> {
                val lookup: Multimap<String, Throwable> = ArrayListMultimap.create<String, Throwable>()
                for (exception in exceptions) {
                    lookup.put(key(exception), exception)
                }
                return lookup
            }

            private fun key(t: Throwable): String {
                return t.javaClass.getName() + ":" + messageOf(t)
            }

            private fun messageOf(t: Throwable): String {
                var result = ""
                try {
                    val message = t.message
                    result = if (message == null) "" else message
                } catch (ignore: RuntimeException) {
                    // ignore exceptions with faulty getMessage() implementation
                }
                return result
            }
        }
    }
}

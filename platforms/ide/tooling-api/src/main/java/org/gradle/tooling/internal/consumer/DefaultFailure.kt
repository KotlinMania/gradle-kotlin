/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.Failure
import org.gradle.tooling.events.problems.Problem
import java.io.PrintWriter
import java.io.StringWriter

open class DefaultFailure @JvmOverloads constructor(
    private val message: String?,
    private val description: String?,
    private val causes: MutableList<out Failure?>?,
    private val problems: MutableList<Problem?>? = mutableListOf<Problem?>()
) : Failure {
    override fun getMessage(): String? {
        return message
    }

    override fun getDescription(): String? {
        return description
    }

    override fun getCauses(): MutableList<out Failure?>? {
        return causes
    }

    override fun getProblems(): MutableList<Problem?>? {
        return problems
    }

    override fun toString(): String {
        return "DefaultFailure{" +
                "message='" + message + '\'' +
                ", description='" + description + '\'' +
                ", causes=" + causes +
                ", problems=" + problems +
                '}'
    }

    companion object {
        @JvmStatic
        fun fromThrowable(t: Throwable): DefaultFailure {
            val out = StringWriter()
            val wrt = PrintWriter(out)
            t.printStackTrace(wrt)
            val cause = t.cause
            val causes = if (cause != null && cause !== t) mutableListOf<DefaultFailure?>(fromThrowable(cause)) else mutableListOf<DefaultFailure?>()
            return DefaultFailure(t.message, out.toString(), causes)
        }
    }
}

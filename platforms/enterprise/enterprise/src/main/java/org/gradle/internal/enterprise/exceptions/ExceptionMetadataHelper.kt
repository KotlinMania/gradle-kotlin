/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.enterprise.exceptions

import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.api.tasks.VerificationException
import org.gradle.groovy.scripts.ScriptCompilationException
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.exceptions.MultiCauseException

object ExceptionMetadataHelper {
    private const val METADATA_KEY_TASK_PATH = "taskPath"
    private const val METADATA_KEY_SCRIPT_LINE_NUMBER = "scriptLineNumber"
    private const val METADATA_KEY_SCRIPT_FILE = "scriptFile"
    private const val METADATA_KEY_SOURCE_DISPLAY_NAME = "sourceDisplayName"
    private const val METADATA_KEY_LOCATION = "location"
    private const val METADATA_KEY_LINE_NUMBER = "lineNumber"
    private const val METADATA_KEY_IS_MULTICAUSE = "isMultiCause"
    private const val METADATA_KEY_IS_VERIFICATION_EXCEPTION = "isVerificationException"

    fun getMetadata(t: Throwable): MutableMap<String, String> {
        val metadata: MutableMap<String, String> = LinkedHashMap<String, String>()

        if (t is TaskExecutionException) {
            val taskPath = (t.getTask() as TaskInternal).getIdentityPath().asString()
            metadata.put(METADATA_KEY_TASK_PATH, taskPath!!)
        }

        if (t is ScriptCompilationException) {
            metadata.put(METADATA_KEY_SCRIPT_FILE, t.getScriptSource().getFileName())
            val sceLineNumber = t.getLineNumber()
            if (sceLineNumber != null) {
                metadata.put(METADATA_KEY_SCRIPT_LINE_NUMBER, sceLineNumber.toString())
            }
        }

        if (t is LocationAwareException) {
            metadata.put(METADATA_KEY_SOURCE_DISPLAY_NAME, t.getSourceDisplayName()!!)
            val laeLineNumber = t.getLineNumber()
            if (laeLineNumber != null) {
                metadata.put(METADATA_KEY_LINE_NUMBER, laeLineNumber.toString())
            }
            metadata.put(METADATA_KEY_LOCATION, t.getLocation()!!)
        }

        if (t is MultiCauseException) {
            metadata.put(METADATA_KEY_IS_MULTICAUSE, true.toString())
        }

        if (t is VerificationException) {
            metadata.put(METADATA_KEY_IS_VERIFICATION_EXCEPTION, true.toString())
        }

        return metadata
    }

    fun extractCauses(t: Throwable): MutableList<out Throwable> {
        if (t is MultiCauseException) {
            val mceCauses: MutableList<out Throwable>? = t.causes
            if (mceCauses != null && !mceCauses.isEmpty()) {
                return mceCauses
            } else {
                return mutableListOf<Throwable>()
            }
        } else {
            val cause = t.cause
            if (cause != null) {
                return mutableListOf<Throwable>(cause)
            } else {
                return mutableListOf<Throwable>()
            }
        }
    }
}

/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report

import java.util.TreeSet

internal class ReportState : VerificationHighLevelErrors {
    private val affectedFiles: MutableSet<String> = TreeSet<String>()
    private var maybeCompromised = false
    private var hasMissing = false
    private var failedSignatures = false
    private var hasUntrustedKeys = false
    private var keyServersDisabled = false

    fun maybeCompromised() {
        maybeCompromised = true
    }

    fun hasMissing() {
        hasMissing = true
    }

    fun failedSignatures() {
        failedSignatures = true
    }

    fun hasUntrustedKeys() {
        hasUntrustedKeys = true
    }

    fun keyServersAreDisabled() {
        keyServersDisabled = true
    }

    override fun isMaybeCompromised(): Boolean {
        return maybeCompromised
    }

    override fun hasFailedSignatures(): Boolean {
        return failedSignatures
    }

    override fun canSuggestWriteMetadata(): Boolean {
        return (hasMissing || hasUntrustedKeys) && !maybeCompromised
    }

    override fun getAffectedFiles(): MutableSet<String> {
        return affectedFiles
    }

    fun addAffectedFile(file: String) {
        affectedFiles.add(file)
    }

    override fun isKeyServersDisabled(): Boolean {
        return keyServersDisabled
    }
}

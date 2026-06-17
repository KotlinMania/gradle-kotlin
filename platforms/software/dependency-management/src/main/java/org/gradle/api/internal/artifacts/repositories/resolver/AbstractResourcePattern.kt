/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver

import com.google.common.base.Strings
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.repositories.PatternHelper
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resource.ExternalResourceName
import java.net.URI
import kotlin.math.max

internal abstract class AbstractResourcePattern private constructor(protected val base: ExternalResourceName) : ResourcePattern {
    private val revisionIsOptional: Boolean
    private val organisationIsOptional: Boolean
    private val artifactIsOptional: Boolean
    private val classifierIsOptional: Boolean
    private val extensionIsOptional: Boolean
    private val typeIsOptional: Boolean

    constructor(pattern: String) : this(ExternalResourceName(pattern))

    constructor(baseUri: URI, pattern: String) : this(ExternalResourceName(baseUri, pattern))

    init {
        this.revisionIsOptional = isOptionalToken(PatternHelper.REVISION_KEY)
        this.organisationIsOptional = isOptionalToken(PatternHelper.ORGANISATION_KEY, PatternHelper.ORGANISATION_KEY2)
        this.artifactIsOptional = isOptionalToken(PatternHelper.ARTIFACT_KEY)
        this.classifierIsOptional = isOptionalToken(CLASSIFIER_KEY)
        this.extensionIsOptional = isOptionalToken(PatternHelper.EXT_KEY)
        this.typeIsOptional = isOptionalToken(PatternHelper.TYPE_KEY)
    }

    override fun getPattern(): String {
        // Replace encoded [] with plain variants, since we are using them with a special meaning here.
        return base.getDisplayable().replace("%5B", "[").replace("%5D", "]")
    }

    protected open fun substituteTokens(pattern: String, attributes: MutableMap<String, String>): String {
        return PatternHelper.substituteTokens(pattern, attributes)
    }

    protected fun toAttributes(artifact: ModuleComponentArtifactMetadata): MutableMap<String, String> {
        val attributes = toAttributes(artifact.getId()!!.getComponentIdentifier())
        attributes.putAll(toAttributes(artifact.getName()!!))
        return attributes
    }

    protected fun toAttributes(module: ModuleIdentifier, ivyArtifactName: IvyArtifactName): MutableMap<String, String> {
        val attributes = toAttributes(module)
        attributes.putAll(toAttributes(ivyArtifactName))
        return attributes
    }

    protected fun toAttributes(ivyArtifact: IvyArtifactName): MutableMap<String, String> {
        val attributes = HashMap<String, String>()
        attributes.put(PatternHelper.ARTIFACT_KEY, ivyArtifact.name!!)
        attributes.put(PatternHelper.TYPE_KEY, ivyArtifact.type!!)
        attributes.put(PatternHelper.EXT_KEY, ivyArtifact.extension!!)
        attributes.put(CLASSIFIER_KEY, ivyArtifact.classifier!!)
        return attributes
    }

    protected fun toAttributes(module: ModuleIdentifier): MutableMap<String, String> {
        val attributes = HashMap<String, String>()
        attributes.put(PatternHelper.ORGANISATION_KEY, module.getGroup())
        attributes.put(PatternHelper.MODULE_KEY, module.getName())
        return attributes
    }

    protected fun toAttributes(componentIdentifier: ModuleComponentIdentifier): MutableMap<String, String> {
        val attributes = HashMap<String, String>()
        attributes.put(PatternHelper.ORGANISATION_KEY, componentIdentifier.getGroup())
        attributes.put(PatternHelper.MODULE_KEY, componentIdentifier.getModule())
        attributes.put(PatternHelper.REVISION_KEY, componentIdentifier.getVersion())
        return attributes
    }

    override fun isComplete(moduleIdentifier: ModuleIdentifier): Boolean {
        return isValidSubstitute(moduleIdentifier.getName(), false)
                && isValidSubstitute(moduleIdentifier.getGroup(), organisationIsOptional)
    }

    override fun isComplete(componentIdentifier: ModuleComponentIdentifier): Boolean {
        return isValidSubstitute(componentIdentifier.getModule(), false)
                && isValidSubstitute(componentIdentifier.getGroup(), organisationIsOptional)
                && isValidSubstitute(componentIdentifier.getVersion(), revisionIsOptional)
    }

    override fun isComplete(artifactIdentifier: ModuleComponentArtifactMetadata): Boolean {
        val artifactName = artifactIdentifier.getName()
        val componentIdentifier = artifactIdentifier.getId()!!.getComponentIdentifier()
        return isValidSubstitute(componentIdentifier.getModule(), false)
                && isValidSubstitute(componentIdentifier.getGroup(), organisationIsOptional)
                && isValidSubstitute(componentIdentifier.getVersion(), revisionIsOptional)
                && isValidSubstitute(artifactName!!.name, artifactIsOptional)
                && isValidSubstitute(artifactName.classifier, classifierIsOptional)
                && isValidSubstitute(artifactName.extension, extensionIsOptional)
                && isValidSubstitute(artifactName.type, typeIsOptional)
    }

    private fun isValidSubstitute(candidate: String?, optional: Boolean): Boolean {
        if (Strings.isNullOrEmpty(candidate)) {
            return optional
        }
        return !candidate!!.startsWith("\${")
    }

    private fun isOptionalToken(vararg tokenVariants: String): Boolean {
        val patternString = base.getPath()
        var tokenIndex = -1
        for (token in tokenVariants) {
            tokenIndex = patternString.indexOf("[" + token + "]")
            if (tokenIndex != -1) {
                break
            }
        }
        if (tokenIndex == -1) {
            return true
        }

        var optionalOpen = 0
        for (i in 0..<tokenIndex) {
            val nextChar = patternString.get(i)
            if (nextChar == '(') {
                optionalOpen++
            } else if (nextChar == ')') {
                optionalOpen = max(0, optionalOpen - 1)
            }
        }
        return optionalOpen > 0
    }

    companion object {
        const val CLASSIFIER_KEY: String = "classifier"
    }
}

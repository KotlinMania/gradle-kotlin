/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple

import com.google.common.base.Objects
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.IvyPatternMatcherExcludeRuleSpec
import org.gradle.internal.component.model.IvyArtifactName

internal class DefaultIvyPatternMatcherExcludeRuleSpec private constructor(private val moduleId: ModuleIdentifier, private val ivyArtifactName: IvyArtifactName?, matcher: String?) :
    IvyPatternMatcherExcludeRuleSpec {
    private val matcher: PatternMatcher
    private val isArtifactExclude: Boolean
    private val hashCode: Int

    init {
        this.matcher = PatternMatchers.Companion.getInstance().getMatcher(matcher)
        this.isArtifactExclude = ivyArtifactName != null
        this.hashCode = Objects.hashCode(moduleId, ivyArtifactName, matcher, isArtifactExclude)
    }

    override fun toString(): String {
        return "{ \"exclude-rule\" : { \"moduleId\": \"" + moduleId + "\", \"artifact\" : \"" + (if (ivyArtifactName != null) ivyArtifactName.displayName else "") + "\", \"matcher\": \"" + matcher.getName() + "\"} }"
    }

    override fun excludes(module: ModuleIdentifier): Boolean {
        if (isArtifactExclude) {
            return false
        }
        return matches(moduleId.getGroup(), module.getGroup()) && matches(moduleId.getName(), module.getName())
    }

    override fun excludesArtifact(module: ModuleIdentifier, artifact: IvyArtifactName): Boolean {
        if (!isArtifactExclude) {
            return false
        }
        return matches(moduleId.getGroup(), module.getGroup())
                && matches(moduleId.getName(), module.getName())
                && matches(ivyArtifactName!!.name, artifact.name)
                && matches(ivyArtifactName.extension, artifact.extension)
                && matches(ivyArtifactName.type, artifact.type)
    }

    override fun mayExcludeArtifacts(): Boolean {
        return isArtifactExclude
    }

    private fun matches(expression: String?, input: String?): Boolean {
        if (expression == null && input == null) {
            return true
        }
        if (expression == null || input == null) {
            return false
        }
        return matcher.getMatcher(expression).matches(input)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultIvyPatternMatcherExcludeRuleSpec
        return hashCode == that.hashCode && isArtifactExclude == that.isArtifactExclude &&
                Objects.equal(moduleId, that.moduleId) &&
                Objects.equal(ivyArtifactName, that.ivyArtifactName) &&
                Objects.equal(matcher, that.matcher)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun getArtifact(): IvyArtifactName? {
        return ivyArtifactName
    }

    companion object {
        fun of(moduleId: ModuleIdentifier, artifact: IvyArtifactName?, matcher: String?): ExcludeSpec {
            return DefaultIvyPatternMatcherExcludeRuleSpec(moduleId, artifact, matcher)
        }
    }
}

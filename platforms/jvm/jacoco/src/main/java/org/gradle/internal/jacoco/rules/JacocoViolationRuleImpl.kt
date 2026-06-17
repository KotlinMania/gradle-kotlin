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
package org.gradle.internal.jacoco.rules

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule
import java.util.Collections

class JacocoViolationRuleImpl : JacocoViolationRule {
    private var enabled = true
    private var scope: String? = "BUNDLE"
    private var includes: MutableList<String?>? = ImmutableList.of<String?>("*")
    private var excludes: MutableList<String?>? = ImmutableList.of<String?>()
    private val limits: MutableList<JacocoLimit?>? = ArrayList<JacocoLimit?>()

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun setElement(element: String?) {
        this.scope = element
    }

    override fun getElement(): String? {
        return scope
    }

    override fun setIncludes(includes: MutableList<String?>?) {
        this.includes = includes
    }

    override fun getIncludes(): MutableList<String?> {
        return Collections.unmodifiableList<String?>(includes)
    }

    override fun setExcludes(excludes: MutableList<String?>?) {
        this.excludes = excludes
    }

    override fun getExcludes(): MutableList<String?> {
        return Collections.unmodifiableList<String?>(excludes)
    }

    override fun getLimits(): MutableList<JacocoLimit?> {
        return Collections.unmodifiableList<JacocoLimit?>(limits)
    }

    override fun limit(configureAction: Action<in JacocoLimit?>): JacocoLimit {
        val limit: JacocoLimit = JacocoLimitImpl()
        configureAction.execute(limit)
        limits!!.add(limit)
        return limit
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as JacocoViolationRuleImpl

        if (enabled != that.enabled) {
            return false
        }
        if (scope !== that.scope) {
            return false
        }
        if (if (includes != null) (includes != that.includes) else that.includes != null) {
            return false
        }
        if (if (excludes != null) (excludes != that.excludes) else that.excludes != null) {
            return false
        }
        return if (limits != null) (limits == that.limits) else that.limits == null
    }

    override fun hashCode(): Int {
        var result = if (enabled) 1 else 0
        result = 31 * result + (if (scope != null) scope.hashCode() else 0)
        result = 31 * result + (if (includes != null) includes.hashCode() else 0)
        result = 31 * result + (if (excludes != null) excludes.hashCode() else 0)
        result = 31 * result + (if (limits != null) limits.hashCode() else 0)
        return result
    }
}

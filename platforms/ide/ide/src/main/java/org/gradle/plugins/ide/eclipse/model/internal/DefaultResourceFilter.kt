/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model.internal

import com.google.common.base.Objects
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.plugins.ide.eclipse.model.ResourceFilter
import org.gradle.plugins.ide.eclipse.model.ResourceFilterAppliesTo
import org.gradle.plugins.ide.eclipse.model.ResourceFilterMatcher
import org.gradle.plugins.ide.eclipse.model.ResourceFilterType
import org.gradle.util.internal.ClosureBackedAction
import java.lang.Boolean
import kotlin.Any
import kotlin.Int
import kotlin.String
import kotlin.hashCode

class DefaultResourceFilter() : ResourceFilter {
    private var appliesTo: ResourceFilterAppliesTo? = ResourceFilterAppliesTo.FILES_AND_FOLDERS
    private var type: ResourceFilterType? = ResourceFilterType.EXCLUDE_ALL
    private var recursive = true
    private var matcher: ResourceFilterMatcher? = null

    constructor(appliesTo: ResourceFilterAppliesTo, type: ResourceFilterType, recursive: Boolean, matcher: ResourceFilterMatcher?) : this() {
        setAppliesTo(appliesTo)
        setType(type)
        setRecursive(recursive)
        setMatcher(matcher)
    }

    override fun getAppliesTo(): ResourceFilterAppliesTo? {
        return appliesTo
    }

    override fun setAppliesTo(appliesTo: ResourceFilterAppliesTo) {
        if (appliesTo == null) {
            throw InvalidUserDataException("appliesTo must not be null")
        }
        this.appliesTo = appliesTo
    }

    override fun getType(): ResourceFilterType? {
        return type
    }

    override fun setType(type: ResourceFilterType) {
        if (type == null) {
            throw InvalidUserDataException("type must not be null")
        }
        this.type = type
    }

    override fun isRecursive(): Boolean {
        return recursive
    }

    override fun setRecursive(recursive: Boolean) {
        this.recursive = recursive
    }

    override fun getMatcher(): ResourceFilterMatcher? {
        return matcher
    }

    fun setMatcher(matcher: ResourceFilterMatcher?) {
        this.matcher = matcher
    }

    fun matcher(@DelegatesTo(value = ResourceFilterMatcher::class, strategy = Closure.DELEGATE_FIRST) configureClosure: Closure<*>?): ResourceFilterMatcher? {
        return matcher(ClosureBackedAction<ResourceFilterMatcher?>(configureClosure))
    }

    override fun matcher(configureAction: Action<in ResourceFilterMatcher?>): ResourceFilterMatcher? {
        if (this.matcher == null) {
            this.matcher = DefaultResourceFilterMatcher()
        }
        configureAction.execute(this.matcher)
        return this.matcher
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null) {
            return false
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val resourceFilter = o as DefaultResourceFilter
        return Objects.equal(appliesTo, resourceFilter.appliesTo)
                && Objects.equal(type, resourceFilter.type)
                && recursive == resourceFilter.recursive && Objects.equal(matcher, resourceFilter.matcher)
    }

    override fun hashCode(): Int {
        var result: Int
        result = if (appliesTo != null) appliesTo.hashCode() else 0
        result = 31 * result + (if (type != null) type.hashCode() else 0)
        result = 31 * result + Boolean.hashCode(recursive)
        result = 31 * result + (if (matcher != null) matcher.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return ("ResourceFilter{"
                + "appliesTo='" + appliesTo + '\''
                + ", type='" + type + '\''
                + ", recursive='" + recursive + '\''
                + ", matcher='" + matcher + '\''
                + '}')
    }
}

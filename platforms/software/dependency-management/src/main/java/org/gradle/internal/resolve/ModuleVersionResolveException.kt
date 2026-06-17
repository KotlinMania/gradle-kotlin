/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resolve

import org.gradle.api.Describable
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.internal.Factory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseExceptionNoStackTrace
import java.util.Formatter

@Contextual
open class ModuleVersionResolveException : DefaultMultiCauseExceptionNoStackTrace {
    private val paths: MutableList<MutableList<Describable?>> = ArrayList<MutableList<Describable?>>()

    /**
     * Returns the selector that could not be resolved.
     */
    @JvmField
    val selector: ComponentSelector?

    constructor(selector: ComponentSelector?, message: Factory<String?>?, cause: Throwable?) : super(message, cause) {
        this.selector = selector
    }

    constructor(selector: ComponentSelector?, message: Factory<String?>?) : super(message) {
        this.selector = selector
    }

    constructor(selector: ComponentSelector, cause: Throwable?) : this(selector, format("Could not resolve %s.", selector)) {
        initCause(cause)
    }

    constructor(selector: ComponentSelector, causes: Iterable<out Throwable?>) : this(selector, format("Could not resolve %s.", selector)) {
        initCauses(causes)
    }

    constructor(id: ModuleVersionIdentifier, message: Factory<String?>?) : this(
        DefaultModuleComponentSelector.newSelector(id.getModule(), DefaultImmutableVersionConstraint.of(id.getVersion())),
        message
    )

    constructor(id: ModuleComponentIdentifier, messageFormat: Factory<String?>?) : this(
        DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId(id.getGroup(), id.getModule()),
            DefaultImmutableVersionConstraint.of(id.getVersion())
        ), messageFormat
    )

    constructor(id: ModuleComponentIdentifier, messageFormat: Factory<String?>?, cause: Throwable?) : this(
        DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId(
                id.getGroup(),
                id.getModule()
            ), DefaultImmutableVersionConstraint.of(id.getVersion())
        ), messageFormat, cause
    )

    constructor(id: ModuleComponentIdentifier, cause: Throwable?) : this(
        DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId(id.getGroup(), id.getModule()),
            DefaultImmutableVersionConstraint.of(id.getVersion())
        ), mutableListOf<Throwable?>(cause)
    )

    constructor(id: ModuleComponentIdentifier, causes: Iterable<out Throwable?>) : this(
        DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId(id.getGroup(), id.getModule()),
            DefaultImmutableVersionConstraint.of(id.getVersion())
        ), causes
    )

    /**
     * Creates a copy of this exception, with the given incoming paths.
     */
    fun withIncomingPaths(paths: MutableCollection<out MutableList<Describable?>?>): ModuleVersionResolveException {
        val copy = createCopy()
        copy.paths.addAll(paths)
        copy.initCauses(getCauses())
        copy.setStackTrace(getStackTrace())
        return copy
    }

    public override fun getMessage(): String? {
        if (paths.isEmpty()) {
            return super.getMessage()
        }
        val formatter = Formatter()
        formatter.format("%s%nRequired by:", super.getMessage())
        for (path in paths) {
            formatter.format("%n    %s", toString(path.get(0)!!))
            for (i in 1..<path.size) {
                formatter.format(" > %s", toString(path.get(i)!!))
            }
        }
        return formatter.toString()
    }

    private fun toString(identifier: Describable): String? {
        return identifier.getDisplayName()
    }

    protected open fun createCopy(): ModuleVersionResolveException {
        try {
            val message = getMessage()
            return javaClass.getConstructor(ComponentSelector::class.java, Factory::class.java).newInstance(this.selector, org.gradle.internal.Factory { message } as Factory<String?>)
        } catch (e: Exception) {
            throw throwAsUncheckedException(e)
        }
    }

    companion object {
        protected fun format(messageFormat: String, selector: ComponentSelector): Factory<String?> {
            return org.gradle.internal.Factory { String.format(messageFormat, selector.getDisplayName()) }
        }
    }
}

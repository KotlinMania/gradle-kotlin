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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Preconditions
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.Action
import org.gradle.plugins.ide.internal.IdeDeprecations.nagDeprecatedType
import org.gradle.util.internal.ConfigureUtil
import java.io.File

/**
 * DSL-friendly model of the IDEA project information.
 * First point of entry when it comes to customizing the IDEA generation.
 *
 *
 * See the examples in docs for [IdeaModule] or [IdeaProject].
 */
abstract class IdeaModel {
    private var module: IdeaModule? = null

    /**
     * Configures IDEA project information.
     *
     * For examples see docs for [IdeaProject].
     */
    var project: IdeaProject? = null

    @get:Deprecated("Will be removed in Gradle 10.")
    @set:Deprecated("Will be removed in Gradle 10.")
    @Suppress("deprecation")
    var workspace: IdeaWorkspace? = null
        /**
         * Configures IDEA workspace information.
         *
         *
         * For examples see docs for [IdeaWorkspace].
         *
         */
        get() {
            nagDeprecatedType(IdeaWorkspace::class.java)
            return field
        }
        /**
         * Set workspace.
         *
         */
        set(workspace) {
            nagDeprecatedType(IdeaWorkspace::class.java)
            field = workspace
        }

    /**
     * Configures the target IDEA version.
     */
    var targetVersion: String? = null

    /**
     * Configures IDEA module information.
     *
     * For examples see docs for [IdeaModule].
     */
    fun getModule(): IdeaModule {
        return module!!
    }

    fun setModule(module: IdeaModule) {
        this.module = module
    }

    /**
     * Configures IDEA module information.
     *
     * For examples see docs for [IdeaModule].
     */
    fun module(@DelegatesTo(IdeaModule::class) @ClosureParams(value = SimpleType::class, options = ["org.gradle.plugins.ide.idea.model.IdeaModule"]) closure: Closure<*>?) {
        ConfigureUtil.configure<IdeaModule?>(closure, getModule())
    }

    /**
     * Configures IDEA module information.
     *
     * For examples see docs for [IdeaModule].
     * @since 3.5
     */
    fun module(action: Action<in IdeaModule?>) {
        action.execute(getModule())
    }

    /**
     * Configures IDEA project information.
     *
     * For examples see docs for [IdeaProject].
     */
    fun project(@DelegatesTo(IdeaProject::class) closure: Closure<*>?) {
        ConfigureUtil.configure<IdeaProject?>(closure, this.project)
    }

    /**
     * Configures IDEA project information.
     *
     * For examples see docs for [IdeaProject].
     * @since 3.5
     */
    fun project(action: Action<in IdeaProject?>) {
        action.execute(this.project)
    }

    /**
     * Configures IDEA workspace information.
     *
     * For examples see docs for [IdeaWorkspace].
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun workspace(@DelegatesTo(IdeaWorkspace::class) closure: Closure<*>?) {
        ConfigureUtil.configure<IdeaWorkspace?>(closure, this.workspace)
    }

    /**
     * Configures IDEA workspace information.
     *
     * For examples see docs for [IdeaWorkspace].
     * @since 3.5
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun workspace(action: Action<in IdeaWorkspace?>) {
        action.execute(this.workspace)
    }

    /**
     * Adds path variables to be used for replacing absolute paths in resulting files (*.iml, etc.).
     *
     * For example see docs for [IdeaModule].
     *
     * @param pathVariables A map with String-&gt;File pairs.
     */
    fun pathVariables(pathVariables: MutableMap<String?, File?>?) {
        Preconditions.checkNotNull<MutableMap<String?, File?>?>(pathVariables)
        module!!.getPathVariables().putAll(pathVariables!!)
    }
}

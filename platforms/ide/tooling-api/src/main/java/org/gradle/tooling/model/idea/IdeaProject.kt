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
package org.gradle.tooling.model.idea

import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.HierarchicalElement
import org.gradle.tooling.model.UnsupportedMethodException

/**
 * Represents the information about the IDEA project.
 *
 * @since 1.0-milestone-5
 */
interface IdeaProject : HierarchicalElement {
    @get:Throws(UnsupportedMethodException::class)
    val javaLanguageSettings: IdeaJavaLanguageSettings?

    /**
     * Returns the name of the JDK.
     *
     * @return The name of the JDK.
     * @since 1.0-milestone-5
     */
    val jdkName: String?

    /**
     * Returns the language level to use within the current project.
     *
     *
     * Note: To determine the project language level [IdeaModule.getJavaLanguageSettings]
     * should be preferred.
     *
     * @return The language level to use within the current project.
     * @since 1.0-milestone-5
     */
    val languageLevel: IdeaLanguageLevel?

    /**
     * Returns the modules of this IDEA project. Most projects have at least one module.
     * Alias for [.getModules].
     *
     * @return The modules of this IDEA project.
     * @since 1.0-milestone-5
     */
    override val children: DomainObjectSet<out IdeaModule?>?

    /**
     * Returns the modules of this IDEA project. Most projects have at least one module.
     * Alias for [.getChildren].
     *
     * @return The modules of this IDEA project.
     * @since 1.0-milestone-5
     */
    val modules: DomainObjectSet<out IdeaModule?>?
}

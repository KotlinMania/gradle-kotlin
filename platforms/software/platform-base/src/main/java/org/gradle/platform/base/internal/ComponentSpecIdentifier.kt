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
package org.gradle.platform.base.internal

import org.gradle.util.Path

/**
 * An identifier for a [org.gradle.platform.base.ComponentSpec], which has a name.
 */
interface ComponentSpecIdentifier {
    /**
     * The parent of the component, if any.
     */
    val parent: ComponentSpecIdentifier?

    /**
     * The base name of this component.
     */
    val name: String?

    /**
     * A path that uniquely identifies this component within its project.
     *
     * Implementation should attempt to produce human consumable identifiers.
     */
    val path: Path?

    /**
     * Returns a child of this component, with the given name.
     */
    fun child(name: String?): ComponentSpecIdentifier?

    /**
     * Returns a name that can be used to identify this component uniquely within its project. The name belongs to a flat namespace and does not include any
     * hierarchy delimiters. As such, it can be safely used for task or file names.
     *
     * Implementation should attempt to produce a somewhat human consumable name (eg not a uuid).
     */
    val projectScopedName: String?

    /**
     * The path of the project that contains this component.
     */
    val projectPath: String?
}

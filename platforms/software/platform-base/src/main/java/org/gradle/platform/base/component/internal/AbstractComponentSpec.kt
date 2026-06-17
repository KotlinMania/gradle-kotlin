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
package org.gradle.platform.base.component.internal

import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.internal.ComponentSpecIdentifier
import org.gradle.platform.base.internal.ComponentSpecInternal

open class AbstractComponentSpec(private val identifier: ComponentSpecIdentifier, private val publicType: Class<*>) : ComponentSpec, ComponentSpecInternal {
    public override fun getIdentifier(): ComponentSpecIdentifier? {
        return identifier
    }

    override fun getName(): String? {
        return identifier.name
    }

    val projectPath: String?
        get() = identifier.projectPath

    protected open val typeName: String?
        get() = publicType.getSimpleName()

    override fun getDisplayName(): String? {
        return this.typeName + " '" + identifier.path + "'"
    }

    override fun toString(): String {
        return getDisplayName()!!
    }
}

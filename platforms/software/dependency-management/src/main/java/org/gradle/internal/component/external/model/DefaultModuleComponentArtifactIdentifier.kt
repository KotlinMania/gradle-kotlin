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
package org.gradle.internal.component.external.model

import org.apache.commons.lang3.StringUtils
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName

class DefaultModuleComponentArtifactIdentifier(private val componentIdentifier: ModuleComponentIdentifier, @JvmField val name: IvyArtifactName) : ModuleComponentArtifactIdentifier {
    private val hashCode: Int

    constructor(componentIdentifier: ModuleComponentIdentifier, name: String, type: String, extension: String?) : this(componentIdentifier, DefaultIvyArtifactName(name, type, extension))

    constructor(componentIdentifier: ModuleComponentIdentifier, name: String, type: String, extension: String?, classifier: String?) : this(
        componentIdentifier,
        DefaultIvyArtifactName(name, type, extension, classifier)
    )

    init {
        this.hashCode = 31 * name.hashCode() + componentIdentifier.hashCode()
    }

    override fun getFileName(): String {
        val classifier = if (StringUtils.isNotEmpty(name.classifier)) "-" + name.classifier else ""
        val extension = if (StringUtils.isNotEmpty(name.extension)) "." + name.extension else ""
        return name.name + "-" + componentIdentifier.getVersion() + classifier + extension
    }

    override fun getDisplayName(): String {
        return getFileName() + " (" + getComponentIdentifier().getDisplayName() + ")"
    }

    override fun getCapitalizedDisplayName(): String {
        return getDisplayName()
    }

    override fun getComponentIdentifier(): ModuleComponentIdentifier {
        return componentIdentifier
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as DefaultModuleComponentArtifactIdentifier
        return other.componentIdentifier == componentIdentifier
                && other.name == name
    }
}

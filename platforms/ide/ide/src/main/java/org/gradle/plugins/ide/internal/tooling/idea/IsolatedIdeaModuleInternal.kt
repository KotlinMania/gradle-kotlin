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
package org.gradle.plugins.ide.internal.tooling.idea

import com.google.common.collect.ImmutableList
import org.gradle.api.JavaVersion
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jspecify.annotations.NullMarked
import java.io.Serializable

/**
 * Represents an IDEA module in isolation.
 *
 *
 * **This model is internal, and is NOT part of the public Tooling API.**
 */
@NullMarked
class IsolatedIdeaModuleInternal : Serializable {
    var name: String? = null
    var javaSourceCompatibility: JavaVersion? = null
    var javaTargetCompatibility: JavaVersion? = null
    var explicitSourceLanguageLevel: IdeaLanguageLevel? = null
    var explicitTargetBytecodeVersion: JavaVersion? = null
    private var contentRoot: DefaultIdeaContentRoot? = null
    var jdkName: String? = null
    var compilerOutput: DefaultIdeaCompilerOutput? = null
    var dependencies: MutableList<DefaultIdeaDependency> = ImmutableList.of<DefaultIdeaDependency>()
        set(dependencies) {
            field = ImmutableList.copyOf<DefaultIdeaDependency>(dependencies) // also ensures it's serializable
        }

    override fun toString(): String {
        return ("IsolatedIdeaModuleInternal{"
                + "contentRoot='" + contentRoot!!.rootDirectory + '\''
                + '}')
    }

    fun getContentRoot(): DefaultIdeaContentRoot {
        return contentRoot!!
    }

    fun setContentRoot(contentRoot: DefaultIdeaContentRoot) {
        this.contentRoot = contentRoot
    }
}

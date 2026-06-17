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
package org.gradle.language.nativeplatform.internal

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternSet
import java.util.concurrent.Callable
import javax.inject.Inject

abstract class DefaultNativeComponent {
    val source: ConfigurableFileCollection

    init {
        // TODO - introduce a new 'var' data structure that allows these conventions to be configured explicitly
        source = this.objectFactory.fileCollection()
    }

    @JvmField
    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    abstract val displayName: DisplayName?

    override fun toString(): String {
        return this.displayName.getDisplayName()
    }

    fun source(action: Action<in ConfigurableFileCollection?>) {
        action.execute(source)
    }

    // TODO - this belongs with the 'var' data structure
    protected fun createSourceView(defaultLocation: String, sourceExtensions: MutableList<String?>): FileCollection {
        val patternSet = PatternSet()
        for (sourceExtension in sourceExtensions) {
            patternSet.include("**/*." + sourceExtension)
        }
        return this.projectLayout.files(object : Callable<Any?> {
            override fun call(): Any {
                val tree: FileTree?
                if (source.getFrom().isEmpty()) {
                    tree = this.projectLayout.getProjectDirectory().dir(defaultLocation).getAsFileTree()
                } else {
                    tree = source.getAsFileTree()
                }
                return tree!!.matching(patternSet)
            }
        })
    }
}

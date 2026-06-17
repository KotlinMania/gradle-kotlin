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
package org.gradle.language.base.internal

import org.gradle.api.BuildableComponentSpec
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.AbstractBuildableComponentSpec
import org.gradle.platform.base.internal.ComponentSpecIdentifier

abstract class AbstractLanguageSourceSet(identifier: ComponentSpecIdentifier?, publicType: Class<out BuildableComponentSpec?>?, private val source: SourceDirectorySet) :
    AbstractBuildableComponentSpec(identifier!!, publicType!!), LanguageSourceSetInternal {
    private var generated = false
    private var generatorTask: Task? = null

    init {
        super.builtBy(source.getBuildDependencies())
    }

    protected open val languageName: String
        get() = guessLanguageName(getTypeName())

    override fun getProjectScopedName(): String? {
        return getIdentifier().projectScopedName
    }

    override fun builtBy(vararg tasks: Any?) {
        generated = true
        super.builtBy(*tasks)
    }

    override fun generatedBy(generatorTask: Task?) {
        this.generatorTask = generatorTask
    }

    override fun getGeneratorTask(): Task? {
        return generatorTask
    }

    override fun getMayHaveSources(): Boolean {
        // This doesn't take into account build dependencies of the SourceDirectorySet.
        // Should just ditch SourceDirectorySet from here since it's not really a great model, and drags in too much baggage.
        return generated || !source.isEmpty()
    }

    override fun getDisplayName(): String {
        val languageName = this.languageName
        if (languageName.lowercase().endsWith("resources")) {
            return languageName + " '" + getIdentifier().path + "'"
        }
        return languageName + " source '" + getIdentifier().path + "'"
    }

    override fun getSource(): SourceDirectorySet {
        return source
    }

    override fun getParentName(): String? {
        return if (getIdentifier().parent == null) null else getIdentifier().parent!!.name
    }

    companion object {
        private val LANGUAGES: MutableMap<String?, String?> = HashMap<String?, String?>()

        @Synchronized
        private fun guessLanguageName(typeName: String): String {
            var language: String? = LANGUAGES.get(typeName)
            if (language != null) {
                return language
            }
            language = typeName.replace("LanguageSourceSet$".toRegex(), "").replace("SourceSet$".toRegex(), "").replace("Source$".toRegex(), "").replace("Set$".toRegex(), "")
            LANGUAGES.put(typeName, language)
            return language
        }
    }
}

/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.TestSpec
import org.gradle.tooling.internal.protocol.test.InternalTestSpec

class DefaultTestSpec(
    taskPath: String?,
    private val classes: MutableList<String?>,
    private val methods: MutableMap<String?, MutableList<String?>?>,
    private val packages: MutableList<String?>,
    private val patterns: MutableList<String?>
) : DefaultTaskSpec(taskPath), TestSpec, InternalTestSpec {
    internal constructor(taskPath: String?) : this(taskPath, ArrayList<String?>(), LinkedHashMap<String?, MutableList<String?>?>(), ArrayList<String?>(), ArrayList<String?>())

    override fun includePackage(pkg: String?): TestSpec {
        return includePackages(mutableListOf<String?>(pkg))
    }

    override fun includePackages(packages: MutableCollection<String?>): TestSpec {
        this.packages.addAll(packages)
        return this
    }

    override fun includeClass(cls: String?): TestSpec {
        return includeClasses(mutableListOf<String?>(cls))
    }

    override fun includeClasses(classes: MutableCollection<String?>): TestSpec {
        this.classes.addAll(classes)
        return this
    }

    override fun includeMethod(cls: String?, method: String?): TestSpec {
        return includeMethods(cls, mutableListOf<String?>(method))
    }

    override fun includeMethods(clazz: String?, newMethods: MutableCollection<String?>): TestSpec {
        var methods = this.methods.get(clazz)
        if (methods == null) {
            methods = ArrayList<String?>(newMethods.size)
            this.methods.put(clazz, methods)
        }
        methods.addAll(newMethods)
        return this
    }

    override fun includePattern(pattern: String?): TestSpec {
        return includePatterns(mutableListOf<String?>(pattern))
    }

    override fun includePatterns(patterns: MutableCollection<String?>): TestSpec {
        this.patterns.addAll(patterns)
        return this
    }

    override fun getPackages(): MutableList<String?> {
        return packages
    }

    override fun getClasses(): MutableList<String?> {
        return classes
    }

    override fun getMethods(): MutableMap<String?, MutableList<String?>?> {
        return methods
    }

    override fun getPatterns(): MutableList<String?> {
        return patterns
    }
}

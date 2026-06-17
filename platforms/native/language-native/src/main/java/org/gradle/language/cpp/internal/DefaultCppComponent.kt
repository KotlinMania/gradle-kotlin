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
package org.gradle.language.cpp.internal

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppComponent
import org.gradle.language.internal.DefaultBinaryCollection
import org.gradle.language.nativeplatform.internal.ComponentWithNames
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent
import org.gradle.language.nativeplatform.internal.Names
import java.util.concurrent.Callable
import javax.inject.Inject

abstract class DefaultCppComponent @Inject constructor(name: String) : DefaultNativeComponent(), CppComponent, ComponentWithNames {
    private val cppSource: FileCollection?
    private val name: String?
    open val allHeaderDirs: FileCollection
    private val names: Names
    private val binaries: DefaultBinaryCollection<CppBinary?>

    init {
        this.name = name
        cppSource = createSourceView("src/" + name + "/cpp", mutableListOf<String?>("cpp", "c++", "cc"))
        this.allHeaderDirs = createDirView(getPrivateHeaders(), "src/" + name + "/headers")
        names = Names.Companion.of(name)
        binaries = org.gradle.internal.Cast.uncheckedCast<DefaultBinaryCollection<CppBinary?>?>(
            getObjectFactory().newInstance<org.gradle.language.internal.DefaultBinaryCollection<*>?>(
                org.gradle.language.internal.DefaultBinaryCollection::class.java,
                org.gradle.language.cpp.CppBinary::class.java
            )
        )!!
    }

    override fun getNames(): Names {
        return names
    }

    override fun getName(): String? {
        return name
    }

    protected fun createDirView(dirs: ConfigurableFileCollection, conventionLocation: String): FileCollection {
        return getProjectLayout().files(object : Callable<Any?> {
            override fun call(): Any {
                if (dirs.getFrom().isEmpty()) {
                    return getProjectLayout().getProjectDirectory().dir(conventionLocation)
                }
                return dirs
            }
        })
    }

    override fun getCppSource(): FileCollection? {
        return cppSource
    }

    override fun privateHeaders(action: Action<in ConfigurableFileCollection?>) {
        action.execute(getPrivateHeaders())
    }

    override fun getPrivateHeaderDirs(): FileCollection {
        return this.allHeaderDirs
    }

    override fun getHeaderFiles(): FileTree {
        val patterns = PatternSet()
        // if you would like to add more endings to this pattern, make sure to also edit DefaultCppLibrary.java and default.vcxproj.filters
        patterns.include("**/*.h")
        patterns.include("**/*.hpp")
        patterns.include("**/*.h++")
        patterns.include("**/*.hxx")
        patterns.include("**/*.hm")
        patterns.include("**/*.inl")
        patterns.include("**/*.inc")
        patterns.include("**/*.xsd")
        return this.allHeaderDirs.getAsFileTree().matching(patterns)
    }

    override fun getBinaries(): DefaultBinaryCollection<CppBinary?> {
        return binaries
    }
}

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
package org.gradle.api.internal.notations

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarType
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.TypeConversionException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class DependencyClassPathNotationConverter(
    private val instantiator: Instantiator,
    private val classPathRegistry: ClassPathRegistry,
    private val fileCollectionFactory: FileCollectionFactory,
    private val runtimeShadedJarFactory: RuntimeShadedJarFactory
) : NotationConverter<DependencyFactoryInternal.ClassPathNotation?, FileCollectionDependency?> {
    private val internCache: ConcurrentMap<DependencyFactoryInternal.ClassPathNotation?, FileCollectionDependency?> =
        ConcurrentHashMap<DependencyFactoryInternal.ClassPathNotation?, FileCollectionDependency?>()

    override fun describe(visitor: DiagnosticsVisitor) {
        visitor.candidate("ClassPathNotation").example("gradleApi()")
    }

    @Throws(TypeConversionException::class)
    override fun convert(notation: DependencyFactoryInternal.ClassPathNotation, result: NotationConvertResult<in FileCollectionDependency?>) {
        var dependency = internCache.get(notation)
        if (dependency == null) {
            dependency = create(notation)
        }
        result.converted(dependency)
    }

    private fun create(notation: DependencyFactoryInternal.ClassPathNotation): FileCollectionDependency {
        val fileCollectionInternal: FileCollectionInternal?
        if (notation == DependencyFactoryInternal.ClassPathNotation.GRADLE_API) {
            fileCollectionInternal = fileCollectionFactory.create(object : GeneratedFileCollection(notation.displayName) {
                override fun generateFileCollection(): MutableSet<File> {
                    return gradleApiFileCollection(getClassPath(notation))
                }
            })
        } else if (notation == DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT) {
            fileCollectionInternal = fileCollectionFactory.create(object : GeneratedFileCollection(notation.displayName) {
                override fun generateFileCollection(): MutableSet<File?> {
                    return gradleTestKitFileCollection(getClassPath(notation))
                }
            })
        } else {
            fileCollectionInternal = fileCollectionFactory.resolving(getClassPath(notation))
        }
        val dependency: FileCollectionDependency =
            instantiator.newInstance<DefaultFileCollectionDependency>(DefaultFileCollectionDependency::class.java, OpaqueComponentIdentifier(notation), fileCollectionInternal)
        val alreadyPresent = internCache.putIfAbsent(notation, dependency)
        return if (alreadyPresent != null) alreadyPresent else dependency
    }

    private fun getClassPath(notation: DependencyFactoryInternal.ClassPathNotation): MutableList<File> {
        return Lists.newArrayList<File?>(classPathRegistry.getClassPath(notation.name).getAsFiles())
    }

    private fun gradleApiFileCollection(apiClasspath: MutableCollection<File>): MutableSet<File> {
        // Don't inline the Groovy jar as the Groovy "tools locator" searches for it by name
        val groovyImpl = classPathRegistry.getClassPath(DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY.name).getAsFiles()
        val kotlinImpl = kotlinImplFrom(apiClasspath)
        val installationBeacon = classPathRegistry.getClassPath("GRADLE_INSTALLATION_BEACON").getAsFiles()
        apiClasspath.removeAll(groovyImpl)
        apiClasspath.removeAll(installationBeacon)
        // Remove Kotlin DSL and Kotlin jars
        removeKotlin(apiClasspath)

        val builder = ImmutableSet.builder<File?>()
        builder.add(relocatedDepsJar(apiClasspath, RuntimeShadedJarType.API))
        builder.addAll(groovyImpl)
        builder.addAll(kotlinImpl)
        builder.addAll(installationBeacon)
        return builder.build()
    }

    private fun removeKotlin(apiClasspath: MutableCollection<File>) {
        val iterator: MutableIterator<File?> = apiClasspath.iterator()
        while (iterator.hasNext()) {
            val name = iterator.next()!!.getName()
            if (name.startsWith("kotlin-") || name.startsWith("gradle-kotlin-")) {
                iterator.remove()
            }
        }
    }

    private fun kotlinImplFrom(classPath: MutableCollection<File>): MutableList<File> {
        val files = ArrayList<File>()
        for (file in classPath) {
            val name = file.getName()
            if (name.startsWith("kotlin-stdlib-") || name.startsWith("kotlin-reflect-")) {
                files.add(file)
            }
        }
        return files
    }

    private fun gradleTestKitFileCollection(testKitClasspath: MutableCollection<File>): MutableSet<File?> {
        val gradleApi = getClassPath(DependencyFactoryInternal.ClassPathNotation.GRADLE_API)
        testKitClasspath.removeAll(gradleApi)

        val builder = ImmutableSet.builder<File?>()
        builder.add(relocatedDepsJar(testKitClasspath, RuntimeShadedJarType.TEST_KIT))
        builder.addAll(gradleApiFileCollection(gradleApi))
        return builder.build()
    }

    private fun relocatedDepsJar(classpath: MutableCollection<File>?, runtimeShadedJarType: RuntimeShadedJarType): File? {
        return runtimeShadedJarFactory.get(runtimeShadedJarType, classpath)
    }

    internal abstract class GeneratedFileCollection(notation: String?) : MinimalFileSet {
        private val displayName: String
        private var generateFiles: MutableSet<File?>? = null

        init {
            this.displayName = notation + " files"
        }

        override fun getDisplayName(): String {
            return displayName
        }

        override fun getFiles(): MutableSet<File?> {
            if (generateFiles == null) {
                generateFiles = generateFileCollection()
            }
            return generateFiles!!
        }

        abstract fun generateFileCollection(): MutableSet<File?>?
    }
}

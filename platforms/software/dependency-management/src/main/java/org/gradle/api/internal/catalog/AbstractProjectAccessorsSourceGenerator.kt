/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog

import com.google.common.base.Splitter
import org.gradle.api.initialization.ProjectDescriptor
import java.io.IOException
import java.io.Writer
import java.util.function.Function
import java.util.stream.Collectors

open class AbstractProjectAccessorsSourceGenerator(writer: Writer) : AbstractSourceGenerator(writer) {
    @Throws(IOException::class)
    protected fun writeHeader(packageName: String) {
        writeLn("package " + packageName + ";")
        writeLn()
        addImport("org.jspecify.annotations.NullMarked")
        addImport("org.gradle.api.artifacts.ProjectDependency")
        addImport("org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal")
        addImport("org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory")
        addImport("org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder")
        addImport("org.gradle.api.internal.catalog.DelegatingProjectDependency")
        addImport("org.gradle.api.internal.catalog.TypeSafeProjectDependencyFactory")
        addImport("javax.inject.Inject")
        writeLn()
    }

    @Throws(IOException::class)
    protected fun writeProjectAccessor(name: String, descriptor: ProjectDescriptor) {
        writeLn("    /**")
        val path = descriptor.getPath()
        writeLn("     * Creates a project dependency on the project at path \"" + path + "\"")
        writeLn("     */")
        val returnType: String = toClassName(path, rootProjectName(descriptor))
        writeLn("    public " + returnType + " get" + name + "() { return new " + returnType + "(getFactory(), create(\"" + path + "\")); }")
        writeLn()
    }

    protected fun processChildren(current: ProjectDescriptor) {
        current.getChildren()
            .stream()
            .sorted(Comparator.comparing<ProjectDescriptor, String>(Function { obj: ProjectDescriptor -> obj.getPath() }))
            .forEachOrdered { child: ProjectDescriptor? ->
                try {
                    writeProjectAccessor(AbstractSourceGenerator.Companion.toJavaName(child!!.getName()), child)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
    }

    companion object {
        protected fun toClassName(path: String, rootProjectName: String): String {
            var name: String = toProjectName(path)
            if (name.isEmpty()) {
                name = AbstractSourceGenerator.Companion.toJavaName(rootProjectName)
            }
            return name + "ProjectDependency"
        }

        protected fun toProjectName(path: String): String {
            return Splitter.on(":")
                .omitEmptyStrings()
                .splitToList(path)
                .stream()
                .map<String> { alias: String? -> AbstractSourceGenerator.Companion.toJavaName(alias) }
                .collect(Collectors.joining("_"))
        }

        protected fun rootProjectName(descriptor: ProjectDescriptor): String {
            var current: ProjectDescriptor? = descriptor
            while (current!!.getParent() != null) {
                current = current.getParent()
            }
            return current.getName()
        }
    }
}

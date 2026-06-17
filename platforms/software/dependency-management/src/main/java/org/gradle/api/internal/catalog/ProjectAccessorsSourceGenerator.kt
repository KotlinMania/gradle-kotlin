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

import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.IOException
import java.io.Writer

class ProjectAccessorsSourceGenerator(writer: Writer) : AbstractProjectAccessorsSourceGenerator(writer) {
    @Throws(IOException::class)
    private fun generate(packageName: String, className: String, current: ProjectDescriptor) {
        writeHeader(packageName)
        writeLn("@NullMarked")
        writeLn("public class " + className + " extends DelegatingProjectDependency {")
        writeLn()
        writeLn("    @Inject")
        writeLn("    public " + className + "(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {")
        writeLn("        super(factory, delegate);")
        writeLn("    }")
        writeLn()
        processChildren(current)
        writeLn("}")
    }

    companion object {
        fun generateSource(
            writer: Writer,
            current: ProjectDescriptor,
            packageName: String
        ): String {
            val generator = ProjectAccessorsSourceGenerator(writer)
            try {
                val className: String = AbstractProjectAccessorsSourceGenerator.Companion.toClassName(current.getPath(), AbstractProjectAccessorsSourceGenerator.Companion.rootProjectName(current))
                generator.generate(packageName, className, current)
                return className
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }
    }
}

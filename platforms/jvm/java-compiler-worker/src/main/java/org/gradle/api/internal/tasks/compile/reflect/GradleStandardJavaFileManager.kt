/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.reflect

import org.gradle.api.internal.tasks.compile.filter.AnnotationProcessorFilter
import org.gradle.internal.classpath.ClassPath
import java.io.IOException
import java.net.URLClassLoader
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation

class GradleStandardJavaFileManager private constructor(fileManager: StandardJavaFileManager, private val annotationProcessorPath: ClassPath, private val hasEmptySourcePaths: Boolean) :
    ForwardingJavaFileManager<StandardJavaFileManager?>(fileManager) {
    override fun hasLocation(location: JavaFileManager.Location): Boolean {
        if (hasEmptySourcePaths) {
            // There is currently a requirement in the JDK9 javac implementation
            // that when javac is invoked with an explicitly empty sourcepath
            // (i.e. {@code --sourcepath ""}), it won't allow you to compile a java 9
            // module. However, we really want to explicitly set an empty sourcepath
            // so that we don't implicitly pull in unrequested sourcefiles which
            // haven't been snapshotted because we will consider the task up-to-date
            // if the implicit files change.
            //
            // This implementation of hasLocation() pretends that the JavaFileManager
            // has no concept of a source path.
            if (location == StandardLocation.SOURCE_PATH) {
                return false
            }
        }
        return super.hasLocation(location)
    }

    @Throws(IOException::class)
    override fun list(location: JavaFileManager.Location, packageName: String?, kinds: MutableSet<JavaFileObject.Kind?>, recurse: Boolean): Iterable<JavaFileObject?>? {
        if (hasEmptySourcePaths) {
            // If we are pretending that we don't have a sourcepath, the compiler will
            // look on the classpath for sources. Since we don't want to bring in any
            // sources implicitly from the classpath, we have to ignore source files
            // found on the classpath.
            if (location == StandardLocation.CLASS_PATH) {
                kinds.remove(JavaFileObject.Kind.SOURCE)
            }
        }
        return super.list(location, packageName, kinds, recurse)
    }

    override fun getClassLoader(location: JavaFileManager.Location): ClassLoader? {
        val classLoader = super.getClassLoader(location)
        if (location == StandardLocation.ANNOTATION_PROCESSOR_PATH) {
            if (classLoader is URLClassLoader) {
                return URLClassLoader(annotationProcessorPath.getAsURLArray(), AnnotationProcessorFilter.getFilteredClassLoader(classLoader.getParent()))
            }
        }

        return classLoader
    }

    companion object {
        /**
         * Overrides particular methods to prevent javac from accessing source files outside of Gradle's understanding or
         * classloaders outside of Gradle's control.
         */
        fun wrap(delegate: StandardJavaFileManager, annotationProcessorPath: ClassPath, hasEmptySourcePaths: Boolean): JavaFileManager {
            return GradleStandardJavaFileManager(delegate, annotationProcessorPath, hasEmptySourcePaths)
        }
    }
}

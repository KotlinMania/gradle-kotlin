/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.internal.initialization.loadercache

import com.google.common.collect.ImmutableList
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.ClassLoaderSpec
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.SystemClassLoaderSpec
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.tooling.internal.provider.serialization.ClientOwnedClassLoaderSpec
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory
import java.net.MalformedURLException
import java.net.URL

class ModelClassLoaderFactory(private val rootClassLoader: ClassLoader?) : PayloadClassLoaderFactory {
    override fun getClassLoaderFor(spec: ClassLoaderSpec?, parents: MutableList<out ClassLoader>): ClassLoader? {
        if (spec is SystemClassLoaderSpec) {
            return rootClassLoader
        }
        if (spec is MultiParentClassLoader.Spec) {
            return MultiParentClassLoader(parents)
        }
        require(parents.size == 1) { "Expected a single parent." }
        val parent: ClassLoader = parents.get(0)
        if (spec is VisitableURLClassLoader.Spec) {
            val clSpec = spec
            return VisitableURLClassLoader(clSpec.getName(), parent, clSpec.getClasspath())
        }
        if (spec is CachingClassLoader.Spec) {
            return CachingClassLoader(parent)
        }
        if (spec is FilteringClassLoader.Spec) {
            val clSpec = spec
            return FilteringClassLoader(parent, clSpec)
        }
        if (spec is ClientOwnedClassLoaderSpec) {
            val clSpec = spec
            return VisitableURLClassLoader(clSpec.javaClass.getName(), parent, convertToURLs(clSpec))
        }
        throw IllegalArgumentException(String.format("Don't know how to create a ClassLoader from spec %s", spec))
    }

    companion object {
        private fun convertToURLs(clSpec: ClientOwnedClassLoaderSpec): MutableList<URL?> {
            val classpath = clSpec.classpath
            val builder = ImmutableList.builderWithExpectedSize<URL?>(classpath.size)
            for (uri in classpath) {
                try {
                    builder.add(uri.toURL())
                } catch (e: MalformedURLException) {
                    throw RuntimeException(e)
                }
            }
            return builder.build()
        }
    }
}

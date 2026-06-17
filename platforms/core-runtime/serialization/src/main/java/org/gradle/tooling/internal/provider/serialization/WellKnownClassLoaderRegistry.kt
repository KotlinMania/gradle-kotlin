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
package org.gradle.tooling.internal.provider.serialization

import com.google.common.collect.ImmutableSet
import org.gradle.internal.classloader.ClassLoaderSpec
import org.gradle.internal.classloader.ClassLoaderUtils
import java.util.UUID

/**
 * A [PayloadClassLoaderRegistry] that maps classes loaded by several well known ClassLoaders: the JVM platform, Gradle core and Gradle plugins.
 *
 *
 * Delegates to another [PayloadClassLoaderRegistry] for all other classes.
 */
class WellKnownClassLoaderRegistry(private val delegate: PayloadClassLoaderRegistry) : PayloadClassLoaderRegistry {
    override fun newSerializeSession(): SerializeMap {
        val delegateSession = delegate.newSerializeSession()
        return object : SerializeMap {
            val knownLoaders: MutableMap<Short?, ClassLoaderDetails?> = HashMap<Short?, ClassLoaderDetails?>()

            override fun visitClass(target: Class<*>): Short {
                val classLoader = target.getClassLoader()
                if (classLoader == null || PLATFORM_CLASS_LOADERS.contains(classLoader)) {
                    knownLoaders.put(PLATFORM_CLASS_LOADER_ID, PLATFORM_CLASS_LOADER_DETAILS)
                    return PLATFORM_CLASS_LOADER_ID
                }
                return delegateSession.visitClass(target)
            }

            override fun collectClassLoaderDefinitions(details: MutableMap<Short?, ClassLoaderDetails?>) {
                delegateSession.collectClassLoaderDefinitions(details)
                details.putAll(knownLoaders)
            }
        }
    }

    override fun newDeserializeSession(): DeserializeMap {
        val delegateSession = delegate.newDeserializeSession()
        return object : DeserializeMap {
            @Throws(ClassNotFoundException::class)
            override fun resolveClass(classLoaderDetails: ClassLoaderDetails, className: String?): Class<*>? {
                if (classLoaderDetails.spec is KnownClassLoaderSpec) {
                    val knownClassLoaderSpec = classLoaderDetails.spec
                    when (knownClassLoaderSpec.id) {
                        PLATFORM_CLASS_LOADER_ID -> return Class.forName(className, false, PLATFORM_CLASS_LOADER)
                        else -> throw IllegalArgumentException("Unknown ClassLoader id specified.")
                    }
                }
                return delegateSession.resolveClass(classLoaderDetails, className)
            }
        }
    }

    private class KnownClassLoaderSpec(private val id: Short) : ClassLoaderSpec() {
        override fun equals(obj: Any?): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }

            val other = obj as KnownClassLoaderSpec
            return id == other.id
        }

        override fun hashCode(): Int {
            return id.toInt()
        }

        override fun toString(): String {
            return "{known-class-loader id: " + id + "}"
        }
    }

    companion object {
        private val PLATFORM_CLASS_LOADERS: MutableSet<ClassLoader?>
        private val PLATFORM_CLASS_LOADER = ClassLoaderUtils.getPlatformClassLoader()
        private val PLATFORM_CLASS_LOADER_ID: Short = -1
        private val PLATFORM_CLASS_LOADER_DETAILS = ClassLoaderDetails(UUID.randomUUID(), KnownClassLoaderSpec(PLATFORM_CLASS_LOADER_ID))

        init {
            val builder = ImmutableSet.builder<ClassLoader?>()
            var cl: ClassLoader? = PLATFORM_CLASS_LOADER
            while (cl != null) {
                builder.add(cl)
                cl = cl.getParent()
            }
            PLATFORM_CLASS_LOADERS = builder.build()
        }
    }
}

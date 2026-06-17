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

import com.google.common.collect.ImmutableList
import org.gradle.internal.UncheckedException
import org.gradle.internal.classloader.ClassLoaderSpec
import org.gradle.internal.classloader.ClassLoaderVisitor
import org.gradle.internal.classloader.SystemClassLoaderSpec
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID
import javax.annotation.concurrent.ThreadSafe

/**
 * A [PayloadClassLoaderRegistry] that maps classes loaded by a set of ClassLoaders that it manages. For ClassLoaders owned by this JVM, inspects the ClassLoader to determine a ClassLoader spec to send across to the peer JVM. For classes serialized from the peer, maintains a set of cached ClassLoaders created using the ClassLoader specs received from the peer.
 */
@ThreadSafe
class DefaultPayloadClassLoaderRegistry(private val cache: ClassLoaderCache, private val classLoaderFactory: PayloadClassLoaderFactory) : PayloadClassLoaderRegistry {
    private val detailsToClassLoader: ClassLoaderToDetailsTransformer = DefaultPayloadClassLoaderRegistry.ClassLoaderToDetailsTransformer()
    private val classLoaderToDetails: DetailsToClassLoaderTransformer = DefaultPayloadClassLoaderRegistry.DetailsToClassLoaderTransformer()

    override fun newSerializeSession(): SerializeMap {
        return object : SerializeMap {
            val classLoaderIds: MutableMap<ClassLoader?, Short?> = HashMap<ClassLoader?, Short?>()
            val classLoaderDetails: MutableMap<Short?, ClassLoaderDetails?> = HashMap<Short?, ClassLoaderDetails?>()

            override fun visitClass(target: Class<*>): Short {
                val classLoader = target.getClassLoader()
                var id = classLoaderIds.get(classLoader)
                if (id != null) {
                    return id
                }
                if (classLoaderIds.size == Short.MAX_VALUE.toInt()) {
                    throw UnsupportedOperationException()
                }
                val details = getDetails(classLoader)
                id = (classLoaderIds.size + 1).toShort()

                classLoaderIds.put(classLoader, id)
                classLoaderDetails.put(id, details)

                return id
            }

            override fun collectClassLoaderDefinitions(details: MutableMap<Short?, ClassLoaderDetails?>) {
                details.putAll(classLoaderDetails)
            }
        }
    }

    override fun newDeserializeSession(): DeserializeMap {
        return object : DeserializeMap {
            @Throws(ClassNotFoundException::class)
            override fun resolveClass(classLoaderDetails: ClassLoaderDetails, className: String?): Class<*> {
                val classLoader = getClassLoader(classLoaderDetails)
                return Class.forName(className, false, classLoader)
            }
        }
    }

    private fun getClassLoader(details: ClassLoaderDetails): ClassLoader? {
        val classLoader = cache.getClassLoader(details, detailsToClassLoader)
        // A single classloader is used in the daemon for a given set of client owned classloaders
        // When this classloader is reused for multiple requests, the classpath of subsequent requests may be different.
        // So, update the classpath of this shared classloader
        // It would be better to not combine client classloaders but instead to recreate the client side structure
        if (details.spec is ClientOwnedClassLoaderSpec) {
            val spec = details.spec
            val urlClassLoader = classLoader as VisitableURLClassLoader
            try {
                val currentClassPath: MutableSet<URI?> = uris(urlClassLoader)
                for (uri in spec.getClasspath()) {
                    if (!currentClassPath.contains(uri)) {
                        urlClassLoader.addURL(uri.toURL())
                    }
                }
            } catch (e: URISyntaxException) {
                throw UncheckedException.throwAsUncheckedException(e)
            } catch (e: MalformedURLException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }
        return classLoader
    }

    private fun getDetails(classLoader: ClassLoader?): ClassLoaderDetails? {
        return cache.getDetails(classLoader, classLoaderToDetails)
    }

    private class ClassLoaderSpecVisitor : ClassLoaderVisitor() {
        val parents: MutableList<ClassLoader?> = ArrayList<ClassLoader?>()
        var spec: ClassLoaderSpec? = null

        override fun visitParent(classLoader: ClassLoader) {
            parents.add(classLoader)
        }

        override fun visitSpec(spec: ClassLoaderSpec) {
            this.spec = spec
        }
    }

    private inner class ClassLoaderToDetailsTransformer : ClassLoaderCache.Transformer<ClassLoader?, ClassLoaderDetails?> {
        override fun transform(details: ClassLoaderDetails): ClassLoader {
            val parents: MutableList<ClassLoader?> = ArrayList<ClassLoader?>()
            for (parentDetails in details.parents) {
                parents.add(getClassLoader(parentDetails))
            }
            if (parents.isEmpty()) {
                parents.add(classLoaderFactory.getClassLoaderFor(SystemClassLoaderSpec.INSTANCE, ImmutableList.of<ClassLoader?>()))
            }

            LOGGER.info("Creating ClassLoader {} from {} and {}.", details.uuid, details.spec, parents)

            return classLoaderFactory.getClassLoaderFor(details.spec, parents)
        }
    }

    private inner class DetailsToClassLoaderTransformer : ClassLoaderCache.Transformer<ClassLoaderDetails?, ClassLoader?> {
        override fun transform(classLoader: ClassLoader): ClassLoaderDetails {
            val visitor = ClassLoaderSpecVisitor()
            visitor.visit(classLoader)

            val uuid = UUID.randomUUID()
            val details = ClassLoaderDetails(uuid, visitor.spec)
            for (parent in visitor.parents) {
                details.parents.add(getDetails(parent))
            }
            return details
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultPayloadClassLoaderRegistry::class.java)

        @Throws(URISyntaxException::class)
        private fun uris(classLoader: VisitableURLClassLoader): MutableSet<URI?> {
            val urls = classLoader.getURLs()
            val uris: MutableSet<URI?> = HashSet<URI?>(urls.size)
            for (url in urls) {
                uris.add(url.toURI())
            }
            return uris
        }
    }
}

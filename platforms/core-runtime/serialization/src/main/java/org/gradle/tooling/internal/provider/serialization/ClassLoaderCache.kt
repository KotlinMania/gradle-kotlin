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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.jspecify.annotations.NullMarked
import java.util.UUID
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
@ServiceScope(Scope.Global::class)
class ClassLoaderCache {
    @NullMarked
    interface Transformer<OUT, IN> {
        /**
         * Transforms the given object, and returns the transformed value.
         *
         * @param in The object to transform.
         * @return The transformed object.
         */
        fun transform(`in`: IN?): OUT?
    }


    private val lock: Lock = ReentrantLock()
    private val classLoaderDetails: Cache<ClassLoader?, ClassLoaderDetails?>
    private val classLoaderIds: Cache<UUID?, ClassLoader?>

    init {
        classLoaderDetails = CacheBuilder.newBuilder().weakKeys().build<ClassLoader?, ClassLoaderDetails?>()
        classLoaderIds = CacheBuilder.newBuilder().softValues().build<UUID?, ClassLoader?>()
    }

    fun getClassLoader(details: ClassLoaderDetails, factory: Transformer<ClassLoader?, ClassLoaderDetails?>): ClassLoader {
        lock.lock()
        try {
            var classLoader = classLoaderIds.getIfPresent(details.uuid)
            if (classLoader != null) {
                return classLoader
            }

            classLoader = factory.transform(details)
            classLoaderIds.put(details.uuid, classLoader)
            classLoaderDetails.put(classLoader, details)
            return classLoader!!
        } finally {
            lock.unlock()
        }
    }

    fun maybeGetDetails(classLoader: ClassLoader): ClassLoaderDetails? {
        lock.lock()
        try {
            return classLoaderDetails.getIfPresent(classLoader)
        } finally {
            lock.unlock()
        }
    }

    fun getDetails(classLoader: ClassLoader, factory: Transformer<ClassLoaderDetails?, ClassLoader?>): ClassLoaderDetails {
        lock.lock()
        try {
            var details = classLoaderDetails.getIfPresent(classLoader)
            if (details != null) {
                return details
            }

            details = factory.transform(classLoader)
            classLoaderDetails.put(classLoader, details)
            classLoaderIds.put(details!!.uuid, classLoader)
            return details
        } finally {
            lock.unlock()
        }
    }

    /**
     * Clears all entries in the cache.
     */
    fun clear() {
        lock.lock()
        try {
            for (classLoader in classLoaderDetails.asMap().keys) {
                ClassLoaderUtils.tryClose(classLoader)
            }
            classLoaderDetails.invalidateAll()
            classLoaderIds.invalidateAll()
        } finally {
            lock.unlock()
        }
    }
}

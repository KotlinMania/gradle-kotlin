/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import java.lang.AutoCloseable
import java.util.concurrent.Callable


/**
 * Simple guava-based classloader cache. Usually used with a very small size (&lt; 10),
 * as classloaders are strongly referenced.
 *
 * Keeping them strongly referenced allows us to correctly release resources for the evicted entries.
 */
class GuavaBackedClassLoaderCache<K>(maxSize: Int) : AutoCloseable {
    private val cache: Cache<K?, ClassLoader>


    init {
        cache = CacheBuilder
            .newBuilder()
            .maximumSize(maxSize.toLong())
            .removalListener<K?, ClassLoader?>(object : RemovalListener<K?, ClassLoader?> {
                override fun onRemoval(notification: RemovalNotification<K?, ClassLoader?>) {
                    val value = notification.value
                    if (value is AutoCloseable) {
                        try {
                            (value as AutoCloseable).close()
                        } catch (ex: Exception) {
                            throw RuntimeException("Failed to close classloader", ex)
                        }
                    }
                }
            })
            .build<K?, ClassLoader?>()
    }

    @Throws(Exception::class)
    fun get(key: K?, loader: Callable<ClassLoader?>): ClassLoader {
        return cache.get(key, loader)
    }

    fun clear() {
        cache.invalidateAll()
    }

    override fun close() {
        cache.invalidateAll()
    }
}

/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.project.antbuilder

import org.gradle.internal.classpath.ClassPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.ref.ReferenceQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock

internal class FinalizerThread(cacheEntries: MutableMap<ClassPath?, CacheEntry?>, lock: Lock) : Thread() {
    val referenceQueue: ReferenceQueue<CachedClassLoader?>
    private val stopped = AtomicBoolean()

    // Protects the following fields
    private val lock: Lock
    private val cleanups: MutableMap<ClassPath?, Cleanup?>
    private val cacheEntries: MutableMap<ClassPath?, CacheEntry?>

    init {
        this.setName("Classloader cache reference queue poller")
        this.setDaemon(true)
        this.referenceQueue = ReferenceQueue<CachedClassLoader?>()
        this.cacheEntries = cacheEntries
        this.cleanups = ConcurrentHashMap<ClassPath?, Cleanup?>()
        this.lock = lock
    }

    override fun run() {
        try {
            while (!stopped.get()) {
                val entry = referenceQueue.remove() as Cleanup
                val key = entry.key!!
                removeCacheEntry(key, entry, Cleanup.Mode.DONT_CLOSE_CLASSLOADER)
            }
        } catch (ex: InterruptedException) {
            currentThread().interrupt()
        }
    }

    private fun removeCacheEntry(key: ClassPath, entry: Cleanup, mode: Cleanup.Mode?) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Removing classloader from cache, classpath = {}", key.getAsURIs())
        }
        lock.lock()
        try {
            cacheEntries.remove(key)
            cleanups.remove(key)
        } finally {
            lock.unlock()
        }
        try {
            entry.clear()
            entry.cleanup(mode)
        } catch (ex: Exception) {
            LOG.error("Unable to perform cleanup of classloader for classpath: " + key, ex)
        }
    }

    fun exit() {
        stopped.set(true)
        interrupt()
        lock.lock()
        try {
            while (!cleanups.isEmpty()) {
                val entry = cleanups.entries.iterator().next()
                removeCacheEntry(entry.key!!, entry.value!!, Cleanup.Mode.CLOSE_CLASSLOADER)
            }
            LOG.debug("Completed shutdown")
        } finally {
            lock.unlock()
        }
    }

    fun putCleanup(key: ClassPath, cleanup: Cleanup) {
        cleanups.put(key, cleanup)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(FinalizerThread::class.java)
    }
}

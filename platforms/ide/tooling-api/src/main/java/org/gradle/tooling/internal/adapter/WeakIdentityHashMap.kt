/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.tooling.internal.adapter

import com.google.common.annotations.VisibleForTesting
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A specialized map wrapper, that uses weak references for keys and stores
 * values as strong references. It allows the garbage collector to collect keys when they are no longer in use.
 *
 * Keys are stored wrapped in `WeakIdentityHashMap.WeakKey` weak reference implementation, that uses `System.identityHashCode`
 * for generating hash code and considers referent equality for `equals` method.
 */
internal class WeakIdentityHashMap<K, V> {
    private val map = ConcurrentHashMap<WeakKey<K?>, V?>()
    private val referenceQueue = ReferenceQueue<K?>()

    fun put(key: K?, value: V?) {
        cleanUnreferencedKeys()
        map.put(WeakKey<K?>(key, referenceQueue), value)
    }

    fun get(key: K?): V? {
        cleanUnreferencedKeys()
        return map.get(WeakKey<K?>(key))
    }

    @VisibleForTesting
    fun keySet(): MutableSet<WeakKey<K?>> {
        cleanUnreferencedKeys()
        return map.keys
    }

    fun computeIfAbsent(key: K?, absentValueProvider: AbsentValueProvider<V?>): V? {
        cleanUnreferencedKeys()
        val weakKey = WeakKey<K?>(key, referenceQueue)
        return map.computeIfAbsent(weakKey) { k: WeakKey<K?>? -> absentValueProvider.provide() }
    }

    private fun cleanUnreferencedKeys() {
        var staleKey: WeakKey<K?>?
        while (((referenceQueue.poll() as WeakKey<K?>?).also { staleKey = it }) != null) {
            map.remove(staleKey)
        }
    }

    interface AbsentValueProvider<T> {
        fun provide(): T?
    }

    class WeakKey<T> @JvmOverloads constructor(referent: T?, q: ReferenceQueue<in T?>? = null) : WeakReference<T?>(referent, q) {
        private val hashCode: Int

        init {
            hashCode = System.identityHashCode(referent)
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj !is WeakKey<*>) {
                return false
            }
            val thisReferent: Any? = get()
            val objReferent: Any? = obj.get()

            if (thisReferent == null && objReferent == null) {
                return super.equals(obj)
            }

            return thisReferent === objReferent
        }

        override fun hashCode(): Int {
            return hashCode
        }
    }
}

/*
 * Copyright 2003 the original author or authors.
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
package org.gradle.internal.lazy

import java.util.function.Supplier
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.Volatile

/**
 * This is basically the same thing as Guava's NonSerializableMemoizingSupplier
 */
@ThreadSafe
internal class LockingLazy<T : Any?>(supplier: Supplier<T?>) : Lazy<T?> {
    @Volatile
    private var supplier: Supplier<T?>?

    @Volatile
    private var initialized = false

    // "value" does not need to be volatile;
    // visibility piggybacks on volatile read of "initialized".
    private var value: T? = null

    init {
        this.supplier = supplier
    }

    // NullAway cannot infer the invariants here:
    // !initialized => (supplier != null && value == null)
    // initialized => (supplier == null && value != null)
    override fun get(): T? {
        // A 2-field variant of Double-Checked Locking.
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    // `supplier` cannot be null here
                    val t: T? = supplier!!.get()
                    value = t
                    initialized = true
                    // Release the delegate to GC.
                    supplier = null
                    return t
                }
            }
        }
        // `value` cannot be null here.
        return value
    }
}

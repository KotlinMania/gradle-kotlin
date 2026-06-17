/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.attributes

interface CompatibilityRule<T> {
    fun doesSomething(): Boolean

    fun execute(tCompatibilityCheckResult: CompatibilityCheckResult<T?>)

    companion object {
        fun <E> doNothing(): CompatibilityRule<E?> {
            return DO_NOTHING as CompatibilityRule<E?>
        }

        /* private */
        val DO_NOTHING: CompatibilityRule<Any> = object : CompatibilityRule<Any> {
            override fun doesSomething(): Boolean {
                return false
            }

            override fun execute(t: CompatibilityCheckResult<Any>) {}
        }
    }
}

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
package org.gradle.internal.component.model

import org.gradle.internal.Cast.uncheckedCast
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function

interface ModuleSources {
    fun <T : ModuleSource?> getSource(sourceType: Class<T?>): Optional<T?>?

    fun withSources(consumer: Consumer<ModuleSource>)

    /**
     * Executes an action on the first source found of the following type, if any.
     */
    fun <T : ModuleSource?, R> withSource(sourceType: Class<T?>, action: Function<Optional<T?>, R?>): R? {
        return action.apply(getSource<T?>(sourceType)!!)
    }

    fun <T : ModuleSource?> withSources(sourceType: Class<T?>, consumer: Consumer<T?>) {
        withSources(Consumer { src: ModuleSource ->
            if (sourceType.isAssignableFrom(src.javaClass)) {
                consumer.accept(uncheckedCast<T?>(src))
            }
        })
    }

    fun size(): Int
}

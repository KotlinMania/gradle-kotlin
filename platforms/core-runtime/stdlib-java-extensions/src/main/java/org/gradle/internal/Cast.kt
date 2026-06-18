/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal

import org.jetbrains.annotations.Contract

object Cast {
    /**
     * Casts the given object to the given type, providing a better error message than the default.
     *
     * The standard [Class.cast] method produces unsatisfactory error messages on some platforms
     * when it fails. All this method does is provide a better, consistent, error message.
     *
     * This should be used whenever there is a chance the cast could fail. If in doubt, use this.
     *
     * @param outputType The type to cast the input to
     * @param object The object to be cast (must not be `null`)
     * @param <O> The type to be cast to
     * @param <I> The type of the object to be vast
     * @return The input object, cast to the output type
    </I></O> */
    @JvmStatic
    fun <O, I> cast(outputType: Class<O?>, `object`: I?): O? {
        try {
            return outputType.cast(`object`)
        } catch (e: ClassCastException) {
            throw ClassCastException(
                String.format(
                    "Failed to cast object %s of type %s to target type %s", `object`, `object`!!.javaClass.getName(), outputType.getName()
                )
            )
        }
    }

    /**
     * Casts the given object to the given type, providing a better error message than the default.
     *
     * The standard [Class.cast] method produces unsatisfactory error messages on some platforms
     * when it fails. All this method does is provide a better, consistent, error message.
     *
     * This should be used whenever there is a chance the cast could fail. If in doubt, use this.
     *
     * @param outputType The type to cast the input to
     * @param object The object to be cast
     * @param <O> The type to be cast to
     * @param <I> The type of the object to be vast
     * @return The input object, cast to the output type
    </I></O> */
    @JvmStatic
    fun <O, I> castNullable(outputType: Class<O?>, `object`: I?): O? {
        if (`object` == null) {
            return null
        }
        return cast<O?, I?>(outputType, `object`)
    }

    @JvmStatic
    @Contract("!null -> !null")
    fun <T> uncheckedCast(`object`: Any?): T? {
        return `object` as T?
    }

    @JvmStatic
    fun <T : Any> uncheckedNonnullCast(`object`: Any?): T {
        return `object` as T
    }

    /**
     * Strips nullability from the type. This is useful to work around type system limitations, but must be used carefully.
     * As a rule of thumb, do not use it outside situations where `T` is a type parameter that can hold both nullable and non-nullable types.
     *
     *
     * A typically safe pattern is to use it when the generic type parameter `T` can be nullable, but has to be mixed with `@Nullable T`.
     * For example:
     * <pre>
     * &lt;T extends@Nullable Object&gt; T doFoo(Supplier&lt;T&gt; factory) {
     * @Nullable T value = null;
     * value = factory.get();
     *
     * // value holds a valid instance of T (only allowing null if T is a nullable type).
     * // But NullAway cannot get it and produces an error.
     * return value;
     * }
    </pre> *
     *
     *
     * It is not possible to use `return Objects.requireNonNull(value)` because `value` can legitimately be null for `doFoo(() -> null)`.
     * NullAway doesn't allow to use [.uncheckedNonnullCast] either. Using `return Cast.unsafeStripNullable(value)` is actually safe.
     *
     *
     * **Put a comment explaining the reasoning why this cast is safe nearby.**
     *
     * @param object the nullable instance that actually holds a valid value of the type `T`
     * @return the given value with explicit nullability annotation stripped.
     * @param <T> the type
     * @see [NullAway docs on "downcasting"](https://github.com/uber/NullAway/wiki/Suppressing-Warnings.downcasting)
    </T> */
    // See the javadoc
    @JvmStatic
    fun <T : Any?> unsafeStripNullable(`object`: T?): T? {
        return `object`
    }
}

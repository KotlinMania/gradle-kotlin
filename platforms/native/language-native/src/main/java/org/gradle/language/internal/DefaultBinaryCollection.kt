/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.internal

import com.google.common.collect.ImmutableSet
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.specs.Spec
import org.gradle.internal.ImmutableActionSet
import org.gradle.language.BinaryCollection
import org.gradle.language.BinaryProvider
import org.gradle.util.internal.ConfigureUtil
import java.util.LinkedList
import javax.inject.Inject

// TODO - error messages
// TODO - display names for this container and the Provider implementations
class DefaultBinaryCollection<T : SoftwareComponent?> @Inject constructor(private val elementType: Class<T?>) : BinaryCollection<T?> {
    private enum class State {
        Collecting, Realizing, Finalized
    }

    private val elements: MutableSet<T?> = LinkedHashSet<T?>()
    private var pending: MutableList<SingleElementProvider<*>>? = LinkedList<SingleElementProvider<*>>()
    private var state = State.Collecting
    private var knownActions = ImmutableActionSet.empty<T?>()
    private var configureActions = ImmutableActionSet.empty<T?>()
    private var finalizeActions = ImmutableActionSet.empty<T?>()

    override fun <S> get(type: Class<S?>, spec: Spec<in S?>?): BinaryProvider<S?> {
        val provider: SingleElementProvider<S?> = DefaultBinaryCollection.SingleElementProvider<S?>(type, spec)
        if (state == State.Collecting) {
            pending!!.add(provider)
        } else {
            provider.selectNow()
        }
        return provider
    }

    override fun getByName(name: String?): BinaryProvider<T?> {
        return get<T?>(elementType, org.gradle.api.specs.Spec { element: T? -> element!!.getName() == name })
    }

    override fun get(spec: Spec<in T?>?): BinaryProvider<T?> {
        return get<T?>(elementType, spec)
    }

    override fun whenElementKnown(action: Action<in T?>) {
        check(state == State.Collecting) { "Cannot add actions to this collection as it has already been realized." }
        knownActions = knownActions.add(action)
    }

    override fun <S> whenElementKnown(type: Class<S?>, action: Action<in S?>) {
        whenElementKnown(TypeFilteringAction<T?, S?>(type, action))
    }

    override fun whenElementFinalized(action: Action<in T?>) {
        if (state == State.Finalized) {
            for (element in elements) {
                action.execute(element)
            }
        } else {
            finalizeActions = finalizeActions.add(action)
        }
    }

    override fun <S> whenElementFinalized(type: Class<S?>, action: Action<in S?>) {
        whenElementFinalized(TypeFilteringAction<T?, S?>(type, action))
    }

    override fun configureEach(action: Action<in T?>) {
        check(state == State.Collecting) { "Cannot add actions to this collection as it has already been realized." }
        configureActions = configureActions.add(action)
    }

    override fun <S> configureEach(type: Class<S?>, action: Action<in S?>) {
        configureEach(TypeFilteringAction<T?, S?>(type, action))
    }

    fun add(element: T?) {
        check(state == State.Collecting) { "Cannot add an element to this collection as it has already been realized." }
        elements.add(element)
    }

    /**
     * Realizes the contents of this collection, running configuration actions and firing notifications. No further elements can be added.
     */
    fun realizeNow() {
        check(state == State.Collecting) { "Cannot realize this collection as it has already been realized." }
        state = State.Realizing

        for (element in elements) {
            knownActions.execute(element)
        }
        knownActions = ImmutableActionSet.empty<T?>()

        for (provider in pending!!) {
            provider.selectNow()
        }
        pending = null

        for (element in elements) {
            configureActions.execute(element)
        }
        configureActions = ImmutableActionSet.empty<T?>()
        state = State.Finalized
        for (element in elements) {
            finalizeActions.execute(element)
        }
        finalizeActions = ImmutableActionSet.empty<T?>()
    }

    override fun get(): MutableSet<T?> {
        check(state == State.Finalized) { "Cannot query the elements of this container as the elements have not been created yet." }
        return ImmutableSet.copyOf<T?>(elements)
    }

    private inner class SingleElementProvider<S>(private val type: Class<S?>, private var spec: Spec<in S?>?) : AbstractMinimalProvider<S?>(), BinaryProvider<S?> {
        private var match: S? = null
        private var ambiguous = false

        fun selectNow() {
            for (element in elements) {
                if (type.isInstance(element) && spec!!.isSatisfiedBy(type.cast(element))) {
                    if (match != null) {
                        ambiguous = true
                        match = null
                        break
                    }
                    match = type.cast(element)
                }
            }
            spec = null
        }

        override fun getType(): Class<S?>? {
            return type
        }

        // Mix in some Groovy DSL support. Should decorate instead
        @Suppress("unused") // public API
        fun configure(closure: Closure<*>?) {
            configure(ConfigureUtil.configureUsing<S?>(closure))
        }

        override fun configure(action: Action<in S?>) {
            configureEach(Action { t: T? ->
                if (match === t) {
                    action.execute(match)
                }
            })
        }

        override fun whenFinalized(action: Action<in S?>) {
            whenElementFinalized(Action { t: T? ->
                if (match === t) {
                    action.execute(match)
                }
            })
        }

        override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): ValueSupplier.Value<S?> {
            check(!ambiguous) { "Found multiple elements" }
            return ValueSupplier.Value.ofNullable<S?>(match)
        }
    }

    private class TypeFilteringAction<T : SoftwareComponent?, S>(private val type: Class<S?>, private val action: Action<in S?>) : Action<T?> {
        override fun execute(t: T?) {
            if (type.isInstance(t)) {
                action.execute(type.cast(t))
            }
        }
    }
}

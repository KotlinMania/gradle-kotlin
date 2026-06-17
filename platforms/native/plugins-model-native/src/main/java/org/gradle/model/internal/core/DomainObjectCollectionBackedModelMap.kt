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
package org.gradle.model.internal.core

import com.google.common.base.Function
import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.api.Namer
import org.gradle.api.specs.Spec
import org.gradle.internal.Actions
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Specs
import org.gradle.model.ModelMap
import org.gradle.model.RuleSource

class DomainObjectCollectionBackedModelMap<T>(
    private val name: String?,
    private val elementType: Class<T?>,
    private val collection: DomainObjectCollection<T?>,
    private val instantiator: NamedEntityInstantiator<T?>,
    private val namer: Namer<in T?>,
    private val onCreateAction: Action<in T?>
) : ModelMapGroovyView<T?>() {
    override fun getName(): String? {
        return name
    }

    override fun getDisplayName(): String? {
        return collection.toString()
    }

    private fun <S> toNonSubtypeMap(type: Class<S?>): ModelMap<S?> {
        val cast: DomainObjectCollection<S?> = toNonSubtype<S?>(type)
        val castNamer = uncheckedCast<Namer<S?>?>(namer)
        return Companion.wrap<S?>(name, type, cast, NamedEntityInstantiators.nonSubtype<S?>(type, elementType), castNamer!!, Actions.doNothing<S?>())
    }

    private fun <S> toNonSubtype(type: Class<S?>): DomainObjectSet<S?> {
        return uncheckedCast<DomainObjectSet<S?>?>(collection.matching(Specs.isInstance<T?>(type)))!!
    }

    private fun <S : T?> toSubtypeMap(itemSubtype: Class<S?>): ModelMap<S?> {
        val instantiator = uncheckedCast<NamedEntityInstantiator<S?>?>(this.instantiator)
        return Companion.wrap<S?>(name, itemSubtype, collection.withType<S?>(itemSubtype), instantiator!!, namer, onCreateAction)
    }

    override fun get(name: String?): T? {
        return Iterables.find<T?>(collection, HasNamePredicate<T?>(name, namer), null)
    }

    override fun keySet(): MutableSet<String?> {
        return Sets.newHashSet<String?>(Iterables.transform<T?, String?>(collection, ToName<T?>(namer)))
    }

    override fun <S> withType(type: Class<S?>, configAction: Action<in S?>) {
        toNonSubtype<S?>(type).all(configAction)
    }

    private class HasNamePredicate<T>(private val name: String?, private val namer: Namer<in T?>) : Predicate<T?> {
        override fun apply(input: T?): Boolean {
            return namer.determineName(input) == name
        }
    }

    private class ToName<T>(private val namer: Namer<in T?>) : Function<T?, String?> {
        override fun apply(input: T?): String? {
            return namer.determineName(input)
        }
    }

    override fun size(): Int {
        return collection.size
    }

    override fun isEmpty(): Boolean {
        return collection.isEmpty()
    }

    override fun get(name: Any): T? {
        return get(name.toString())
    }

    override fun containsKey(name: Any): Boolean {
        return keySet().contains(name.toString())
    }

    override fun containsValue(item: Any?): Boolean {
        return collection.contains(item)
    }

    override fun create(name: String) {
        create<T?>(name, elementType, Actions.doNothing<T?>())
    }

    override fun create(name: String, configAction: Action<in T?>) {
        create<T?>(name, elementType, configAction)
    }

    override fun <S : T?> create(name: String, type: Class<S?>?) {
        create<S?>(name, type, Actions.doNothing<S?>())
    }

    override fun <S : T?> create(name: String, type: Class<S?>?, configAction: Action<in S?>) {
        check(!containsKey(name)) { "Entry with name already exists: " + name }
        val s = instantiator.create<S?>(name, type)
        configAction.execute(s)
        onCreateAction.execute(s)
        collection.add(s)
    }

    override fun put(name: String?, instance: T?) {
        throw UnsupportedOperationException()
    }

    override fun named(name: String?, ruleSource: Class<out RuleSource?>?) {
        throw UnsupportedOperationException()
    }

    override fun all(configAction: Action<in T?>) {
        collection.all(configAction)
    }

    override fun beforeEach(configAction: Action<in T?>) {
        all(configAction)
    }

    override fun <S> withType(type: Class<S?>?, rules: Class<out RuleSource?>?) {
        throw UnsupportedOperationException()
    }

    override fun afterEach(configAction: Action<in T?>) {
        all(configAction)
    }

    override fun values(): MutableCollection<T?> {
        return collection
    }

    override fun iterator(): MutableIterator<T?> {
        return collection.iterator()
    }

    override fun <S> beforeEach(type: Class<S?>, configAction: Action<in S?>) {
        withType<S?>(type, configAction)
    }

    override fun <S> afterEach(type: Class<S?>, configAction: Action<in S?>) {
        withType<S?>(type, configAction)
    }

    override fun named(name: String?, configAction: Action<in T?>) {
        collection.matching(object : Spec<T?> {
            override fun isSatisfiedBy(element: T?): Boolean {
                return get(name) === element
            }
        }).all(configAction)
    }

    override fun <S> withType(type: Class<S?>): ModelMap<S?> {
        if (type == elementType) {
            return uncheckedCast<ModelMap<S?>?>(this)!!
        }

        if (elementType.isAssignableFrom(type)) {
            val castType: Class<out T?> = uncheckedCast<Class<out T?>?>(type)!!
            val subType: ModelMap<out T?> = toSubtypeMap(castType)
            return uncheckedCast<ModelMap<S?>?>(subType)!!
        }

        return toNonSubtypeMap<S?>(type)
    }

    companion object {
        fun <T> wrap(
            name: String?,
            elementType: Class<T?>,
            domainObjectSet: DomainObjectCollection<T?>,
            instantiator: NamedEntityInstantiator<T?>,
            namer: Namer<in T?>,
            onCreate: Action<in T?>
        ): DomainObjectCollectionBackedModelMap<T?> {
            return DomainObjectCollectionBackedModelMap<T?>(name, elementType, domainObjectSet, instantiator, namer, onCreate)
        }
    }
}

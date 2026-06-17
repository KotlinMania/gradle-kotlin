/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal

import org.gradle.api.specs.Spec
import org.gradle.internal.Cast
import org.gradle.internal.Factory
import org.gradle.internal.Pair
import org.gradle.util.internal.CollectionUtils.diffSetsBy
import org.gradle.util.internal.CollectionUtils.filter
import org.gradle.util.internal.CollectionUtils.findFirst
import java.lang.reflect.Array
import java.util.AbstractList
import java.util.Arrays
import java.util.Collections
import java.util.Enumeration
import java.util.LinkedList
import java.util.function.Function

object CollectionUtils {
    /**
     * Returns the single element in the collection or throws.
     */
    fun <T> single(source: Iterable<out T?>): T? {
        val iterator: MutableIterator<out T?> = source.iterator()
        if (!iterator.hasNext()) {
            throw NoSuchElementException("Expecting collection with single element, got none.")
        }
        val element = iterator.next()
        require(!iterator.hasNext()) { "Expecting collection with single element, got multiple." }
        return element
    }

    fun <T> checkedCast(type: Class<T?>?, input: MutableCollection<*>): MutableCollection<out T?> {
        for (o in input) {
            Cast.castNullable<T?, Any?>(type, o)
        }
        return Cast.uncheckedNonnullCast<MutableCollection<out T?>?>(input)
    }

    fun <T> findFirst(source: Iterable<out T?>, filter: Spec<in T?>): T? {
        for (item in source) {
            if (filter.isSatisfiedBy(item)) {
                return item
            }
        }

        return null
    }

    fun <T> findFirst(source: Array<T?>, filter: Spec<in T?>): T? {
        for (thing in source) {
            if (filter.isSatisfiedBy(thing)) {
                return thing
            }
        }

        return null
    }

    fun <T> first(source: Iterable<out T?>): T? {
        return source.iterator().next()
    }

    fun <T> any(source: Iterable<out T?>, filter: Spec<in T?>): Boolean {
        return findFirst(source, filter) != null
    }

    fun <T> any(source: Array<T?>, filter: Spec<in T?>): Boolean {
        return findFirst<T?>(source, filter) != null
    }

    fun <T> filter(set: MutableSet<out T?>, filter: Spec<in T?>): MutableSet<T?> {
        return filter<T?, LinkedHashSet<T?>>(set, LinkedHashSet<T?>(), filter)!!
    }

    fun <T> filter(list: MutableList<out T?>, filter: Spec<in T?>): MutableList<T?> {
        return filter<T?, ArrayList<T?>>(list, ArrayList<T?>(list.size), filter)!!
    }

    fun <T> filter(array: Array<T?>, filter: Spec<in T?>): MutableList<T?> {
        return filter<T?, ArrayList<T?>>(Arrays.asList<T?>(*array), ArrayList<T?>(array.size), filter)!!
    }

    /**
     * Returns a sorted copy of the provided collection of things. Uses the provided comparator to sort.
     */
    fun <T> sort(things: Iterable<out T?>?, comparator: Comparator<in T?>?): MutableList<T?> {
        val copy = toMutableList<T?>(things)
        Collections.sort<T?>(copy, comparator)
        return copy
    }

    /**
     * Returns a sorted copy of the provided collection of things. Uses the natural ordering of the things.
     */
    fun <T : Comparable<T?>?> sort(things: Iterable<T?>?): MutableList<T?> {
        val copy = toMutableList<T?>(things)
        Collections.sort<T?>(copy)
        return copy
    }

    fun <T, C : MutableCollection<T?>?> filter(source: Iterable<out T?>, destination: C?, filter: Spec<in T?>): C? {
        for (item in source) {
            if (filter.isSatisfiedBy(item)) {
                destination!!.add(item)
            }
        }
        return destination
    }

    fun <K, V> filter(map: MutableMap<K?, V?>, filter: Spec<MutableMap.MutableEntry<K?, V?>?>): MutableMap<K?, V?> {
        return filter<K?, V?>(map, HashMap<K?, V?>(), filter)
    }

    fun <K, V> filter(map: MutableMap<K?, V?>, destination: MutableMap<K?, V?>, filter: Spec<MutableMap.MutableEntry<K?, V?>?>): MutableMap<K?, V?> {
        for (entry in map.entries) {
            if (filter.isSatisfiedBy(entry)) {
                destination.put(entry.key, entry.value)
            }
        }

        return destination
    }

    fun <R, I> collectArray(list: Array<I?>, newType: Class<R?>?, transformer: Function<in I?, out R?>): Array<R?> {
        val destination = Array.newInstance(newType, list.size) as kotlin.Array<R?>
        return collectArray<R?, I?>(list, destination, transformer)
    }

    fun <R, I> collectArray(list: kotlin.Array<I?>, destination: kotlin.Array<R?>, transformer: Function<in I?, out R?>): kotlin.Array<R?> {
        assert(list.size <= destination.size)
        for (i in list.indices) {
            destination[i] = transformer.apply(list[i])
        }
        return destination
    }

    fun <R, I> collect(list: kotlin.Array<I?>, transformer: Function<in I?, out R?>): MutableList<R?> {
        return collect<R?, I?>(Arrays.asList<I?>(*list), transformer)
    }

    fun <R, I> collect(set: MutableSet<out I?>, transformer: Function<in I?, out R?>): MutableSet<R?> {
        return collect(set, HashSet<R?>(set.size), transformer)!!
    }

    fun <R, I> collect(source: Iterable<out I?>, transformer: Function<in I?, out R?>): MutableList<R?> {
        if (source is MutableCollection<*>) {
            val collection = Cast.uncheckedNonnullCast<MutableCollection<out I?>?>(source)
            return collect(source, ArrayList<R?>(collection.size), transformer)!!
        } else {
            return collect(source, LinkedList<R?>(), transformer)!!
        }
    }

    fun <R, I, C : MutableCollection<R?>?> collect(source: Iterable<out I?>, destination: C?, transformer: Function<in I?, out R?>): C? {
        for (item in source) {
            destination!!.add(transformer.apply(item))
        }
        return destination
    }

    fun toStringList(iterable: Iterable<*>): MutableList<String?> {
        return stringize<LinkedList<String?>>(iterable, LinkedList<String?>())!!
    }

    /**
     * Recursively unpacks all the given things into a flat list.
     *
     * Nulls are not removed, they are left intact.
     *
     * @param things The things to flatten
     * @return A flattened list of the given things
     */
    @JvmStatic
    fun flattenCollections(vararg things: Any?): MutableList<*> {
        return CollectionUtils.flattenCollections<Any?>(Any::class.java, *things)
    }

    /**
     * Recursively unpacks all the given things into a flat list, ensuring they are of a certain type.
     *
     * Nulls are not removed, they are left intact.
     *
     * If a non-null object cannot be cast to the target type, a ClassCastException will be thrown.
     *
     * @param things The things to flatten
     * @param <T> The target type in the flattened list
     * @return A flattened list of the given things
    </T> */
    fun <T> flattenCollections(type: Class<T?>, vararg things: Any?): MutableList<T?> {
        if (things == null) {
            return mutableListOf<T?>(null)
        } else if (things.size == 0) {
            return mutableListOf<T?>()
        } else if (things.size == 1) {
            val thing: Any? = things[0]

            if (thing == null) {
                return mutableListOf<T?>(null)
            }

            // Casts to Class below are to workaround Eclipse compiler bug
            // See: https://github.com/gradle/gradle/pull/200
            if (thing.javaClass.isArray()) {
                val thingArray = thing as kotlin.Array<Any?>
                val list: MutableList<T?> = ArrayList<T?>(thingArray.size)
                for (thingThing in thingArray) {
                    list.addAll(flattenCollections<T?>(type, thingThing))
                }
                return list
            }

            if (thing is MutableCollection<*>) {
                val collection = thing
                val list: MutableList<T?> = ArrayList<T?>()
                for (element in collection) {
                    list.addAll(flattenCollections<T?>(type, element))
                }
                return list
            }

            return mutableListOf<T?>(Cast.cast<T?, Any?>(type, thing))
        } else {
            val list: MutableList<T?> = ArrayList<T?>()
            for (thing in things) {
                list.addAll(flattenCollections<T?>(type, thing))
            }
            return list
        }
    }

    /**
     * Repacks an [Iterable] into a [List].
     *
     *
     * When the input is a [List], it is returned as is.
     * Otherwise, the input is iterated and the elements are added to a new [List].
     *
     * @param things The things to repack
     * @return an empty list if the input is null, otherwise [List] containing the elements of the input otherwise
     */
    fun <T> toList(things: Iterable<out T?>?): MutableList<T?> {
        if (things is MutableList<*>) {
            val castThings = things as MutableList<T?>
            return castThings
        }
        return toMutableList<T?>(things)
    }

    fun <T> toList(things: Enumeration<out T?>): MutableList<T?> {
        val list: AbstractList<T?> = ArrayList<T?>()
        while (things.hasMoreElements()) {
            list.add(things.nextElement())
        }
        return list
    }

    private fun <T> toMutableList(things: Iterable<out T?>?): MutableList<T?> {
        if (things == null) {
            return ArrayList<T?>(0)
        }
        val list: MutableList<T?> = ArrayList<T?>()
        for (thing in things) {
            list.add(thing)
        }
        return list
    }


    fun <T> intersection(availableValuesByDescriptor: MutableCollection<out MutableCollection<T?>>): MutableList<T?> {
        val result: MutableList<T?> = ArrayList<T?>()
        val iterator: MutableIterator<out MutableCollection<T?>> = availableValuesByDescriptor.iterator()
        if (iterator.hasNext()) {
            val firstSet = iterator.next()
            result.addAll(firstSet)
            while (iterator.hasNext()) {
                val next = iterator.next()
                result.retainAll(next)
            }
        }
        return result
    }

    fun <T> toList(things: kotlin.Array<T?>?): MutableList<T?> {
        if (things == null || things.size == 0) {
            return ArrayList<T?>(0)
        }

        val list: MutableList<T?> = ArrayList<T?>(things.size)
        Collections.addAll<T?>(list, *things)
        return list
    }

    fun <T> toSet(things: Iterable<out T?>?): MutableSet<T?> {
        if (things == null) {
            return HashSet<T?>(0)
        }
        if (things is MutableSet<*>) {
            val castThings = things as MutableSet<T?>
            return castThings
        }

        val set: MutableSet<T?> = LinkedHashSet<T?>()
        for (thing in things) {
            set.add(thing)
        }
        return set
    }

    fun <E> compact(list: MutableList<E?>): MutableList<E?> {
        var compacted: MutableList<E?>? = null
        var i = 0

        for (element in list) {
            if (element == null) {
                if (compacted == null) {
                    // This is the first null element we've found, have to allocate a compacted list.
                    compacted = ArrayList<E?>(list.size)
                    if (i > 0) {
                        compacted.addAll(list.subList(0, i))
                    }
                }
            } else if (compacted != null) {
                compacted.add(element)
            }
            ++i
        }

        return if (compacted != null) compacted else list
    }

    // TODO(mlopatkin) This is a polynull function in disguise.
    //  You may end up here when fighting with NullAway because your source can contain nulls.
    //  Consider adding a null-taking overload then.
    fun <C : MutableCollection<String?>?> stringize(source: Iterable<*>, destination: C?): C? {
        return collect(source, destination) { value: Any? -> if (value == null) null else value.toString() }
    }

    fun stringize(source: MutableCollection<*>): MutableList<String?> {
        return stringize<ArrayList<String?>>(source, ArrayList<String?>(source.size))!!
    }

    fun <E> replace(list: MutableList<E?>, filter: Spec<in E?>, transformer: Function<in E?, out E?>): Boolean {
        var replaced = false
        var i = 0
        for (it in list) {
            if (filter.isSatisfiedBy(it)) {
                list.set(i, transformer.apply(it))
                replaced = true
            }
            ++i
        }
        return replaced
    }

    fun <K, V> collectMap(destination: MutableMap<K?, V?>, items: Iterable<out V?>, keyGenerator: Function<in V?, out K?>) {
        for (item in items) {
            destination.put(keyGenerator.apply(item), item)
        }
    }

    /**
     * Given a set of values, derive a set of keys and return a map
     */
    fun <K, V> collectMap(items: Iterable<out V?>, keyGenerator: Function<in V?, out K?>): MutableMap<K?, V?> {
        val map: MutableMap<K?, V?> = LinkedHashMap<K?, V?>()
        collectMap<K?, V?>(map, items, keyGenerator)
        return map
    }

    fun <K, V> collectMapValues(destination: MutableMap<K?, V?>, keys: Iterable<out K?>, keyGenerator: Function<in K?, out V?>) {
        for (item in keys) {
            destination.put(item, keyGenerator.apply(item))
        }
    }

    /**
     * Given a set of keys, derive a set of values and return a map
     */
    fun <K, V> collectMapValues(keys: Iterable<out K?>, keyGenerator: Function<in K?, out V?>): MutableMap<K?, V?> {
        val map: MutableMap<K?, V?> = LinkedHashMap<K?, V?>()
        collectMapValues<K?, V?>(map, keys, keyGenerator)
        return map
    }

    fun <T> every(things: Iterable<out T?>, predicate: Spec<in T?>): Boolean {
        for (thing in things) {
            if (!predicate.isSatisfiedBy(thing)) {
                return false
            }
        }

        return true
    }

    /**
     * Utility for adding an iterable to a collection.
     *
     * @param t1 The collection to add to
     * @param t2 The iterable to add each item of to the collection
     * @param <T> The element type of t1
     * @return t1
    </T> */
    fun <T, C : MutableCollection<in T?>?> addAll(t1: C?, t2: Iterable<out T?>): C? {
        for (t in t2) {
            t1!!.add(t)
        }
        return t1
    }

    /**
     * Utility for adding an array to a collection.
     *
     * @param t1 The collection to add to
     * @param t2 The iterable to add each item of to the collection
     * @param <T> The element type of t1
     * @return t1
    </T> */
    @SafeVarargs
    fun <T, C : MutableCollection<in T?>?> addAll(t1: C?, vararg t2: T?): C? {
        Collections.addAll<T?>(t1, *t2)
        return t1
    }

    /**
     * Provides a "diff report" of how the two sets are similar and how they are different, comparing the entries by some aspect.
     *
     * The transformer is used to generate the value to use to compare the entries by. That is, the entries are not compared by equals by an attribute or characteristic.
     *
     * The transformer is expected to produce a unique value for each entry in a single set. Behaviour is undefined if this condition is not met.
     *
     * @param left The set on the "left" side of the comparison.
     * @param right The set on the "right" side of the comparison.
     * @param compareBy Provides the value to compare entries from either side by
     * @param <T> The type of the entry objects
     * @return A representation of the difference
    </T> */
    fun <T> diffSetsBy(left: MutableSet<out T?>, right: MutableSet<out T?>, compareBy: Function<T?, *>): SetDiff<T?> {
        if (left == null) {
            throw NullPointerException("'left' set is null")
        }
        if (right == null) {
            throw NullPointerException("'right' set is null")
        }

        val setDiff = SetDiff<T?>()

        val indexedLeft = collectMap<Any?, T?>(left, compareBy)
        val indexedRight = collectMap<Any?, T?>(right, compareBy)

        for (leftEntry in indexedLeft.entries) {
            val rightValue = indexedRight.remove(leftEntry.key)
            if (rightValue == null) {
                setDiff.leftOnly.add(leftEntry.value)
            } else {
                val pair: Pair<T?, T?> = Pair.Companion.of<T?, T?>(leftEntry.value, rightValue)
                setDiff.common.add(pair)
            }
        }

        for (rightValue in indexedRight.values) {
            setDiff.rightOnly.add(rightValue)
        }

        return setDiff
    }

    /**
     * Creates a string with `toString()` of each object with the given separator.
     *
     * <pre>
     * expect:
     * join(",", new Object[]{"a"}) == "a"
     * join(",", new Object[]{"a", "b", "c"}) == "a,b,c"
     * join(",", new Object[]{}) == ""
    </pre> *
     *
     * The `separator` must not be null and `objects` must not be null.
     *
     * @param separator The string by which to join each string representation
     * @param objects The objects to join the string representations of
     * @return The joined string
     */
    fun join(separator: String, objects: kotlin.Array<Any?>): String {
        return join(separator, Arrays.asList<Any?>(*objects))
    }

    /**
     * Creates a string with `toString()` of each transformed object with the given separator.
     *
     * @see .join
     */
    fun <R, I> join(separator: String, objects: kotlin.Array<I?>, transformer: Function<in I?, out R?>): String {
        return join(separator, collect(objects, transformer))
    }

    /**
     * Creates a string with `toString()` of each object with the given separator.
     *
     * <pre>
     * expect:
     * join(",", ["a"]) == "a"
     * join(",", ["a", "b", "c"]) == "a,b,c"
     * join(",", []) == ""
    </pre> *
     *
     * The `separator` must not be null and `objects` must not be null.
     *
     * @param separator The string by which to join each string representation
     * @param objects The objects to join the string representations of
     * @return The joined string
     */
    fun join(separator: String, objects: Iterable<*>): String {
        if (separator == null) {
            throw NullPointerException("The 'separator' cannot be null")
        }
        if (objects == null) {
            throw NullPointerException("The 'objects' cannot be null")
        }

        val string = StringBuilder()
        val iterator: MutableIterator<*> = objects.iterator()
        if (iterator.hasNext()) {
            string.append(iterator.next().toString())
            while (iterator.hasNext()) {
                string.append(separator)
                string.append(iterator.next().toString())
            }
        }
        return string.toString()
    }

    /**
     * Creates a string with `toString()` of each transformed object with the given separator.
     *
     * @see .join
     */
    fun <R, I> join(separator: String, objects: Iterable<out I?>, transformer: Function<in I?, out R?>): String {
        return join(separator, collect(objects, transformer))
    }

    /**
     * Partition given Collection into a Pair of Collections.
     *
     * <pre>Left</pre> Collection containing entries that satisfy the given predicate
     * <pre>Right</pre> Collection containing entries that do NOT satisfy the given predicate
     */
    fun <T> partition(items: Iterable<T?>, predicate: Spec<in T?>): Pair<MutableCollection<T?>?, MutableCollection<T?>?> {
        if (items == null) {
            throw NullPointerException("Cannot partition null Collection")
        }
        if (predicate == null) {
            throw NullPointerException("Cannot apply null Spec when partitioning")
        }

        val left: MutableCollection<T?> = LinkedList<T?>()
        val right: MutableCollection<T?> = LinkedList<T?>()

        for (item in items) {
            if (predicate.isSatisfiedBy(item)) {
                left.add(item)
            } else {
                right.add(item)
            }
        }

        return Pair.Companion.of<MutableCollection<T?>?, MutableCollection<T?>?>(left, right)
    }

    fun <T> unpack(factories: Iterable<out Factory<out T?>?>): Iterable<out T?> {
        return object : Iterable<T?> {
            private val delegate: MutableIterator<out Factory<out T?>?> = factories.iterator()

            override fun iterator(): MutableIterator<T?> {
                return object : MutableIterator<T?> {
                    override fun hasNext(): Boolean {
                        return delegate.hasNext()
                    }

                    override fun next(): T? {
                        return delegate.next()!!.create()
                    }

                    override fun remove() {
                        throw UnsupportedOperationException()
                    }
                }
            }
        }
    }

    /**
     * The result of diffing two sets.
     *
     * @param <T> The type of element the sets contain
     * @see diffSetsBy
    </T> */
    class SetDiff<T> {
        var leftOnly: MutableSet<T?> = HashSet<T?>()
        var common: MutableSet<Pair<T?, T?>?> = HashSet<Pair<T?, T?>?>()
        @JvmField
        var rightOnly: MutableSet<T?> = HashSet<T?>()
    }
}

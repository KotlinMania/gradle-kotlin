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
package org.gradle.plugins.ide.internal.configurer

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import java.util.Collections

/**
 * A generic name de-duplicator for hierarchical elements.
 *
 *
 * Conflicting sub-elements are de-duplicated by prepending their parent element names, separated by a dash.
 * Conflicting root elements are rejected with an [IllegalArgumentException]
 *
 *
 * If a child's simple name already contains the name of its parent, the two prefixes are collapsed to keep names short.
 * For example, an elements with the name segments `root:impl:impl-simple` would initially get the name
 * `root-impl-impl-simple` and would then be shortened to `root-impl-simple`
 * This shortening is of course only applied if it does not introduce a new name conflict.
 *
 * @param <T> the type of element to de-duplicate
</T> */
class HierarchicalElementDeduplicator<T>(private val adapter: HierarchicalElementAdapter<T?>) {
    /**
     * Calculates a set of renamings for each duplicate name in the given set of elements.
     *
     * @param elements the elements with possibly duplicated names
     * @return a Map containing the new name for each element that has to be renamed
     */
    fun deduplicate(elements: Iterable<out T>): MutableMap<T?, String> {
        return HierarchicalElementDeduplicator.StatefulDeduplicator(elements).getNewNames()
    }

    /*
     * This inner class hides the fact that the actual de-duplication algorithm is stateful.
     */
    private inner class StatefulDeduplicator(elements: Iterable<out T>) {
        private val elements: MutableList<T?>
        private val elementsByName: Multimap<String, T?>
        private val originalNames: MutableMap<T?, String>
        private val newNames: MutableMap<T?, String>
        private val prefixes: MutableMap<T?, T?>

        init {
            this.elements = Lists.newArrayList<T?>(elements)
            this.elementsByName = LinkedHashMultimap.create<String, T?>()
            this.originalNames = HashMap<T?, String>()
            this.newNames = HashMap<T?, String>()
            this.prefixes = HashMap<T?, T?>()
        }

        fun getNewNames(): MutableMap<T?, String> {
            if (!elements.isEmpty() && newNames.isEmpty()) {
                calculateNewNames()
            }

            return ImmutableMap.copyOf<T?, String>(newNames)
        }

        fun calculateNewNames() {
            sortElementsByDepth()
            for (element in elements) {
                elementsByName.put(getOriginalName(element), element)
                prefixes.put(element, getParent(element))
            }
            while (!this.duplicateNames.isEmpty()) {
                deduplicate()
            }
            simplifyNames()
        }

        fun deduplicate() {
            for (duplicateName in this.duplicateNames) {
                val duplicatedElements: MutableCollection<T?> = elementsByName.get(duplicateName)
                val reservedNames: MutableSet<String> = ImmutableSet.copyOf<String>(elementsByName.keySet())
                val notYetRenamed: MutableSet<T?> = getNotYetRenamedElements(duplicatedElements)
                var deduplicationSuccessful = false
                val elementsToRename = if (notYetRenamed.isEmpty()) ImmutableSet.copyOf<T?>(duplicatedElements) else notYetRenamed
                for (element in elementsToRename) {
                    var elementRenamed = true
                    while (elementRenamed && reservedNames.contains(getCurrentlyAssignedName(element))) {
                        elementRenamed = renameUsingParentPrefix(element)
                        deduplicationSuccessful = deduplicationSuccessful or elementRenamed
                    }
                }
                if (!deduplicationSuccessful) {
                    // there was no more success in deduplication by path only
                    for (element in elementsToRename) {
                        var elementRenamed = true
                        while (elementRenamed && reservedNames.contains(getCurrentlyAssignedName(element))) {
                            elementRenamed = renameUsingIdentityName(element)
                            deduplicationSuccessful = deduplicationSuccessful or elementRenamed
                        }
                    }
                }

                require(deduplicationSuccessful) { "Duplicate root element " + duplicateName }
            }
        }

        fun renameUsingIdentityName(element: T?): Boolean {
            val currentlyAssignedName = getCurrentlyAssignedName(element)
            val originalName = getOriginalName(element)
            val identityName = getIdentityName(element)
            if (originalName != identityName && (currentlyAssignedName == originalName || currentlyAssignedName.endsWith("-" + originalName))) {
                val newName = currentlyAssignedName.substring(0, currentlyAssignedName.length - originalName.length) + identityName
                renameTo(element, newName)
                originalNames.put(element, identityName)
                return true
            }
            return false
        }

        fun renameUsingParentPrefix(element: T?): Boolean {
            val prefixElement = prefixes.get(element)
            if (prefixElement != null) {
                renameTo(element, getOriginalName(prefixElement) + "-" + getCurrentlyAssignedName(element))
                prefixes.put(element, getParent(prefixElement))
                return true
            }
            return false
        }

        fun renameTo(element: T?, newName: String) {
            elementsByName.remove(getCurrentlyAssignedName(element), element)
            elementsByName.put(newName, element)
            newNames.put(element, newName)
        }

        fun simplifyNames() {
            val deduplicatedNames = elementsByName.keySet()
            for (element in elements) {
                val simplifiedName = removeDuplicateWordsFromPrefix(getCurrentlyAssignedName(element), getOriginalName(element))
                if (!deduplicatedNames.contains(simplifiedName)) {
                    renameTo(element, simplifiedName)
                }
            }
        }

        fun removeDuplicateWordsFromPrefix(deduplicatedName: String, originalName: String): String {
            val prefix = deduplicatedName.substring(0, deduplicatedName.lastIndexOf(originalName))
            if (prefix.isEmpty()) {
                return deduplicatedName
            }

            val splitter = Splitter.on('-').omitEmptyStrings()
            val prefixParts: MutableList<String> = Lists.newArrayList<String>(splitter.split(prefix))
            val postfixParts: MutableList<String> = Lists.newArrayList<String>(splitter.split(originalName))
            val words: MutableList<String> = ArrayList<String>()

            if (postfixParts.size > 1) {
                val postfixHead = postfixParts.get(0)
                prefixParts.add(postfixHead)
                postfixParts.remove(postfixHead)
            }

            for (prefixPart in prefixParts) {
                if (prefixPart != Iterables.getLast<String>(words, null)) {
                    words.add(prefixPart)
                }
            }

            words.addAll(postfixParts)

            return Joiner.on('-').join(words)
        }

        val duplicateNames: MutableSet<String>
            get() {
                val duplicates: MutableSet<String> = LinkedHashSet<String>()
                for (entry in elementsByName.asMap().entries) {
                    if (entry.value.size > 1) {
                        duplicates.add(entry.key)
                    }
                }
                return duplicates
            }

        fun getNotYetRenamedElements(elementsToRename: MutableCollection<T?>): MutableSet<T?> {
            val notYetRenamed: MutableSet<T?> = LinkedHashSet<T?>()
            for (element in elementsToRename) {
                if (!hasBeenRenamed(element)) {
                    notYetRenamed.add(element)
                }
            }
            return notYetRenamed
        }

        fun getOriginalName(element: T?): String {
            if (originalNames.containsKey(element)) {
                return originalNames.get(element)!!
            }
            return adapter.getName(element)
        }

        fun getIdentityName(element: T?): String {
            return adapter.getIdentityName(element)
        }

        fun getCurrentlyAssignedName(element: T?): String {
            if (hasBeenRenamed(element)) {
                return newNames.get(element)!!
            } else {
                return getOriginalName(element)
            }
        }

        fun getParent(parent: T?): T? {
            return adapter.getParent(parent)
        }

        fun hasBeenRenamed(element: T?): Boolean {
            return newNames.containsKey(element)
        }

        fun sortElementsByDepth() {
            Collections.sort<T?>(elements, object : Comparator<T?> {
                override fun compare(left: T?, right: T?): Int {
                    return Integer.compare(getDepth(left), getDepth(right))
                }

                fun getDepth(element: T?): Int {
                    var depth = 0
                    var parent = element
                    while (parent != null) {
                        depth++
                        parent = getParent(parent)
                    }
                    return depth
                }
            })
        }
    }
}

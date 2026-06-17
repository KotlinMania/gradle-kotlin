/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.deps

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import com.google.common.collect.Sets
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.apache.commons.lang3.StringUtils
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentSetSerializer
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.Companion.dependencyToAll
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.AbstractCollectionSerializer.read
import org.gradle.internal.serialize.AbstractCollectionSerializer.write
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.HashCodeSerializer.read
import org.gradle.internal.serialize.HashCodeSerializer.write
import org.gradle.internal.serialize.HierarchicalNameSerializer
import org.gradle.internal.serialize.HierarchicalNameSerializer.read
import org.gradle.internal.serialize.HierarchicalNameSerializer.write
import org.gradle.internal.serialize.MapSerializer.read
import org.gradle.internal.serialize.MapSerializer.write
import org.gradle.internal.serialize.Serializer.read
import org.gradle.internal.serialize.Serializer.write
import java.util.ArrayDeque
import java.util.Deque
import java.util.function.Supplier

/**
 * Provides information about a set of classes, e.g. a JAR or a whole classpath.
 * Contains a hash for every class contained in the set, so it can determine which classes have changed compared to another set.
 * Contains a reverse dependency view, so we can determine which classes in this set are affected by a change to a class inside or outside this set.
 * Contains information about the accessible, inlineable constants in each class, since these require full recompilation of dependents if changed.
 * If analysis failed for any reason, that reason is captured and triggers full rebuilds if this class set is used.
 *
 * @see ClassSetAnalysis for the logic that calculates transitive dependencies.
 */
class ClassSetAnalysisData @JvmOverloads constructor(
    private val classHashes: MutableMap<String, HashCode?> = mutableMapOf<String?, HashCode?>(),
    private val dependents: MutableMap<String?, DependentsSet?> = mutableMapOf<String?, DependentsSet?>(),
    private val classesToConstants: MutableMap<String, IntSet?> = mutableMapOf<String?, IntSet?>(),
    private val fullRebuildCause: String? = null
) {
    /**
     * Returns a shrunk down version of this class set, which only contains information about types that could affect the other set.
     * This is useful for reducing the size of classpath snapshots, since a classpath usually contains a lot more types than the client
     * actually uses.
     *
     * Apart from the obvious classes that are directly used by the other set, we also need to keep any classes that might affect any number
     * of classes, like package-info, module-info and inlineable constants.
     */
    fun reduceToTypesAffecting(other: ClassSetAnalysisData, compilerApiData: CompilerApiData): ClassSetAnalysisData {
        if (fullRebuildCause != null) {
            return this
        }
        val usedClasses: MutableSet<String?> = HashSet<String?>(classHashes.size)
        for (entry in dependents.entries) {
            if (entry.value!!.isDependencyToAll) {
                usedClasses.add(entry.key)
            }
        }
        for (cls in classHashes.keys) {
            if (cls.endsWith(PACKAGE_INFO)) {
                usedClasses.add(cls)
            }
        }
        usedClasses.addAll(other.dependents.keys)

        val dependencies = this.forwardDependencyView

        val visited: MutableSet<String?> = HashSet<String?>(usedClasses.size)
        val pending: Deque<String?> = ArrayDeque<String?>(usedClasses)
        while (!pending.isEmpty()) {
            val cls = pending.poll()
            if (visited.add(cls)) {
                usedClasses.add(cls)
                pending.addAll(dependencies.get(cls))
            }
        }

        val usedConstantSources = if (compilerApiData.isSupportsConstantsMapping())
            compilerApiData.getConstantToClassMapping().constantDependents.keys
        else
            classesToConstants.keys

        usedClasses.addAll(usedConstantSources)

        val classHashes: MutableMap<String, HashCode?> = HashMap<String, HashCode?>(usedClasses.size)
        val dependents: MutableMap<String?, DependentsSet?> = HashMap<String?, DependentsSet?>(usedClasses.size)
        val classesToConstants: MutableMap<String, IntSet?> = HashMap<String, IntSet?>(usedClasses.size)
        for (usedClass in usedClasses) {
            val hash = this.classHashes.get(usedClass)
            if (hash != null) {
                classHashes.put(usedClass!!, hash)
                val dependentsSet = this.dependents.get(usedClass)
                if (dependentsSet != null) {
                    if (dependentsSet.isDependencyToAll) {
                        dependents.put(usedClass, dependentsSet)
                    } else {
                        val usedAccessibleClasses: MutableSet<String?> = HashSet<String?>(dependentsSet.accessibleDependentClasses)
                        usedAccessibleClasses.retainAll(usedClasses)
                        if (!usedAccessibleClasses.isEmpty()) {
                            dependents.put(usedClass, DependentsSet.dependentClasses(mutableSetOf<T?>(), usedAccessibleClasses))
                        }
                    }
                }
                val constants = this.classesToConstants.get(usedClass)
                if (constants != null && usedConstantSources.contains(usedClass)) {
                    classesToConstants.put(usedClass, constants)
                }
            }
        }

        return ClassSetAnalysisData(classHashes, dependents, classesToConstants, null)
    }

    private val forwardDependencyView: Multimap<String?, String?>
        /**
         * Takes the reverse dependency view of this set and reverses it, so it turns into a forward dependency view.
         * Excludes types that are dependencies to all others, these need to be handled separately by the caller.
         */
        get() {
            val dependencies: Multimap<String?, String?> =
                ArrayListMultimap.create<String?, String?>(dependents.size, 10)
            for (entry in dependents.entries) {
                if (entry.value!!.isDependencyToAll) {
                    continue
                }
                for (dependent in entry.value!!.accessibleDependentClasses!!) {
                    dependencies.put(dependent, entry.key)
                }
            }
            return dependencies
        }

    /**
     * Returns the additions, changes and removals compared to the other class set.
     * Only includes changes that could possibly trigger recompilation.
     * For example, adding a new class can't affect anyone, since it didn't exist before.
     *
     * Does not include classes that are transitively affected by the additions/removals/changes.
     */
    fun getChangedClassesSince(other: ClassSetAnalysisData): DependentsSet {
        if (fullRebuildCause != null) {
            return dependencyToAll(fullRebuildCause)
        }
        if (other.fullRebuildCause != null) {
            return dependencyToAll(other.fullRebuildCause)
        }

        val changed = ImmutableSet.builder<String?>()
        for (added in Sets.difference<String>(classHashes.keys, other.classHashes.keys)) {
            val dependents = getDependents(added)
            if (dependents.isDependencyToAll) {
                return dependents
            }
            if (added.endsWith(PACKAGE_INFO)) {
                changed.add(added)
            }
        }
        for (removedOrChanged in Sets.difference<MutableMap.MutableEntry<String?, HashCode?>>(other.classHashes.entries, classHashes.entries)) {
            val dependents = getDependents(removedOrChanged.key!!)
            if (dependents.isDependencyToAll) {
                return dependents
            }
            changed.add(removedOrChanged.key)
        }
        return DependentsSet.dependentClasses(ImmutableSet.of<E?>(), changed.build())!!
    }

    /**
     * Returns the dependents that directly depend on the given class.
     */
    fun getDependents(className: String): DependentsSet {
        if (fullRebuildCause != null) {
            return dependencyToAll(fullRebuildCause)
        }
        if (className == MODULE_INFO) {
            return dependencyToAll("module-info has changed")
        }
        if (className.endsWith(PACKAGE_INFO)) {
            val packageName = if (className == PACKAGE_INFO) null else StringUtils.removeEnd(className, "." + PACKAGE_INFO)
            return getDependentsOfPackage(packageName)
        }
        val dependentsSet = dependents.get(className)
        return if (dependentsSet == null) DependentsSet.empty() else dependentsSet
    }

    private fun getDependentsOfPackage(packageName: String?): DependentsSet {
        val typesInPackage: MutableSet<String?> = HashSet<String?>()
        for (type in classHashes.keys) {
            val i = type.lastIndexOf(".")
            if ((i < 0 && packageName == null) || (i > 0 && type.substring(0, i) == packageName)) {
                typesInPackage.add(type)
            }
        }
        return DependentsSet.dependentClasses(mutableSetOf<T?>(), typesInPackage)!!
    }

    /**
     * Gets the accessible, inlineable constants of the given class.
     */
    fun getConstants(className: String?): IntSet {
        val integers = classesToConstants.get(className)
        if (integers == null) {
            return IntSets.EMPTY_SET
        }
        return integers
    }

    class Serializer(private val classNameSerializerSupplier: Supplier<HierarchicalNameSerializer>) : AbstractSerializer<ClassSetAnalysisData?>() {
        private val hashCodeSerializer = HashCodeSerializer()

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ClassSetAnalysisData? {
            val hierarchicalNameSerializer = classNameSerializerSupplier.get()
            val dependentSetSerializer = DependentSetSerializer(Supplier { hierarchicalNameSerializer })
            var count = decoder.readSmallInt()
            val classHashes = ImmutableMap.builderWithExpectedSize<String?, HashCode?>(count)
            for (i in 0..<count) {
                val className = hierarchicalNameSerializer.read(decoder)
                val hashCode = hashCodeSerializer.read(decoder)
                classHashes.put(className, hashCode)
            }

            count = decoder.readSmallInt()
            val dependentsBuilder = ImmutableMap.builderWithExpectedSize<String?, DependentsSet?>(count)
            for (i in 0..<count) {
                val className = hierarchicalNameSerializer.read(decoder)
                val dependents = dependentSetSerializer.read(decoder)
                dependentsBuilder.put(className, dependents)
            }

            count = decoder.readSmallInt()
            val classesToConstantsBuilder = ImmutableMap.builderWithExpectedSize<String?, IntSet?>(count)
            for (i in 0..<count) {
                val className = hierarchicalNameSerializer.read(decoder)
                val constants: IntSet? = IntSetSerializer.Companion.INSTANCE.read(decoder)
                classesToConstantsBuilder.put(className, constants)
            }

            val fullRebuildCause = decoder.readNullableString()

            return ClassSetAnalysisData(classHashes.build(), dependentsBuilder.build(), classesToConstantsBuilder.build(), fullRebuildCause)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ClassSetAnalysisData) {
            val hierarchicalNameSerializer = classNameSerializerSupplier.get()
            val dependentSetSerializer = DependentSetSerializer(Supplier { hierarchicalNameSerializer })
            encoder.writeSmallInt(value.classHashes.size)
            for (entry in value.classHashes.entries) {
                hierarchicalNameSerializer.write(encoder, entry.key)
                hashCodeSerializer.write(encoder, entry.value)
            }

            encoder.writeSmallInt(value.dependents.size)
            for (entry in value.dependents.entries) {
                hierarchicalNameSerializer.write(encoder, entry.key)
                dependentSetSerializer.write(encoder, entry.value)
            }

            encoder.writeSmallInt(value.classesToConstants.size)
            for (entry in value.classesToConstants.entries) {
                hierarchicalNameSerializer.write(encoder, entry.key)
                IntSetSerializer.Companion.INSTANCE.write(encoder, entry.value)
            }
            encoder.writeNullableString(value.fullRebuildCause)
        }
    }

    companion object {
        const val MODULE_INFO: String = "module-info"
        const val PACKAGE_INFO: String = "package-info"

        /**
         * Merges the given class sets, applying classpath shadowing semantics. I.e. only the first occurrency of each class will be kept.
         */
        fun merge(datas: MutableList<ClassSetAnalysisData>): ClassSetAnalysisData {
            var classCount = 0
            var constantsCount = 0
            var dependentsCount = 0
            for (data in datas) {
                classCount += data.classHashes.size
                constantsCount += data.classesToConstants.size
                dependentsCount += data.dependents.size
            }

            val classHashes: MutableMap<String, HashCode?> = HashMap<String, HashCode?>(classCount)
            val classesToConstants: MutableMap<String, IntSet?> = HashMap<String, IntSet?>(constantsCount)
            val dependents: Multimap<String?, DependentsSet?> = ArrayListMultimap.create<String?, DependentsSet?>(dependentsCount, 10)
            var fullRebuildCause: String? = null

            for (data in Lists.reverse<ClassSetAnalysisData>(datas)) {
                classHashes.putAll(data.classHashes)
                classesToConstants.putAll(data.classesToConstants)
                data.dependents.forEach { (key: String?, value: DependentsSet?) -> dependents.put(key, value) }
                if (fullRebuildCause == null) {
                    fullRebuildCause = data.fullRebuildCause
                }
            }
            val mergedDependents = ImmutableMap.builderWithExpectedSize<String?, DependentsSet?>(dependents.size())
            for (entry in dependents.asMap().entries) {
                mergedDependents.put(entry.key, DependentsSet.merge(entry.value))
            }
            return ClassSetAnalysisData(classHashes, mergedDependents.build(), classesToConstants, fullRebuildCause)
        }
    }
}

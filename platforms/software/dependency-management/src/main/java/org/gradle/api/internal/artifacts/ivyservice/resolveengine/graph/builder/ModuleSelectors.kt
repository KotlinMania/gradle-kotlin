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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleSelectors.Companion.hasLatestSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState
import org.gradle.internal.component.model.IvyArtifactName
import java.lang.Boolean
import java.util.Collections
import kotlin.Comparator
import kotlin.Int

class ModuleSelectors<T : ResolvableSelectorState?>(versionComparator: Comparator<Version>, private val versionParser: VersionParser) : Iterable<T?> {
    private val emptyVersion: Version

    private val selectors: MutableList<T?> = ArrayList<T?>()
    private var deferSelection = false
    private var forced = false
    private val selectorComparator: Comparator<ResolvableSelectorState>

    init {
        this.emptyVersion = versionParser.transform("")
        this.selectorComparator = ModuleSelectors.SelectorComparator(versionComparator)
    }

    private inner class SelectorComparator(private val versionComparator: Comparator<Version>) : Comparator<ResolvableSelectorState?> {
        override fun compare(left: ResolvableSelectorState, right: ResolvableSelectorState): Int {
            if (right.isProject() == left.isProject()) {
                if (right.isFromLock() == left.isFromLock()) {
                    if (hasLatestSelector(right) == hasLatestSelector(left)) {
                        if (isDynamicSelector(right) == isDynamicSelector(left)) {
                            val o1RequiredVersion = this@ModuleSelectors.requiredVersion(right)
                            val o2RequiredVersion = this@ModuleSelectors.requiredVersion(left)
                            val compareRequiredVersion = versionComparator.compare(o1RequiredVersion, o2RequiredVersion)
                            if (compareRequiredVersion == 0) {
                                val o1Version = this@ModuleSelectors.preferredVersion(right)
                                val o2Version = this@ModuleSelectors.preferredVersion(left)
                                return versionComparator.compare(o1Version, o2Version)
                            } else {
                                return compareRequiredVersion
                            }
                        } else {
                            return Boolean.compare(isDynamicSelector(left), isDynamicSelector(right))
                        }
                    } else {
                        return Boolean.compare(hasLatestSelector(right), hasLatestSelector(left))
                    }
                } else {
                    return Boolean.compare(right.isFromLock(), left.isFromLock())
                }
            }
            return Boolean.compare(right.isProject(), left.isProject())
        }
    }

    fun checkDeferSelection(): kotlin.Boolean {
        if (deferSelection) {
            deferSelection = false
            return true
        }
        return false
    }

    override fun iterator(): MutableIterator<T?> {
        return selectors.iterator()
    }

    fun add(selector: T?, deferSelection: kotlin.Boolean) {
        this.deferSelection = deferSelection
        if (selectors.isEmpty() || forced) {
            selectors.add(selector)
        } else {
            doAdd(selector)
        }
        forced = forced || selector!!.isForce()
    }

    private fun doAdd(selector: T?) {
        val size = selectors.size
        if (size == 1) {
            doAddWhenListHasOneElement(selector)
        } else {
            doAddWhenListHasManyElements(selectors, selector, size)
        }
    }

    private fun doAddWhenListHasManyElements(selectors: MutableList<T?>, selector: T?, size: Int) {
        var insertionPoint = Collections.binarySearch<T?>(selectors, selector, selectorComparator)
        insertionPoint = advanceToPreserveOrder(selectors, selector, size, insertionPoint)
        if (insertionPoint < 0) {
            insertionPoint = insertionPoint.inv()
        }
        selectors.add(insertionPoint, selector)
    }

    private fun advanceToPreserveOrder(selectors: MutableList<T?>, selector: T?, size: Int, insertionPoint: Int): Int {
        var insertionPoint = insertionPoint
        while (insertionPoint > 0 && insertionPoint < size && selectorComparator.compare(selectors.get(insertionPoint), selector) == 0) {
            insertionPoint++
        }
        return insertionPoint
    }

    private fun doAddWhenListHasOneElement(selector: T?) {
        val first: T? = selectors.get(0)
        val c = selectorComparator.compare(first, selector)
        if (c <= 0) {
            selectors.add(selector)
        } else {
            selectors.add(0, selector)
        }
    }

    fun remove(selector: T?): kotlin.Boolean {
        return selectors.remove(selector)
    }

    private fun requiredVersion(selector: ResolvableSelectorState): Version {
        val versionConstraint = selector.getVersionConstraint()
        if (versionConstraint == null) {
            return emptyVersion
        }
        return versionOf(versionConstraint.requiredSelector)
    }

    private fun preferredVersion(selector: ResolvableSelectorState): Version {
        val versionConstraint = selector.getVersionConstraint()
        if (versionConstraint == null) {
            return emptyVersion
        }
        return versionOf(versionConstraint.preferredSelector)
    }

    private fun versionOf(selector: VersionSelector?): Version {
        if (selector !is ExactVersionSelector) {
            return emptyVersion
        }
        return versionParser.transform(selector.getSelector())
    }

    fun size(): Int {
        return selectors.size
    }

    fun first(): T? {
        if (size() == 0) {
            return null
        }
        return selectors.get(0)
    }

    val firstDependencyArtifact: IvyArtifactName?
        get() {
            for (selector in selectors) {
                val artifact = selector!!.getFirstDependencyArtifact()
                if (artifact != null) {
                    return artifact
                }
            }
            return null
        }

    companion object {
        private fun isDynamicSelector(selector: ResolvableSelectorState): kotlin.Boolean {
            return selector.getVersionConstraint() != null && selector.getVersionConstraint()!!.isDynamic
        }

        private fun hasLatestSelector(selector: ResolvableSelectorState): kotlin.Boolean {
            return selector.getVersionConstraint() != null
                    && Companion.hasLatestSelector(selector.getVersionConstraint()!!)
        }

        private fun hasLatestSelector(vc: ResolvedVersionConstraint): kotlin.Boolean {
            // Latest is only given priority if it's in a require
            return hasLatestSelector(vc.requiredSelector)
        }

        private fun hasLatestSelector(versionSelector: VersionSelector?): kotlin.Boolean {
            return versionSelector is LatestVersionSelector
        }
    }
}

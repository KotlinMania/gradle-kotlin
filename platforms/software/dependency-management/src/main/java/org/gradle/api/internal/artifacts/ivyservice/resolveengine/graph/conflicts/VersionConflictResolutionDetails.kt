/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import com.google.common.base.Objects
import org.gradle.api.Describable
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons

class VersionConflictResolutionDetails(candidates: MutableCollection<out ComponentResolutionState>) : Describable {
    val candidates: MutableCollection<out ComponentResolutionState>
    private val hashCode: Int

    init {
        this.candidates = candidates
        this.hashCode = candidates.hashCode()
    }

    override fun getDisplayName(): String {
        val sb = StringBuilder(16 + 16 * candidates.size)
        sb.append("between versions ")
        val it: MutableIterator<out ComponentResolutionState> = candidates.iterator()
        var more = false
        while (it.hasNext()) {
            val next = it.next()
            if (more) {
                if (it.hasNext()) {
                    sb.append(", ")
                } else {
                    sb.append(" and ")
                }
            }
            more = true
            sb.append(next.id.getVersion())
        }
        return sb.toString()
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        return Objects.equal(candidates, (o as VersionConflictResolutionDetails).candidates)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        /**
         * For a single module, conflict resolution can happen several times. However, we want to keep only one version
         * conflict resolution cause, listing all modules which participated in resolution. So this method is going to iterate
         * over all causes, and if it finds that version conflict resolution kicked in several times, will merge all candidates
         * in order to report it once with all candidates.
         *
         * This method tries its best not to create new lists if not required.
         *
         * @param descriptors all selection descriptors
         * @return a filtered descriptors list, with merged conflict version resolution
         */
        fun mergeCauses(descriptors: MutableList<ComponentSelectionDescriptorInternal>): MutableList<ComponentSelectionDescriptorInternal> {
            val byVersionConflictResolution: MutableList<VersionConflictResolutionDetails> = collectVersionConflictCandidates(descriptors)
            if (byVersionConflictResolution.size > 1) {
                val allCandidates: MutableSet<ComponentResolutionState> = mergeAllCandidates(byVersionConflictResolution)
                val merged: MutableList<ComponentSelectionDescriptorInternal> = ArrayList<ComponentSelectionDescriptorInternal>(descriptors.size - 1)
                var added = false
                for (descriptor in descriptors) {
                    if (isByVersionConflict(descriptor)) {
                        if (!added) {
                            merged.add(
                                ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(
                                    org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails(
                                        allCandidates
                                    )
                                )!!
                            )
                        }
                        added = true
                    } else {
                        merged.add(descriptor)
                    }
                }
                return merged
            }
            return descriptors
        }

        private fun mergeAllCandidates(byVersionConflictResolution: MutableList<VersionConflictResolutionDetails>): MutableSet<ComponentResolutionState> {
            val allCandidates: MutableSet<ComponentResolutionState> = LinkedHashSet<ComponentResolutionState>()
            for (versionConflictResolutionDetails in byVersionConflictResolution) {
                allCandidates.addAll(versionConflictResolutionDetails.candidates)
            }
            return allCandidates
        }

        private fun collectVersionConflictCandidates(descriptors: MutableList<ComponentSelectionDescriptorInternal>): MutableList<VersionConflictResolutionDetails> {
            val byVersionConflictResolution: MutableList<VersionConflictResolutionDetails> = ArrayList<VersionConflictResolutionDetails>(descriptors.size)
            for (descriptor in descriptors) {
                if (isByVersionConflict(descriptor)) {
                    byVersionConflictResolution.add((descriptor.describable as org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails?)!!)
                }
            }
            return byVersionConflictResolution
        }

        private fun isByVersionConflict(descriptor: ComponentSelectionDescriptorInternal): Boolean {
            return descriptor.getCause() == ComponentSelectionCause.CONFLICT_RESOLUTION
                    && descriptor.describable is VersionConflictResolutionDetails
        }
    }
}

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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.HashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

/**
 * This class is responsible for "normalizing" trusted PGP keys.
 * It tries to identify common super modules/groups/etc... which can
 * then be moved globally.
 *
 * It's worth noting that the result is _less strict_ than keeping all
 * trusted PGP keys at the artifact level, but it significantly reduces
 * the configuration file size and helps maintenance.
 */
internal class PgpKeyGrouper(private val verificationsBuilder: DependencyVerifierBuilder, private val entriesToBeWritten: MutableSet<VerificationEntry>) {
    fun performPgpKeyGrouping() {
        val keysToEntries = groupEntriesByPgpKey()
        keysToEntries.asMap()
            .entries
            .forEach(Consumer { e: MutableMap.MutableEntry<String, MutableCollection<PgpEntry>>? ->
                // Filter out anything for which we have a trusted key entry already
                val pgpKeys: MutableCollection<PgpEntry> = e!!.value.stream()
                    .filter { entry: PgpEntry ->
                        verificationsBuilder.trustedKeys.stream()
                            .filter { trustedKey: DependencyVerificationConfiguration.TrustedKey? -> trustedKey!!.keyId == e.key }
                            .noneMatch { trustedKey: DependencyVerificationConfiguration.TrustedKey? -> entry.checkAndMarkSatisfiedBy(trustedKey!!) }
                    }
                    .collect(Collectors.toList())
                if (pgpKeys.size > 1) {
                    // if there's only one entry, we won't "normalize" into globally trusted keys
                    val moduleComponentIds = pgpKeys.stream()
                        .map<ModuleComponentArtifactIdentifier> { obj: PgpEntry? -> obj!!.getId() }
                        .map<ModuleComponentIdentifier> { obj: ModuleComponentArtifactIdentifier? -> obj!!.getComponentIdentifier() }
                        .distinct()
                        .collect(Collectors.toList())
                    if (moduleComponentIds.size == 1) {
                        groupByModuleComponentId(e, moduleComponentIds)
                    } else {
                        val moduleIds = moduleComponentIds.stream()
                            .map<ModuleIdentifier> { obj: ModuleComponentIdentifier? -> obj!!.getModuleIdentifier() }
                            .distinct()
                            .collect(Collectors.toList())
                        if (moduleIds.size == 1) {
                            groupByModuleId(e, moduleIds)
                        } else {
                            val groups = moduleIds.stream()
                                .map<String> { obj: ModuleIdentifier? -> obj!!.getGroup() }
                                .distinct()
                                .collect(Collectors.toList())
                            if (groups.size == 1) {
                                groupByGroupOnly(e, groups)
                            } else {
                                groupUsingRegex(e, groups)
                                processRemainingGroups(e, groups)
                            }
                        }
                    }
                }
            })
    }

    private fun processRemainingGroups(e: MutableMap.MutableEntry<String, MutableCollection<PgpEntry>>, groups: MutableList<String>) {
        val keyId = e.key
        val remainingUntouched = e.value
            .stream()
            .filter { p: PgpEntry? -> p!!.doesNotDeclareKeyGlobally(keyId) }
            .collect(Collectors.toList())
        for (group in groups) {
            val count = remainingUntouched.stream().filter { p: PgpEntry? -> p!!.getGroup() == group }.count()
            if (count > 1) {
                // a key is at least used in 2 artifacts
                verificationsBuilder.addTrustedKey(
                    keyId,
                    group,
                    null,
                    null,
                    null,
                    false
                )
                remainingUntouched
                    .stream()
                    .filter { p: PgpEntry? -> p!!.getGroup() == group }
                    .forEach { p: PgpEntry? -> p!!.keyDeclaredGlobally(keyId) }
            }
        }
    }

    private fun groupUsingRegex(e: MutableMap.MutableEntry<String, MutableCollection<PgpEntry>>, groups: MutableList<String>) {
        val keyID = e.key
        val commonPrefixes: MutableList<MutableList<String>> = tryComputeCommonPrefixes(groups)
        for (prefix in commonPrefixes) {
            val groupRegex = "^" + GROUP_JOINER.join(prefix) + GROUP_SUFFIX
            verificationsBuilder.addTrustedKey(
                e.key,
                groupRegex,
                null,
                null,
                null,
                true
            )
            for (pgpEntry in e.value) {
                if (pgpEntry.getGroup().matches(groupRegex.toRegex())) {
                    pgpEntry.keyDeclaredGlobally(keyID)
                }
            }
        }
    }

    private fun markKeyDeclaredGlobally(e: MutableMap.MutableEntry<String, MutableCollection<PgpEntry>>) {
        val keyID = e.key
        for (pgpEntry in e.value) {
            pgpEntry.keyDeclaredGlobally(keyID)
        }
    }

    private fun groupByGroupOnly(e: MutableMap.MutableEntry<String, MutableCollection<PgpEntry>>, groups: MutableList<String>) {
        val group = groups.get(0)
        verificationsBuilder.addTrustedKey(
            e.key,
            group,
            null,
            null,
            null,
            false
        )
        markKeyDeclaredGlobally(e)
    }

    private fun groupByModuleId(e: MutableMap.MutableEntry<String, MutableCollection<PgpEntry>>, moduleIds: MutableList<ModuleIdentifier>) {
        val mi = moduleIds.get(0)
        verificationsBuilder.addTrustedKey(
            e.key,
            mi.getGroup(),
            mi.getName(),
            null,
            null,
            false
        )
        markKeyDeclaredGlobally(e)
    }

    private fun groupByModuleComponentId(e: MutableMap.MutableEntry<String, MutableCollection<PgpEntry>>, moduleComponentIds: MutableList<ModuleComponentIdentifier>) {
        val mci = moduleComponentIds.get(0)
        verificationsBuilder.addTrustedKey(
            e.key,
            mci.getGroup(),
            mci.getModule(),
            mci.getVersion(),
            null,
            false
        )
        markKeyDeclaredGlobally(e)
    }

    private fun groupEntriesByPgpKey(): Multimap<String, PgpEntry> {
        val keysToEntries: Multimap<String, PgpEntry> = HashMultimap.create<String, PgpEntry>()
        entriesToBeWritten.stream()
            .filter { obj: VerificationEntry? -> PgpEntry::class.java.isInstance(obj) }
            .map<PgpEntry> { obj: VerificationEntry? -> PgpEntry::class.java.cast(obj) }
            .filter { e: PgpEntry? -> !e!!.getTrustedKeys().isEmpty() }
            .forEach { e: PgpEntry? ->
                for (trustedKey in e!!.getTrustedKeys()) {
                    keysToEntries.put(trustedKey, e)
                }
            }
        return keysToEntries
    }

    companion object {
        private val GROUP_SPLITTER = Splitter.on(".")
        private const val GROUP_SUFFIX = "($|([.].*))"
        private val GROUP_JOINER = Joiner.on("[.]")

        // Tries to find the common super-group for a list of groups
        // For example given ["org.foo", "org.foo.bar", "org.foo.baz"] it will group using "org.foo.*"
        fun tryComputeCommonPrefixes(groups: MutableList<String>): MutableList<MutableList<String>> {
            val splitGroups = groups.stream()
                .map<MutableList<String>> { sequence: String? -> GROUP_SPLITTER.splitToList(sequence!!) }
                .sorted(Comparator.comparing<MutableList<String>, Int>(Function { obj: MutableList<String> -> obj.size }))
                .collect(Collectors.toList())
            var shortest = splitGroups.get(0)
            if (shortest.size < 2) {
                // we need at least a prefix of 2 elements, like "com.mycompany", to perform grouping
                return mutableListOf<MutableList<String>>()
            }
            val commonPrefixes: MutableList<MutableList<String>> = ArrayList<MutableList<String>>()
            val remainder: MutableList<MutableList<String>> = Lists.newArrayList<MutableList<String>>(splitGroups)
            var previous: MutableList<MutableList<String>>?
            while (!remainder.isEmpty()) {
                previous = Lists.newArrayList<MutableList<String>>(remainder)
                shortest = remainder.get(0)
                var prefixLen = 2
                var prefix = shortest.subList(0, prefixLen)
                var commonPrefix: MutableList<String>? = null
                var candidatesWithSamePrefix: MutableList<MutableList<String>> = Lists.newArrayList<MutableList<String>>(remainder)
                while ((samePrefix(prefixLen, prefix, candidatesWithSamePrefix).also { candidatesWithSamePrefix = it }).size > 1) {
                    remainder.removeAll(candidatesWithSamePrefix)
                    commonPrefix = prefix
                    prefixLen++
                    if (prefixLen <= shortest.size) {
                        prefix = shortest.subList(0, prefixLen)
                    } else {
                        break
                    }
                }
                if (commonPrefix != null) {
                    commonPrefixes.add(commonPrefix)
                }
                if (remainder == previous) {
                    // could do nothing with the first, let's go with the next one
                    remainder.removeAt(0)
                }
            }
            return commonPrefixes
        }

        private fun samePrefix(prefixLen: Int, prefix: MutableList<String>, candidates: MutableList<MutableList<String>>): MutableList<MutableList<String>> {
            return candidates.stream().filter { groups: MutableList<String?>? -> groups!!.subList(0, prefixLen) == prefix }.collect(Collectors.toList())
        }
    }
}

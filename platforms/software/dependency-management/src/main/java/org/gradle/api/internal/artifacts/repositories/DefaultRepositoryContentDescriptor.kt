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
package org.gradle.api.internal.artifacts.repositories

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.internal.Actions
import org.gradle.internal.Cast.uncheckedCast
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import java.util.regex.Pattern

internal open class DefaultRepositoryContentDescriptor(val repositoryNameSupplier: Supplier<String>, protected val versionParser: VersionParser) : RepositoryContentDescriptorInternal {
    private enum class MatcherKind {
        SIMPLE,
        REGEX,
        SUB_GROUP,
    }

    private var includedConfigurations: MutableSet<String>? = null
    private var excludedConfigurations: MutableSet<String>? = null
    private var includeSpecs: MutableSet<ContentSpec>? = null
    private var excludeSpecs: MutableSet<ContentSpec>? = null
    private var requiredAttributes: MutableMap<Attribute<Any>, MutableSet<Any>>? = null
    private var locked = false

    private var cachedAction: Action<in ArtifactResolutionDetails>? = null
    private val versionSelectorScheme: VersionSelectorScheme
    private val versionSelectors = ConcurrentHashMap<String, VersionSelector>()

    init {
        this.versionSelectorScheme = DefaultVersionSelectorScheme(
            DefaultVersionComparator(),
            versionParser
        )
    }

    private fun assertMutable() {
        check(!locked) {
            "Cannot mutate content repository descriptor '" +
                    repositoryNameSupplier.get() +
                    "' after repository has been used"
        }
    }

    override fun toContentFilter(): Action<in ArtifactResolutionDetails> {
        if (cachedAction != null) {
            return cachedAction!!
        }
        locked = true
        if (includeSpecs == null && excludeSpecs == null) {
            // no filtering in place
            return Actions.doNothing<ArtifactResolutionDetails>()
        }
        cachedAction = RepositoryFilterAction(createSpecMatchers(includeSpecs), createSpecMatchers(excludeSpecs))
        return cachedAction!!
    }

    override fun asMutableCopy(): RepositoryContentDescriptorInternal {
        val copy = DefaultRepositoryContentDescriptor(
            repositoryNameSupplier,
            this.versionParser
        )
        if (includedConfigurations != null) {
            copy.includedConfigurations = Sets.newHashSet<String>(includedConfigurations)
        }
        if (excludedConfigurations != null) {
            copy.excludedConfigurations = Sets.newHashSet<String>(excludedConfigurations)
        }
        if (includeSpecs != null) {
            copy.includeSpecs = Sets.newHashSet<ContentSpec>(includeSpecs)
        }
        if (excludeSpecs != null) {
            copy.excludeSpecs = Sets.newHashSet<ContentSpec>(excludeSpecs)
        }
        if (requiredAttributes != null) {
            copy.requiredAttributes = Maps.newHashMap<Attribute<Any>, MutableSet<Any>>(requiredAttributes)
        }
        return copy
    }

    override fun includeGroup(group: String) {
        checkNotNull(group, "Group cannot be null")
        addInclude(group, null, null, MatcherKind.SIMPLE)
    }

    override fun includeGroupAndSubgroups(groupPrefix: String) {
        checkNotNull(groupPrefix, "Group prefix cannot be null")
        addInclude(groupPrefix, null, null, MatcherKind.SUB_GROUP)
    }

    override fun includeGroupByRegex(groupRegex: String) {
        checkNotNull(groupRegex, "Group regex cannot be null")
        addInclude(groupRegex, null, null, MatcherKind.REGEX)
    }

    override fun includeModule(group: String, moduleName: String) {
        checkNotNull(group, "Group cannot be null")
        checkNotNull(moduleName, "Module name cannot be null")
        addInclude(group, moduleName, null, MatcherKind.REGEX)
    }

    override fun includeModuleByRegex(groupRegex: String, moduleNameRegex: String) {
        checkNotNull(groupRegex, "Group regex cannot be null")
        checkNotNull(moduleNameRegex, "Module name regex cannot be null")
        addInclude(groupRegex, moduleNameRegex, null, MatcherKind.REGEX)
    }

    override fun includeVersion(group: String, moduleName: String, version: String) {
        checkNotNull(group, "Group cannot be null")
        checkNotNull(moduleName, "Module name cannot be null")
        checkNotNull(version, "Version cannot be null")
        addInclude(group, moduleName, version, MatcherKind.SIMPLE)
    }

    override fun includeVersionByRegex(groupRegex: String, moduleNameRegex: String, versionRegex: String) {
        checkNotNull(groupRegex, "Group regex cannot be null")
        checkNotNull(moduleNameRegex, "Module name regex cannot be null")
        checkNotNull(versionRegex, "Version regex cannot be null")
        addInclude(groupRegex, moduleNameRegex, versionRegex, MatcherKind.REGEX)
    }

    private fun addInclude(group: String, moduleName: String?, version: String?, matcherKind: MatcherKind) {
        assertMutable()
        if (includeSpecs == null) {
            includeSpecs = HashSet<ContentSpec>()
        }
        includeSpecs!!.add(ContentSpec(matcherKind, group, moduleName, version, versionSelectorScheme, versionSelectors, true))
    }

    override fun excludeGroup(group: String) {
        checkNotNull(group, "Group cannot be null")
        addExclude(group, null, null, MatcherKind.SIMPLE)
    }

    override fun excludeGroupAndSubgroups(groupPrefix: String) {
        checkNotNull(groupPrefix, "Group prefix cannot be null")
        addExclude(groupPrefix, null, null, MatcherKind.SUB_GROUP)
    }

    override fun excludeGroupByRegex(groupRegex: String) {
        checkNotNull(groupRegex, "Group regex cannot be null")
        addExclude(groupRegex, null, null, MatcherKind.REGEX)
    }

    override fun excludeModule(group: String, moduleName: String) {
        checkNotNull(group, "Group cannot be null")
        checkNotNull(moduleName, "Module name cannot be null")
        addExclude(group, moduleName, null, MatcherKind.SIMPLE)
    }

    override fun excludeModuleByRegex(groupRegex: String, moduleNameRegex: String) {
        checkNotNull(groupRegex, "Group regex cannot be null")
        checkNotNull(moduleNameRegex, "Module name regex cannot be null")
        addExclude(groupRegex, moduleNameRegex, null, MatcherKind.REGEX)
    }

    override fun excludeVersion(group: String, moduleName: String, version: String) {
        checkNotNull(group, "Group cannot be null")
        checkNotNull(moduleName, "Module name cannot be null")
        checkNotNull(version, "Version cannot be null")
        addExclude(group, moduleName, version, MatcherKind.SIMPLE)
    }

    override fun excludeVersionByRegex(groupRegex: String, moduleNameRegex: String, versionRegex: String) {
        checkNotNull(groupRegex, "Group regex cannot be null")
        checkNotNull(moduleNameRegex, "Module name regex cannot be null")
        checkNotNull(versionRegex, "Version regex cannot be null")
        addExclude(groupRegex, moduleNameRegex, versionRegex, MatcherKind.REGEX)
    }

    private fun addExclude(group: String, moduleName: String?, version: String?, matcherKind: MatcherKind) {
        assertMutable()
        if (excludeSpecs == null) {
            excludeSpecs = HashSet<ContentSpec>()
        }
        excludeSpecs!!.add(ContentSpec(matcherKind, group, moduleName, version, versionSelectorScheme, versionSelectors, false))
    }

    override fun onlyForConfigurations(vararg configurationNames: String) {
        if (includedConfigurations == null) {
            includedConfigurations = HashSet<String>()
        }
        Collections.addAll<String>(includedConfigurations, *configurationNames)
    }

    override fun notForConfigurations(vararg configurationNames: String) {
        if (excludedConfigurations == null) {
            excludedConfigurations = HashSet<String>()
        }
        Collections.addAll<String>(excludedConfigurations, *configurationNames)
    }

    override fun <T> onlyForAttribute(attribute: Attribute<T?>, vararg validValues: T?) {
        if (requiredAttributes == null) {
            requiredAttributes = HashMap<Attribute<Any>, MutableSet<Any>>()
        }
        requiredAttributes!!.put(uncheckedCast<Attribute<Any>?>(attribute)!!, ImmutableSet.copyOf<Any>(validValues))
    }

    override fun getIncludedConfigurations(): MutableSet<String>? {
        return includedConfigurations
    }

    fun setIncludedConfigurations(includedConfigurations: MutableSet<String>?) {
        this.includedConfigurations = includedConfigurations
    }

    override fun getExcludedConfigurations(): MutableSet<String>? {
        return excludedConfigurations
    }

    fun setExcludedConfigurations(excludedConfigurations: MutableSet<String>?) {
        this.excludedConfigurations = excludedConfigurations
    }

    fun getIncludeSpecs(): MutableSet<ContentSpec>? {
        return includeSpecs
    }

    fun setIncludeSpecs(includeSpecs: MutableSet<ContentSpec>?) {
        this.includeSpecs = includeSpecs
    }

    fun getExcludeSpecs(): MutableSet<ContentSpec>? {
        return excludeSpecs
    }

    fun setExcludeSpecs(excludeSpecs: MutableSet<ContentSpec>?) {
        this.excludeSpecs = excludeSpecs
    }

    override fun getRequiredAttributes(): MutableMap<Attribute<Any>, MutableSet<Any>>? {
        return requiredAttributes
    }

    fun setRequiredAttributes(requiredAttributes: MutableMap<Attribute<Any>, MutableSet<Any>>?) {
        this.requiredAttributes = requiredAttributes
    }

    private class ContentSpec(
        private val matcherKind: MatcherKind,
        private val group: String,
        private val module: String?,
        private val version: String?,
        private val versionSelectorScheme: VersionSelectorScheme,
        private val versionSelectors: ConcurrentHashMap<String, VersionSelector>,
        private val inclusive: Boolean
    ) {
        private val hashCode: Int

        init {
            this.hashCode = Objects.hashCode(matcherKind, group, module, version, inclusive)
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ContentSpec
            return matcherKind == that.matcherKind && hashCode == that.hashCode &&
                    Objects.equal(group, that.group) &&
                    Objects.equal(module, that.module) &&
                    Objects.equal(version, that.version) && inclusive == that.inclusive
        }

        override fun hashCode(): Int {
            return hashCode
        }

        fun toMatcher(): SpecMatcher {
            when (matcherKind) {
                MatcherKind.SIMPLE, MatcherKind.SUB_GROUP -> return SimpleSpecMatcher(
                    group, module, version, versionSelectorScheme, versionSelectors, inclusive, matcherKind == MatcherKind.SUB_GROUP
                )

                MatcherKind.REGEX -> return PatternSpecMatcher(group, module, version, inclusive)
                else -> throw AssertionError("Unknown matcher kind: " + matcherKind)
            }
        }
    }

    private interface SpecMatcher {
        fun matches(id: ModuleIdentifier): Boolean

        fun matches(id: ModuleComponentIdentifier): Boolean
    }

    private class SimpleSpecMatcher(
        private val group: String, private val module: String?, private val version: String?, versionSelectorScheme: VersionSelectorScheme,
        versionSelectors: ConcurrentHashMap<String, VersionSelector>, private val inclusive: Boolean, private val includeSubGroups: Boolean
    ) : SpecMatcher {
        private val versionSelector: VersionSelector

        init {
            this.versionSelector = getVersionSelector(versionSelectors, versionSelectorScheme, version)!!
        }

        fun groupMatches(checkTarget: String): Boolean {
            if (!checkTarget.startsWith(group)) {
                return false
            }
            // Check if the group is simply equal
            if (checkTarget.length == group.length) {
                return true
            }
            // Check if the group is a subgroup
            return includeSubGroups && checkTarget.get(group.length) == '.'
        }

        override fun matches(id: ModuleIdentifier): Boolean {
            return groupMatches(id.getGroup())
                    && (module == null || module == id.getName())
                    && (inclusive || version == null)
        }

        override fun matches(id: ModuleComponentIdentifier): Boolean {
            return groupMatches(id.getGroup())
                    && (module == null || module == id.getModule())
                    && (version == null || version == id.getVersion() || versionSelector.accept(id.getVersion()))
        }

        fun getVersionSelector(versionSelectors: ConcurrentHashMap<String, VersionSelector>, versionSelectorScheme: VersionSelectorScheme, version: String?): VersionSelector? {
            return if (version != null) versionSelectors.computeIfAbsent(version) { s: String? -> versionSelectorScheme.parseSelector(version) } else null
        }
    }

    private class PatternSpecMatcher(group: String, module: String?, version: String?, private val inclusive: Boolean) : SpecMatcher {
        private val groupPattern: Pattern
        private val modulePattern: Pattern
        private val versionPattern: Pattern

        init {
            this.groupPattern = Pattern.compile(group)
            this.modulePattern = (if (module == null) null else java.util.regex.Pattern.compile(module))!!
            this.versionPattern = (if (version == null) null else java.util.regex.Pattern.compile(version))!!
        }

        override fun matches(id: ModuleIdentifier): Boolean {
            return groupPattern.matcher(id.getGroup()).matches()
                    && (modulePattern == null || modulePattern.matcher(id.getName()).matches())
                    && (inclusive || versionPattern == null)
        }

        override fun matches(id: ModuleComponentIdentifier): Boolean {
            return groupPattern.matcher(id.getGroup()).matches()
                    && (modulePattern == null || modulePattern.matcher(id.getModule()).matches())
                    && (versionPattern == null || versionPattern.matcher(id.getVersion()).matches())
        }
    }

    private class RepositoryFilterAction(private val includeMatchers: ImmutableList<SpecMatcher>?, private val excludeMatchers: ImmutableList<SpecMatcher>?) : Action<ArtifactResolutionDetails> {
        override fun execute(details: ArtifactResolutionDetails) {
            if (includeMatchers != null && !anyMatch(includeMatchers, details)) {
                details.notFound()
                return
            }
            if (excludeMatchers != null && anyMatch(excludeMatchers, details)) {
                details.notFound()
                return
            }
        }

        fun anyMatch(matchers: ImmutableList<SpecMatcher>, details: ArtifactResolutionDetails): Boolean {
            for (matcher in matchers) {
                val matches: Boolean
                if (details.isVersionListing()) {
                    matches = matcher.matches(details.getModuleId())
                } else {
                    matches = matcher.matches(details.getComponentId()!!)
                }
                if (matches) {
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private fun createSpecMatchers(specs: MutableSet<ContentSpec>?): ImmutableList<SpecMatcher>? {
            var matchers: ImmutableList<SpecMatcher>? = null
            if (specs != null) {
                val builder = ImmutableList.builderWithExpectedSize<SpecMatcher>(specs.size)
                for (spec in specs) {
                    builder.add(spec.toMatcher())
                }
                matchers = builder.build()
            }
            return matchers
        }

        private fun checkNotNull(value: String?, message: String) {
            requireNotNull(value) { message }
        }
    }
}

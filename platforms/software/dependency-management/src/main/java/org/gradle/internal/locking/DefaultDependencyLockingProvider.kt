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
package org.gradle.internal.locking

import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.Describable
import org.gradle.api.artifacts.ArtifactSelectionDetails
import org.gradle.api.artifacts.DependencyArtifactSelector
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.internal.artifacts.dsl.dependencies.LockEntryFilter
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.resource.local.FileResourceListener
import java.util.Collections

class DefaultDependencyLockingProvider(
    fileResolver: FileResolver,
    startParameter: StartParameter,
    private val context: DomainObjectContext,
    private val dependencySubstitutionRules: DependencySubstitutionRules,
    propertyFactory: PropertyFactory,
    filePropertyFactory: FilePropertyFactory,
    listener: FileResourceListener
) : DependencyLockingProvider {
    private val converter = DependencyLockingNotationConverter()
    private val lockFileReaderWriter: LockFileReaderWriter
    private val writeLocks: Boolean
    private val partialUpdate: Boolean
    private val updateLockEntryFilter: LockEntryFilter
    private val lockMode: Property<LockMode>
    private val lockFile: RegularFileProperty
    private val ignoredDependencies: ListProperty<String>
    private var uniqueLockStateLoaded = false
    private var allLockState: MutableMap<String, MutableList<String>>? = null
    private var compoundLockEntryFilter: LockEntryFilter? = null
        get() {
            if (field == null) {
                field = LockEntryFilterFactory.combine(this.ignoredEntryFilter!!, updateLockEntryFilter)
            }
            return field
        }
    private var ignoredEntryFilter: LockEntryFilter? = null
        get() {
            if (field == null) {
                field = LockEntryFilterFactory.forParameter(ignoredDependencies.getOrElse(mutableListOf<String>()), "Ignored dependencies", false)
            }
            return field
        }

    init {
        this.writeLocks = startParameter.isWriteDependencyLocks()
        if (writeLocks) {
            LOGGER.debug("Write locks is enabled")
        }
        val lockedDependenciesToUpdate = startParameter.lockedDependenciesToUpdate
        partialUpdate = !lockedDependenciesToUpdate.isEmpty()
        updateLockEntryFilter = LockEntryFilterFactory.forParameter(lockedDependenciesToUpdate, "Update lock", true)
        lockMode = propertyFactory.property<LockMode>(LockMode::class.java)
        lockMode.convention(LockMode.DEFAULT)
        lockFile = filePropertyFactory.newFileProperty()
        ignoredDependencies = propertyFactory.listProperty<String>(String::class.java)
        this.lockFileReaderWriter = LockFileReaderWriter(fileResolver, context, lockFile, listener)
    }

    override fun loadLockState(lockId: String, lockOwner: DisplayName): DependencyLockingState {
        recordUsage()
        loadLockState()
        if (!writeLocks || partialUpdate) {
            val lockedModules = findLockedModules(lockId)
            if (lockedModules == null && lockMode.get() == LockMode.STRICT) {
                throw MissingLockStateException(lockOwner)
            }
            if (lockedModules != null) {
                val results: MutableSet<ModuleComponentIdentifier> = Sets.newHashSetWithExpectedSize<ModuleComponentIdentifier>(lockedModules.size)
                for (module in lockedModules) {
                    val lockedIdentifier = parseLockNotation(lockOwner, module)
                    if (!this.compoundLockEntryFilter!!.isSatisfiedBy(lockedIdentifier) && !isSubstitutedInComposite(lockedIdentifier)) {
                        results.add(lockedIdentifier)
                    }
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded state for lock with ID '{}', state is: {}", lockId, lockedModules)
                } else {
                    LOGGER.info("Loaded state for lock with ID '{}'", lockId)
                }
                val strictlyValidate = !partialUpdate && lockMode.get() != LockMode.LENIENT
                return DefaultDependencyLockingState(strictlyValidate, results, this.ignoredEntryFilter!!)
            }
        }
        return DefaultDependencyLockingState.Companion.EMPTY_LOCK_CONSTRAINT
    }

    private fun findLockedModules(lockId: String): MutableList<String>? {
        var result = allLockState!!.get(lockId)
        if (result == null) {
            result = lockFileReaderWriter.readLockFile(lockId)
        }
        return result
    }

    @Synchronized
    private fun loadLockState() {
        if (!uniqueLockStateLoaded) {
            try {
                allLockState = lockFileReaderWriter.readUniqueLockFile()
                uniqueLockStateLoaded = true
            } catch (e: IllegalStateException) {
                throw InvalidLockFileException(context.getDisplayName(), e, LockFileReaderWriter.Companion.FORMATTING_DOC_LINK)
            }
        }
    }

    private fun recordUsage() {
        lockMode.finalizeValue()
        lockFile.finalizeValue()
        ignoredDependencies.finalizeValue()
    }

    private fun isSubstitutedInComposite(lockedIdentifier: ModuleComponentIdentifier): Boolean {
        if (dependencySubstitutionRules.rulesMayAddProjectDependency()) {
            val lockingDependencySubstitution = LockingDependencySubstitution(toComponentSelector(lockedIdentifier))
            dependencySubstitutionRules.ruleAction.execute(lockingDependencySubstitution)
            return lockingDependencySubstitution.didSubstitute()
        }
        return false
    }

    private fun parseLockNotation(lockOwner: Describable, module: String): ModuleComponentIdentifier {
        val lockedIdentifier: ModuleComponentIdentifier
        try {
            lockedIdentifier = converter.convertFromLockNotation(module)
        } catch (e: IllegalArgumentException) {
            throw InvalidLockFileException(lockOwner.getDisplayName(), e, LockFileReaderWriter.Companion.FORMATTING_DOC_LINK)
        }
        return lockedIdentifier
    }

    override fun persistResolvedDependencies(
        lockId: String,
        lockOwner: DisplayName,
        resolvedModules: MutableSet<ModuleComponentIdentifier>,
        changingResolvedModules: MutableSet<ModuleComponentIdentifier>
    ) {
        if (writeLocks) {
            val modulesOrdered = getModulesOrdered(resolvedModules)
            val changingModulesOrdered = getModulesOrdered(changingResolvedModules)
            if (!changingModulesOrdered.isEmpty()) {
                LOGGER.warn(
                    "Dependency lock state for {} contains changing modules: {}. This means that dependencies content may still change over time. {}",
                    lockOwner, changingModulesOrdered, DOC_REG.getDocumentationRecommendationFor("details", "dependency_locking")
                )
            }
            allLockState!!.put(lockId, modulesOrdered)
        }
    }

    override fun buildFinished() {
        if (uniqueLockStateLoaded && lockFileReaderWriter.canWrite()) {
            lockFileReaderWriter.writeUniqueLockfile(allLockState!!)
            LOGGER.lifecycle("Persisted dependency lock state for {}", context.getDisplayName())
        }
    }

    private fun getModulesOrdered(resolvedComponents: MutableCollection<ModuleComponentIdentifier>): MutableList<String> {
        val modules: MutableList<String> = ArrayList<String>(resolvedComponents.size)
        for (identifier in resolvedComponents) {
            if (!this.ignoredEntryFilter!!.isSatisfiedBy(identifier)) {
                modules.add(converter.convertToLockNotation(identifier))
            }
        }
        Collections.sort<String>(modules)
        return modules
    }

    override fun getLockMode(): Property<LockMode> {
        return lockMode
    }

    override fun getLockFile(): RegularFileProperty {
        return lockFile
    }

    override fun getIgnoredDependencies(): ListProperty<String> {
        return ignoredDependencies
    }

    override fun confirmNotLocked(lockId: String) {
        if (writeLocks) {
            loadLockState()
            allLockState!!.remove(lockId)
        }
    }

    private class LockingDependencySubstitution(private val selector: ComponentSelector) : DependencySubstitutionInternal {
        private var didSubstitute = false

        override fun getRequested(): ComponentSelector {
            return selector
        }

        override fun useTarget(notation: Any) {
            didSubstitute = true
        }

        override fun useTarget(notation: Any, reason: String) {
            didSubstitute = true
        }

        override fun artifactSelection(action: Action<in ArtifactSelectionDetails>) {
            // No need to execute the artifact selection action.
            // We only care if the dependency selector was substituted.
        }

        fun didSubstitute(): Boolean {
            return didSubstitute
        }

        override fun useTarget(notation: Any, ruleDescriptor: ComponentSelectionDescriptor) {
            didSubstitute = true
        }

        override fun getConfiguredTargetSelector(): ComponentSelector? {
            return selector
        }

        override fun getRuleDescriptors(): ImmutableList<ComponentSelectionDescriptorInternal>? {
            throw UnsupportedOperationException("Should not be called")
        }

        override fun getConfiguredArtifactSelectors(): ImmutableList<DependencyArtifactSelector>? {
            throw UnsupportedOperationException("Should not be called")
        }
    }

    companion object {
        private val LOGGER: Logger = getLogger(DefaultDependencyLockingProvider::class.java)!!
        private val DOC_REG = DocumentationRegistry()

        private fun toComponentSelector(lockIdentifier: ModuleComponentIdentifier): ComponentSelector {
            val lockedVersion = lockIdentifier.getVersion()
            val versionConstraint: VersionConstraint = DefaultMutableVersionConstraint.withVersion(lockedVersion)
            return DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(lockIdentifier.getGroup(), lockIdentifier.getModule()), versionConstraint)
        }
    }
}

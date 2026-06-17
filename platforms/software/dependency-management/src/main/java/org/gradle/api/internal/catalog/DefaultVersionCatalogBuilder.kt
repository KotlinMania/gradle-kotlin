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
package org.gradle.api.internal.catalog

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Interner
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.catalog.parser.DependenciesModelHelper
import org.gradle.api.internal.catalog.parser.StrictVersionParser
import org.gradle.api.internal.catalog.parser.TomlCatalogFileParser
import org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemSpecInternal
import org.gradle.api.problems.internal.ProblemSpecInternal.contextualLabel
import org.gradle.api.problems.internal.ProblemSpecInternal.documentedAt
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.provider.Property
import org.gradle.internal.FileUtils
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.lazy.Lazy.Companion.unsafe
import org.gradle.internal.management.VersionCatalogBuilderInternal
import org.gradle.util.internal.TextUtil
import java.io.File
import java.io.IOException
import java.util.function.Supplier
import javax.inject.Inject

abstract class DefaultVersionCatalogBuilder @Inject constructor(
    private val name: String,
    private val strings: Interner<String>,
    private val versionConstraintInterner: Interner<ImmutableVersionConstraint>,
    private val objects: ObjectFactory,
    private val dependencyResolutionServicesSupplier: Supplier<DependencyResolutionServices>
) : VersionCatalogBuilderInternal {
    private enum class AliasType {
        LIBRARY,
        PLUGIN,
        BUNDLE,
        VERSION,

        // To be removed.
        ALIAS;

        override fun toString(): String {
            return name.lowercase()
        }
    }

    private val versionConstraints: MutableMap<String, VersionModel> = LinkedHashMap<String, VersionModel>()
    private val libraries: MutableMap<String, Supplier<DependencyModel>> = LinkedHashMap<String, Supplier<DependencyModel>>()

    /**
     * Aliases that are being constructed, used to detect unfinished builders.
     */
    private val aliasesInProgress: MutableSet<String> = LinkedHashSet<String>()
    private val plugins: MutableMap<String, Supplier<PluginModel>> = LinkedHashMap<String, Supplier<PluginModel>>()
    private val bundles: MutableMap<String, BundleModel> = LinkedHashMap<String, BundleModel>()
    private val model = unsafe().of<DefaultVersionCatalog?>(Supplier { this.doBuild() })
    private var importedCatalog: Import? = null
    private val strictVersionParser: StrictVersionParser

    private val description: Property<String>

    private var currentContext: String? = null

    init {
        this.strictVersionParser = StrictVersionParser(strings, this.problemsService)
        this.description = objects.property<String>(String::class.java).convention("A catalog of dependencies accessible via the {@code " + name + "} extension.")
    }

    @get:Inject
    protected abstract val problemsService: ProblemsInternal?

    override fun getLibrariesExtensionName(): String {
        return name
    }

    override fun getDescription(): Property<String> {
        return description
    }

    override fun build(): DefaultVersionCatalog {
        return model.get()!!
    }

    override fun withContext(context: String, action: Runnable) {
        val oldContext = currentContext
        currentContext = intern(context)
        try {
            action.run()
        } finally {
            currentContext = oldContext
        }
    }

    private fun doBuild(): DefaultVersionCatalog {
        maybeImportCatalogs()
        if (!aliasesInProgress.isEmpty()) {
            val alias = aliasesInProgress.iterator().next()
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(builder, this.problemInVersionCatalog + "dependency alias builder '" + alias + "' was not finished", VersionCatalogProblemId.ALIAS_NOT_FINISHED)
                    .details("A version was not set or explicitly declared as not wanted")!!
                    .solution("Call `.version()` to give the alias a version")!!
                    .solution("Call `.withoutVersion()` to explicitly declare that the alias should not have a version")
            }))
        }
        for (entry in bundles.entries) {
            val bundleName = entry.key
            val aliases = entry.value.getComponents()
            for (alias in aliases) {
                if (!libraries.containsKey(alias)) {
                    throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                        org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.Companion.configureVersionCatalogError(
                            builder, this.problemInVersionCatalog + "a bundle with name '" + bundleName + "' declares a dependency on '" + alias +
                                    "' which doesn't exist", org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNDEFINED_ALIAS_REFERENCE
                        )
                            .details("Bundles can only contain references to existing library aliases.")!!
                            .solution("Make sure that the library alias '" + alias + "' is declared")!!
                            .solution("Remove '" + alias + "' from bundle '" + bundleName + "'.")
                    }))
                }
            }
        }
        val realizedLibs = ImmutableMap.builderWithExpectedSize<String, DependencyModel>(libraries.size)
        for (entry in libraries.entries) {
            realizedLibs.put(entry.key, entry.value.get())
        }
        val realizedPlugins = ImmutableMap.builderWithExpectedSize<String, PluginModel>(plugins.size)
        for (entry in plugins.entries) {
            realizedPlugins.put(entry.key, entry.value.get())
        }
        return DefaultVersionCatalog(
            name,
            description.getOrElse(""),
            realizedLibs.build(),
            ImmutableMap.copyOf<String, BundleModel>(bundles),
            ImmutableMap.copyOf<String, VersionModel>(versionConstraints),
            realizedPlugins.build()
        )
    }

    private val problemInVersionCatalog: String
        get() = DefaultCatalogProblemBuilder.getProblemInVersionCatalog(name) + ", "

    private fun maybeImportCatalogs() {
        if (importedCatalog == null) {
            return
        }
        val drs = dependencyResolutionServicesSupplier.get()
        val cnf = createResolvableConfiguration(drs)
        addImportsToResolvableConfiguration(drs, cnf, importedCatalog!!)

        val artifacts = cnf.getIncoming().getArtifacts().getArtifacts()
        if (artifacts.size > 1) {
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(
                    builder, this.problemInVersionCatalog + StringUtils.uncapitalize(
                        VersionCatalogProblemId.TOO_MANY_IMPORT_FILES.getDisplayName()
                    ), VersionCatalogProblemId.TOO_MANY_IMPORT_FILES
                )
                    .details("The import consists of multiple files")!!
                    .solution("Only import a single file")
            }))
        }

        // We need to fall back to if-else with the Optional, as the Problems API cannot return an instance of an exception, only throw
        val maybeResolvedArtifactResult = artifacts.stream().findFirst()
        if (maybeResolvedArtifactResult.isPresent()) {
            val resolvedArtifactResult = maybeResolvedArtifactResult.get()
            val file = resolvedArtifactResult.getFile()
            withContext("catalog " + resolvedArtifactResult.getVariant().getOwner(), Runnable { importCatalogFromFile(file) })
        } else {
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(
                    builder, this.problemInVersionCatalog + StringUtils.uncapitalize(
                        VersionCatalogProblemId.NO_IMPORT_FILES.getDisplayName()
                    ), VersionCatalogProblemId.NO_IMPORT_FILES
                )
                    .details("The imported dependency doesn't resolve into any file")!!
                    .solution("Check the import statement, it should resolve into a single file")
            }))
        }
    }

    private fun addImportsToResolvableConfiguration(drs: DependencyResolutionServices, cnf: Configuration, imported: Import) {
        val notation = imported.notation
        val dependency = drs.getDependencyHandler().create(notation)
        cnf.getDependencies().add(dependency)
    }

    @Suppress("deprecation")
    private fun createResolvableConfiguration(drs: DependencyResolutionServices): Configuration {
        // The zero at the end of the configuration comes from the previous implementation;
        // Multiple files could be imported, and all members of the list were given their own configuration, postfixed by the index in the array.
        // After moving this into a single-file import, we didn't want to break the lock files generated for the configuration, so we simply kept the zero.
        val confName = "incomingCatalogFor" + StringUtils.capitalize(name) + "0"
        return (drs.getConfigurationContainer() as RoleBasedConfigurationContainerInternal).resolvableDependencyScopeLocked(confName, Action { conf: Configuration? ->
            conf!!.getResolutionStrategy().activateDependencyLocking()
            conf.attributes(Action { attrs: AttributeContainer? ->
                attrs!!.attribute<Category>(Category.CATEGORY_ATTRIBUTE, attrs.named<Category>(Category::class.java, Category.REGULAR_PLATFORM))
                attrs.attribute<Usage>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage>(Usage::class.java, Usage.VERSION_CATALOG))
            })
        })
    }

    override fun from(dependencyNotation: Any) {
        if (importedCatalog == null) {
            importedCatalog = Import(dependencyNotation)
        } else {
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(builder, this.problemInVersionCatalog + "you can only call the 'from' method a single time", VersionCatalogProblemId.TOO_MANY_IMPORT_INVOCATION)
                    .details("The method was called more than once")!!
                    .solution("Remove further usages of the method call")
            }))
        }
    }

    private fun importCatalogFromFile(modelFile: File) {
        if (!FileUtils.hasExtensionIgnoresCase(modelFile.getName(), "toml")) {
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(builder, this.problemInVersionCatalog + "File " + modelFile.getName() + " isn't a supported", VersionCatalogProblemId.UNSUPPORTED_FILE_FORMAT)
                    .details("Only .toml files are allowed when importing catalogs")!!
                    .solution("Use a TOML file instead, with the .toml extension")
            }))
        }
        if (!modelFile.exists()) {
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(
                    builder, this.problemInVersionCatalog + StringUtils.uncapitalize(
                        VersionCatalogProblemId.CATALOG_FILE_DOES_NOT_EXIST.getDisplayName()
                    ), VersionCatalogProblemId.CATALOG_FILE_DOES_NOT_EXIST
                )
                    .details("File '" + modelFile + "' doesn't exist")!!
                    .solution("Make sure that the catalog file '" + modelFile.getName() + "' exists before importing it")
            }))
        }

        Instrumented.fileOpened(modelFile, javaClass.getName())
        try {
            TomlCatalogFileParser.parse(modelFile.toPath(), this, Supplier { this.problemsService })
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    override fun version(alias: String, versionSpec: Action<in MutableVersionConstraint>): String {
        var alias = alias
        validateAlias(AliasType.VERSION, alias)
        alias = intern(AliasNormalizer.normalize(alias))!!
        if (versionConstraints.containsKey(alias)) {
            // For versions, in order to allow overriding whatever is declared by
            // a platform, we want to silence overrides
            return alias
        }
        val versionBuilder: MutableVersionConstraint = DefaultMutableVersionConstraint("")
        versionSpec.execute(versionBuilder)
        val version = versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder))
        versionConstraints.put(alias, VersionModel(version, currentContext))
        return alias
    }

    override fun version(alias: String, version: String): String {
        val richVersion = strictVersionParser.parse(version)
        version(alias, Action { vc: MutableVersionConstraint ->
            configureRequiredRichVersion(vc, richVersion)
        })
        return alias
    }

    override fun library(alias: String, group: String, artifact: String): VersionCatalogBuilder.LibraryAliasBuilder {
        val normalizedAlias = normalizeAndValidateAlias(AliasType.LIBRARY, alias)
        return objects.newInstance<DefaultLibraryAliasBuilder>(DefaultLibraryAliasBuilder::class.java, this@DefaultVersionCatalogBuilder, normalizedAlias, group, artifact)
    }

    override fun library(alias: String, groupArtifactVersion: String) {
        val normalizedAlias = normalizeAndValidateAlias(AliasType.LIBRARY, alias)

        val coordinates = groupArtifactVersion.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (coordinates.size == 3) {
            objects.newInstance<DefaultLibraryAliasBuilder>(DefaultLibraryAliasBuilder::class.java, this@DefaultVersionCatalogBuilder, normalizedAlias, coordinates[0], coordinates[1])
                .version(coordinates[2])
        } else {
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(
                    builder,
                    this.problemInVersionCatalog + "on alias '" + alias + "' notation '" + groupArtifactVersion + "' is not a valid dependency notation",
                    VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION
                )
                    .details("The 'to(String)' method only supports 'group:artifact:version' coordinates")!!
                    .solution("Make sure that the coordinates consist of 3 parts separated by colons, eg: my.group:artifact:1.2")!!
                    .solution("Use the to(group, name) method instead")
            }))
        }
    }

    override fun plugin(alias: String, id: String): VersionCatalogBuilder.PluginAliasBuilder {
        val normalizedAlias = normalizeAndValidateAlias(AliasType.PLUGIN, alias)
        return objects.newInstance<DefaultPluginAliasBuilder>(DefaultPluginAliasBuilder::class.java, this@DefaultVersionCatalogBuilder, normalizedAlias, id)
    }

    private fun normalizeAndValidateAlias(type: AliasType, alias: String): String {
        validateAlias(type, alias)
        val normalizedAlias: String = AliasNormalizer.normalize(alias)!!
        validateNormalizedAlias(type, alias, normalizedAlias)
        return normalizedAlias
    }

    private fun validateNormalizedAlias(type: AliasType, alias: String, normalizedAlias: String) {
        if (!aliasesInProgress.add(normalizedAlias)) {
            LOGGER.warn("Duplicate alias builder registered for {}", normalizedAlias)
        }

        if (type == AliasType.LIBRARY) {
            for (prefix in FORBIDDEN_LIBRARY_ALIAS_PREFIX) {
                if (normalizedAlias == prefix || normalizedAlias.startsWith(prefix + ".")) {
                    throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                        configureVersionCatalogError(builder, this.problemInVersionCatalog + "alias '" + alias + "' is a reserved alias", VersionCatalogProblemId.RESERVED_ALIAS_NAME)
                            .details("Prefix for dependency shouldn't be equal to '" + prefix + "'")!!
                            .solution("Use a different alias which prefix is not equal to " + quotedOxfordListOf(FORBIDDEN_LIBRARY_ALIAS_PREFIX, "or"))
                    }))
                }
            }
        }
        if (RESERVED_ALIAS_NAMES.contains(normalizedAlias)) {
            throw throwAliasCatalogException(alias, RESERVED_ALIAS_NAMES)
        }

        for (name in normalizedAlias.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (RESERVED_JAVA_NAMES.contains(name)) {
                throw throwAliasCatalogException(alias, RESERVED_JAVA_NAMES)
            }
        }
    }

    private fun throwAliasCatalogException(alias: String, reservedNames: MutableCollection<String>): RuntimeException? {
        throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
            configureVersionCatalogError(builder, this.problemInVersionCatalog + "alias '" + alias + "' is a reserved alias", VersionCatalogProblemId.RESERVED_ALIAS_NAME)
                .details("Alias '" + alias + "' is a reserved name in Gradle which prevents generation of accessors.")!!
                .solution("Use a different alias which doesn't contain " + getExcludedNames(reservedNames) + ".")
        }))
    }

    private fun validateAlias(type: AliasType, value: String) {
        if (!DependenciesModelHelper.ALIAS_PATTERN.matcher(value).matches()) {
            throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                configureVersionCatalogError(builder, this.problemInVersionCatalog + "invalid " + type + " alias '" + value + "'", VersionCatalogProblemId.INVALID_ALIAS_NOTATION)
                    .details(type.toString() + " aliases must match the following regular expression: " + org.gradle.api.internal.catalog.parser.DependenciesModelHelper.ALIAS_REGEX)!!
                    .solution("Make sure the alias matches the " + DependenciesModelHelper.ALIAS_REGEX + " regular expression")
            }))
        }
    }

    override fun bundle(alias: String, aliases: MutableList<String>) {
        validateAlias(AliasType.BUNDLE, alias)
        val components = aliases.stream()
            .map<String?> { obj: String? -> AliasNormalizer.normalize() }
            .map<String?> { value: String? -> this.intern(value) }
            .collect(ImmutableList.toImmutableList<String>())
        val previous = bundles.put(AliasNormalizer.normalize(intern(alias))!!, BundleModel(components, currentContext))
        if (previous != null) {
            LOGGER.warn("Duplicate entry for bundle '{}': {} is replaced with {}", alias, previous.getComponents(), components)
        }
    }

    private fun intern(value: String?): String? {
        if (value == null) {
            return null
        }
        return strings.intern(value)
    }

    fun containsLibraryAlias(name: String): Boolean {
        return libraries.containsKey(name)
    }

    override fun getName(): String {
        return name
    }

    private inner class VersionReferencingDependencyModel(private val group: String, private val name: String, private val versionRef: String) : Supplier<DependencyModel> {
        private val context: String?

        init {
            this.context = currentContext
        }

        override fun get(): DependencyModel {
            val model: VersionModel = versionConstraints.get(versionRef)!!
            if (model == null) {
                throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                    val configurator: ProblemSpec? = org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.Companion.configureVersionCatalogError(
                        builder,
                        this.problemInVersionCatalog + "version reference '" + versionRef + "' doesn't exist",
                        org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE
                    )
                        .details("Dependency '" + group + ":" + name + "' references version '" + versionRef + "' which doesn't exist")!!
                        .solution("Declare '" + versionRef + "' in the catalog")
                    if (!versionConstraints.isEmpty()) {
                        configurator!!.solution("Use one of the following existing versions: " + quotedOxfordListOf(versionConstraints.keys, "or"))
                    }
                }))
            } else {
                return DependencyModel(group, name, versionRef, model.getVersion(), context)
            }
        }
    }

    private inner class VersionReferencingPluginModel(private val id: String, private val versionRef: String) : Supplier<PluginModel> {
        private val context: String?

        init {
            this.context = currentContext
        }

        override fun get(): PluginModel {
            val model: VersionModel = versionConstraints.get(versionRef)!!
            if (model == null) {
                throw throwVersionCatalogProblemException(this.problemsService, this.problemsService.internalReporter.internalCreate({ builder ->
                    val configurator: ProblemSpec? = org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.Companion.configureVersionCatalogError(
                        builder,
                        this.problemInVersionCatalog + "version reference '" + versionRef + "' doesn't exist",
                        org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE
                    )
                        .details("Plugin '" + id + "' references version '" + versionRef + "' which doesn't exist")!!
                        .solution("Declare '" + versionRef + "' in the catalog")
                    if (!versionConstraints.isEmpty()) {
                        configurator!!.solution("Use one of the following existing versions: " + quotedOxfordListOf(versionConstraints.keys, "or"))
                    }
                }))
            } else {
                return PluginModel(id, versionRef, model.getVersion(), context)
            }
        }
    }

    // static public for injection!
    class DefaultLibraryAliasBuilder @Inject constructor(private val owner: DefaultVersionCatalogBuilder, private val alias: String, private val group: String, private val name: String) :
        VersionCatalogBuilder.LibraryAliasBuilder {
        override fun version(versionSpec: Action<in MutableVersionConstraint>) {
            val versionBuilder: MutableVersionConstraint = DefaultMutableVersionConstraint("")
            versionSpec.execute(versionBuilder)
            owner.aliasesInProgress.remove(alias)
            val version = owner.versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder))
            val model = DependencyModel(owner.intern(group)!!, owner.intern(name)!!, null, version, owner.currentContext)
            val previous = owner.libraries.put(owner.intern(alias)!!, Supplier { model })
            if (previous != null) {
                LOGGER.warn("Duplicate entry for alias '{}': {} is replaced with {}", alias, previous.get(), model)
            }
        }

        override fun version(version: String) {
            val richVersion = owner.strictVersionParser.parse(version)
            version(Action { vc: MutableVersionConstraint ->
                configureRequiredRichVersion(vc, richVersion)
            })
        }

        override fun versionRef(versionRef: String) {
            owner.aliasesInProgress.remove(alias)
            owner.createAliasWithVersionRef(alias, group, name, versionRef)
        }

        override fun withoutVersion() {
            version("")
        }
    }

    // static public for injection!
    class DefaultPluginAliasBuilder @Inject constructor(private val owner: DefaultVersionCatalogBuilder, private val alias: String, private val id: String) : VersionCatalogBuilder.PluginAliasBuilder {
        override fun version(versionSpec: Action<in MutableVersionConstraint>) {
            val versionBuilder: MutableVersionConstraint = DefaultMutableVersionConstraint("")
            versionSpec.execute(versionBuilder)
            owner.aliasesInProgress.remove(alias)
            val version = owner.versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder))
            val model = PluginModel(owner.intern(id)!!, null, version, owner.currentContext)
            val previous = owner.plugins.put(owner.intern(alias)!!, Supplier { model })
            if (previous != null) {
                LOGGER.warn("Duplicate entry for plugin '{}': {} is replaced with {}", alias, previous.get(), model)
            }
        }

        override fun version(version: String) {
            val richVersion = owner.strictVersionParser.parse(version)
            version(Action { vc: MutableVersionConstraint ->
                configureRequiredRichVersion(vc, richVersion)
            })
        }

        override fun versionRef(versionRef: String) {
            owner.aliasesInProgress.remove(alias)
            owner.createPluginAliasWithVersionRef(alias, id, versionRef)
        }
    }

    private fun createAliasWithVersionRef(alias: String, group: String, name: String, versionRef: String) {
        val previous = libraries.put(intern(AliasNormalizer.normalize(alias))!!, DefaultVersionCatalogBuilder.VersionReferencingDependencyModel(group, name, AliasNormalizer.normalize(versionRef)))
        if (previous != null) {
            LOGGER.warn("Duplicate entry for alias '{}': {} is replaced with {}", alias, previous.get(), model)
        }
    }

    private fun createPluginAliasWithVersionRef(alias: String, id: String, versionRef: String) {
        val previous = plugins.put(intern(AliasNormalizer.normalize(alias))!!, DefaultVersionCatalogBuilder.VersionReferencingPluginModel(id, AliasNormalizer.normalize(versionRef)))
        if (previous != null) {
            LOGGER.warn("Duplicate entry for plugin '{}': {} is replaced with {}", alias, previous.get(), model)
        }
    }

    private class Import(private val notation: Any)
    companion object {
        private val LOGGER: Logger = getLogger(DefaultVersionCatalogBuilder::class.java)!!
        val FORBIDDEN_LIBRARY_ALIAS_PREFIX: MutableList<String> = ImmutableList.of<String>("bundles", "versions", "plugins")
        val RESERVED_ALIAS_NAMES: MutableSet<String> = ImmutableSet.of<String>("extensions", "convention")

        /**
         * names that are forbidden in generated accessors because we can't override getClass()
         */
        val RESERVED_JAVA_NAMES: MutableSet<String> = ImmutableSet.of<String>("class")

        private fun configureVersionCatalogError(builder: ProblemSpecInternal, message: String, catalogProblemId: VersionCatalogProblemId): ProblemSpecInternal {
            return builder.id
            (TextUtil.screamingSnakeToKebabCase(catalogProblemId.name), catalogProblemId.getDisplayName(), org.gradle.api.problems.internal.GradleCoreProblemGroup.versionCatalog())
            .contextualLabel(message)
                .documentedAt(userManual(DefaultCatalogProblemBuilder.VERSION_CATALOG_PROBLEMS, catalogProblemId.name.lowercase()))
        }

        private fun throwVersionCatalogProblemException(problemsService: ProblemsInternal, problem: ProblemInternal): RuntimeException? {
            throw problemsService.reporter!!.throwing(InvalidUserDataException(), problem)
        }

        fun getExcludedNames(reservedNames: MutableCollection<String>): String {
            val namesOrName: String = quotedOxfordListOf(reservedNames, "or")
            if (reservedNames.size == 1) {
                return namesOrName
            }
            return "any of " + namesOrName
        }

        private fun configureRequiredRichVersion(vc: MutableVersionConstraint, richVersion: StrictVersionParser.RichVersion) {
            if (richVersion.require != null) {
                vc.require(richVersion.require)
            }
            if (richVersion.prefer != null) {
                vc.prefer(richVersion.prefer)
            }
            if (richVersion.strictly != null) {
                vc.strictly(richVersion.strictly)
            }
        }
    }
}

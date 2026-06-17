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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.apache.commons.lang3.StringUtils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.problems.Problems
import org.gradle.initialization.DependenciesAccessors
import org.gradle.initialization.ProjectDescriptorInternal
import org.gradle.initialization.ProjectDescriptorRegistry
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.ExecutionContext
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.Identity
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.InputVisitor
import org.gradle.internal.execution.OutputVisitor
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkOutput
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.history.OverlappingOutputs
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.Hashing
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.management.VersionCatalogBuilderInternal
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.util.internal.IncubationLogger.incubatingFeatureUsed
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringWriter
import java.util.Arrays
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors
import javax.inject.Inject

class DefaultDependenciesAccessors @Inject constructor(
    registry: ClassPathRegistry,
    private val workspace: DependenciesAccessorsWorkspaceProvider,
    private val projectDependencyFactory: DefaultProjectDependencyFactory,
    private val featureFlags: FeatureFlags,
    private val engine: ExecutionEngine,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter,
    private val attributesFactory: AttributesFactory,
    private val capabilityNotationParser: CapabilityNotationParser,
    private val problemsService: Problems
) : DependenciesAccessors {
    private val classPath: ClassPath
    private val models: MutableList<DefaultVersionCatalog> = ArrayList<DefaultVersionCatalog>()
    private val factories: MutableMap<String, Class<out ExternalModuleDependencyFactory>> = HashMap<String, Class<out ExternalModuleDependencyFactory>>()

    private var classLoaderScope: ClassLoaderScope? = null
    private var generatedProjectFactory: Class<out TypeSafeProjectDependencyFactory>? = null
    private var sources = DefaultClassPath.of()
    private var classes = DefaultClassPath.of()

    init {
        this.classPath = registry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER")
    }

    override fun generateAccessors(builders: MutableList<VersionCatalogBuilder>, classLoaderScope: ClassLoaderScope, settings: Settings) {
        try {
            this.classLoaderScope = classLoaderScope
            this.models.clear() // this is used in tests only, shouldn't happen in real context
            for (builder in builders) {
                val model = (builder as VersionCatalogBuilderInternal).build()
                models.add(model!!)
            }
            if (models.stream().anyMatch { obj: DefaultVersionCatalog? -> obj!!.isNotEmpty() }) {
                for (model in models) {
                    if (model.isNotEmpty()) {
                        writeDependenciesAccessors(model)
                    }
                }
            }
            if (featureFlags.isEnabled(FeaturePreviews.Feature.TYPESAFE_PROJECT_ACCESSORS)) {
                incubatingFeatureUsed("Type-safe project accessors")
                writeProjectAccessors((settings as SettingsInternal).getProjectRegistry())
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun writeDependenciesAccessors(model: DefaultVersionCatalog) {
        executeWork(DefaultDependenciesAccessors.DependencyAccessorUnitOfWork(model))
    }

    private fun writeProjectAccessors(projectRegistry: ProjectDescriptorRegistry) {
        if (!assertCanGenerateAccessors(projectRegistry)) {
            return
        }
        warnIfRootProjectNameNotSetExplicitly(projectRegistry.getRootProject())
        executeWork(DefaultDependenciesAccessors.ProjectAccessorUnitOfWork(projectRegistry))
    }

    private fun executeWork(work: UnitOfWork) {
        val result = engine.createRequest(work).execute()
        val accessors = result.getOutputAs<GeneratedAccessors>(GeneratedAccessors::class.java).get()
        val generatedClasses = DefaultClassPath.of(accessors.classesDir)
        sources = sources.plus(DefaultClassPath.of(accessors.sourcesDir))
        classes = classes.plus(generatedClasses)
        classLoaderScope!!.export(generatedClasses)
    }

    override fun createExtensions(project: ProjectInternal) {
        val container: ExtensionContainer = project.getExtensions()
        val providerFactory = project.getProviders()
        try {
            if (models.isEmpty()) {
                addVersionCatalogsProjectExtension(container, mutableMapOf<String, VersionCatalog>())
            } else {
                val catalogs = ImmutableMap.builderWithExpectedSize<String, VersionCatalog>(models.size)
                for (model in models) {
                    if (model.isNotEmpty()) {
                        val factory = loadVersionCatalogFactoryClass(accessorClassNameSuffix(model))
                        if (factory != null) {
                            container.create(model.getName(), factory, model)
                            catalogs.put(model.getName(), VersionCatalogView(model, providerFactory, project.getObjects(), attributesFactory, capabilityNotationParser))
                        }
                    }
                }
                addVersionCatalogsProjectExtension(container, catalogs.build())
            }
        } finally {
            if (featureFlags.isEnabled(FeaturePreviews.Feature.TYPESAFE_PROJECT_ACCESSORS)) {
                val services = project.getServices()
                val drm = services.get<DependencyResolutionManagementInternal?>(DependencyResolutionManagementInternal::class.java)
                val projectFinder = services.get<ProjectFinder?>(ProjectFinder::class.java)
                createProjectsExtension(container, drm!!, projectFinder!!)
            }
        }
    }

    private fun addVersionCatalogsProjectExtension(container: ExtensionContainer, catalogs: MutableMap<String, VersionCatalog>) {
        container.create<VersionCatalogsExtension>(VersionCatalogsExtension::class.java, "versionCatalogs", DefaultVersionCatalogsExtension::class.java, catalogs)
    }

    private fun accessorClassNameSuffix(model: DefaultVersionCatalog): String {
        return StringUtils.capitalize(model.getName())
    }

    override fun createPluginsBlockFactories(objects: ObjectFactory): MutableMap<String, ExternalModuleDependencyFactory> {
        if (!models.isEmpty()) {
            val catalogs = ImmutableMap.builderWithExpectedSize<String, ExternalModuleDependencyFactory>(models.size)
            for (model in models) {
                if (model.isNotEmpty()) {
                    val factory = loadVersionCatalogFactoryClass(pluginsBlockAccessorClassNameSuffix(model))
                    if (factory != null) {
                        catalogs.put(model.getName(), objects.newInstance(factory, model))
                    }
                }
            }
            return catalogs.build()
        }
        return mutableMapOf<String, ExternalModuleDependencyFactory>()
    }

    private fun pluginsBlockAccessorClassNameSuffix(model: DefaultVersionCatalog): String {
        return accessorClassNameSuffix(model) + DependenciesAccessors.IN_PLUGINS_BLOCK_FACTORIES_SUFFIX
    }

    private fun loadVersionCatalogFactoryClass(accessorsClassnameSuffix: String): Class<out ExternalModuleDependencyFactory>? {
        val factory: Class<out ExternalModuleDependencyFactory>?
        synchronized(this) {
            factory = factories.computeIfAbsent(accessorsClassnameSuffix) { n: String? ->
                Companion.loadFactory<ExternalModuleDependencyFactory>(
                    classLoaderScope!!,
                    org.gradle.api.internal.catalog.DefaultDependenciesAccessors.Companion.ACCESSORS_PACKAGE + "." + org.gradle.api.internal.catalog.DefaultDependenciesAccessors.Companion.ACCESSORS_CLASSNAME_PREFIX + accessorsClassnameSuffix
                )!!
            }
        }
        return factory
    }

    private fun createProjectsExtension(container: ExtensionContainer, drm: DependencyResolutionManagementInternal, projectFinder: ProjectFinder) {
        if (generatedProjectFactory == null) {
            synchronized(this) {
                generatedProjectFactory = Companion.loadFactory<TypeSafeProjectDependencyFactory>(classLoaderScope!!, ROOT_PROJECT_ACCESSOR_FQCN)
            }
        }
        if (generatedProjectFactory != null) {
            val defaultProjectsExtensionName = drm.getDefaultProjectsExtensionName()
            defaultProjectsExtensionName.finalizeValue()
            container.create(defaultProjectsExtensionName.get(), generatedProjectFactory, projectDependencyFactory, projectFinder)
        }
    }

    override fun getSources(): ClassPath {
        return sources
    }

    override fun getClasses(): ClassPath {
        return classes
    }

    private abstract inner class AbstractAccessorUnitOfWork : ImmutableUnitOfWork {
        override fun identify(scalarInputs: MutableMap<String, ValueSnapshot>, fileInputs: MutableMap<String, CurrentFileCollectionFingerprint>): Identity {
            val hasher = Hashing.sha1().newHasher()
            scalarInputs.values.forEach(Consumer { s: ValueSnapshot? -> s!!.appendToHasher(hasher) })
            val identity = hasher.hash().toString()
            return Identity { identity }
        }

        override fun getWorkspaceProvider(): ImmutableWorkspaceProvider {
            return workspace
        }

        override fun getInputFingerprinter(): InputFingerprinter {
            return inputFingerprinter
        }

        protected abstract val classSources: MutableList<ClassSource>

        override fun execute(executionContext: ExecutionContext): WorkOutput {
            val workspace = executionContext.getWorkspace()
            val srcDir = File(workspace, OUT_SOURCES)
            val dstDir = File(workspace, OUT_CLASSES)
            val sources = this.classSources
            SimpleGeneratedJavaClassCompiler.compile(srcDir, dstDir, sources, classPath)
            return object : WorkOutput {
                override fun getDidWork(): WorkOutput.WorkResult {
                    return WorkOutput.WorkResult.DID_WORK
                }

                override fun getOutput(workspace: File): Any {
                    return loadAlreadyProducedOutput(workspace)
                }
            }
        }

        override fun loadAlreadyProducedOutput(workspace: File): Any {
            val srcDir = File(workspace, OUT_SOURCES)
            val dstDir = File(workspace, OUT_CLASSES)
            return GeneratedAccessors(srcDir, dstDir)
        }

        override fun visitOutputs(workspace: File, visitor: OutputVisitor) {
            visitOutputDir(visitor, workspace, OUT_SOURCES)
            visitOutputDir(visitor, workspace, OUT_CLASSES)
        }

        fun visitOutputDir(visitor: OutputVisitor, workspace: File, propertyName: String) {
            val dir = File(workspace, propertyName)
            visitor.visitOutputProperty(propertyName, TreeType.DIRECTORY, OutputVisitor.OutputFileValueSupplier.fromStatic(dir, fileCollectionFactory.fixed(dir)))
        }

        companion object {
            private const val OUT_SOURCES = "sources"
            private const val OUT_CLASSES = "classes"
        }
    }

    private inner class DependencyAccessorUnitOfWork(private val model: DefaultVersionCatalog) : AbstractAccessorUnitOfWork() {
        override fun shouldDisableCaching(detectedOverlappingOutputs: OverlappingOutputs?): Optional<CachingDisabledReason> {
            // This was a behaviour before 8.9, where we unified ExecutionEngine in https://github.com/gradle/gradle/pull/29534
            return Optional.of<CachingDisabledReason>(UnitOfWork.NOT_WORTH_CACHING)
        }

        override fun getClassSources(): MutableList<ClassSource> {
            return Arrays.asList<ClassSource>(
                DependenciesAccessorClassSource(model.getName(), model, problemsService),
                PluginsBlockDependenciesAccessorClassSource(model.getName(), model, problemsService)
            )
        }

        override fun visitImmutableInputs(visitor: InputVisitor) {
            visitor.visitInputProperty(IN_LIBRARIES, InputVisitor.ValueSupplier { model.getLibraryAliases() })
            visitor.visitInputProperty(IN_BUNDLES, InputVisitor.ValueSupplier { model.getBundleAliases() })
            visitor.visitInputProperty(IN_VERSIONS, InputVisitor.ValueSupplier { model.getVersionAliases() })
            visitor.visitInputProperty(IN_PLUGINS, InputVisitor.ValueSupplier { model.getPluginAliases() })
            visitor.visitInputProperty(IN_MODEL_NAME, InputVisitor.ValueSupplier { model.getName() })
            visitor.visitInputFileProperty(
                IN_CLASSPATH, InputBehavior.NON_INCREMENTAL,
                InputVisitor.InputFileValueSupplier(
                    classPath,
                    InputNormalizer.RUNTIME_CLASSPATH,
                    DirectorySensitivity.IGNORE_DIRECTORIES,
                    LineEndingSensitivity.DEFAULT,
                    Supplier { fileCollectionFactory.fixed(classPath.getAsFiles()) })
            )
        }

        override fun getDisplayName(): String {
            return "generation of dependency accessors for " + model.getName()
        }

        companion object {
            private const val IN_LIBRARIES = "libraries"
            private const val IN_BUNDLES = "bundles"
            private const val IN_PLUGINS = "plugins"
            private const val IN_VERSIONS = "versions"
            private const val IN_MODEL_NAME = "modelName"
            private const val IN_CLASSPATH = "classpath"
        }
    }

    private inner class ProjectAccessorUnitOfWork(private val projectRegistry: ProjectDescriptorRegistry) : AbstractAccessorUnitOfWork() {
        override fun shouldDisableCaching(detectedOverlappingOutputs: OverlappingOutputs?): Optional<CachingDisabledReason> {
            // This was a behaviour before 8.9, where we unified ExecutionEngine in https://github.com/gradle/gradle/pull/29534
            return Optional.of<CachingDisabledReason>(UnitOfWork.NOT_WORTH_CACHING)
        }

        override fun getClassSources(): MutableList<ClassSource> {
            val sources: MutableList<ClassSource> = ArrayList<ClassSource>()
            sources.add(DefaultDependenciesAccessors.RootProjectAccessorSource(projectRegistry.getRootProject()!!))
            for (project in projectRegistry.getAllProjects()) {
                sources.add(ProjectAccessorClassSource(project))
            }
            return sources
        }

        override fun visitImmutableInputs(visitor: InputVisitor) {
            visitor.visitInputProperty(IN_PROJECTS, InputVisitor.ValueSupplier { this.buildProjectTree() })
        }

        fun buildProjectTree(): String {
            val allprojects: MutableSet<out ProjectDescriptor> = projectRegistry.getAllProjects()
            return allprojects.stream()
                .map<String> { obj: ProjectDescriptor? -> obj!!.getPath() }
                .sorted()
                .collect(Collectors.joining(","))
        }

        override fun getDisplayName(): String {
            return "generation of project accessors"
        }

        companion object {
            private const val IN_PROJECTS = "projects"
        }
    }

    private class GeneratedAccessors(private val sourcesDir: File, private val classesDir: File)

    private class DependenciesAccessorClassSource(private val name: String, private val model: DefaultVersionCatalog, private val problemsService: Problems) : ClassSource {
        override fun getPackageName(): String {
            return ACCESSORS_PACKAGE
        }

        override fun getSimpleClassName(): String {
            return ACCESSORS_CLASSNAME_PREFIX + StringUtils.capitalize(name)
        }

        override fun getSource(): String {
            val writer = StringWriter()
            LibrariesSourceGenerator.Companion.generateSource(writer, model, ACCESSORS_PACKAGE, getSimpleClassName(), problemsService)
            return writer.toString()
        }
    }

    private class PluginsBlockDependenciesAccessorClassSource(private val name: String, private val model: DefaultVersionCatalog, private val problemsService: Problems) : ClassSource {
        override fun getPackageName(): String {
            return ACCESSORS_PACKAGE
        }

        override fun getSimpleClassName(): String {
            return ACCESSORS_CLASSNAME_PREFIX + StringUtils.capitalize(name) + DependenciesAccessors.IN_PLUGINS_BLOCK_FACTORIES_SUFFIX
        }

        override fun getSource(): String {
            val writer = StringWriter()
            LibrariesSourceGenerator.Companion.generatePluginsBlockSource(writer, model, ACCESSORS_PACKAGE, getSimpleClassName(), problemsService)
            return writer.toString()
        }
    }

    private class ProjectAccessorClassSource(private val project: ProjectDescriptor) : ClassSource {
        private var className: String? = null
        private var source: String? = null

        override fun getPackageName(): String {
            return ACCESSORS_PACKAGE
        }

        override fun getSimpleClassName(): String {
            ensureInitialized()
            return className!!
        }

        override fun getSource(): String {
            ensureInitialized()
            return source!!
        }

        fun ensureInitialized() {
            if (className == null) {
                val writer = StringWriter()
                className = ProjectAccessorsSourceGenerator.Companion.generateSource(writer, project, ACCESSORS_PACKAGE)
                source = writer.toString()
            }
        }
    }

    private class RootProjectAccessorSource(private val rootProject: ProjectDescriptor) : ClassSource {
        override fun getPackageName(): String {
            return ACCESSORS_PACKAGE
        }

        override fun getSimpleClassName(): String {
            return RootProjectAccessorSourceGenerator.Companion.ROOT_PROJECT_ACCESSOR_CLASSNAME
        }

        override fun getSource(): String {
            val writer = StringWriter()
            RootProjectAccessorSourceGenerator.Companion.generateSource(writer, rootProject, ACCESSORS_PACKAGE)
            return writer.toString()
        }
    }

    // public for injection
    class DefaultVersionCatalogsExtension @Inject constructor(private val catalogs: MutableMap<String, VersionCatalog>) : VersionCatalogsExtension {
        override fun find(name: String): Optional<VersionCatalog> {
            if (catalogs.containsKey(name)) {
                return Optional.of<VersionCatalog>(catalogs.get(name)!!)
            }
            return Optional.empty<VersionCatalog>()
        }

        override fun getCatalogNames(): MutableSet<String> {
            return ImmutableSet.copyOf<String>(catalogs.keys)
        }

        override fun iterator(): MutableIterator<VersionCatalog> {
            return catalogs.values.iterator()
        }
    }

    companion object {
        private const val SUPPORTED_PROJECT_NAMES = "[a-zA-Z]([A-Za-z0-9\\-_])*"
        private val SUPPORTED_PATTERN: Pattern = Pattern.compile(SUPPORTED_PROJECT_NAMES)
        private const val ACCESSORS_PACKAGE = "org.gradle.accessors.dm"
        private const val ACCESSORS_CLASSNAME_PREFIX = "LibrariesFor"
        private val ROOT_PROJECT_ACCESSOR_FQCN: String = ACCESSORS_PACKAGE + "." + RootProjectAccessorSourceGenerator.Companion.ROOT_PROJECT_ACCESSOR_CLASSNAME

        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultDependenciesAccessors::class.java)

        private fun warnIfRootProjectNameNotSetExplicitly(project: ProjectDescriptorInternal?) {
            if (!project!!.isExplicitName()) {
                LOGGER.warn(
                    "Project accessors enabled, but root project name not explicitly set for '" + project.getName() +
                            "'. Checking out the project in different folders will impact the generated code and implicitly the buildscript classpath, breaking caching."
                )
            }
        }

        private fun assertCanGenerateAccessors(projectRegistry: ProjectDescriptorRegistry): Boolean {
            val errors: MutableList<String> = ArrayList<String>()
            projectRegistry.getAllProjects()
                .stream()
                .map<String> { obj: ProjectDescriptorInternal? -> obj!!.getName() }
                .filter { p: String? -> !SUPPORTED_PATTERN.matcher(p).matches() }
                .map<String> { name: String? -> "project '" + name + "' doesn't follow the naming convention: " + SUPPORTED_PROJECT_NAMES }
                .forEach { e: String? -> errors.add(e!!) }
            for (project in projectRegistry.getAllProjects()) {
                project.getChildren()
                    .stream()
                    .map<String> { obj: ProjectDescriptor? -> obj!!.getName() }
                    .collect(Collectors.groupingBy(Function { alias: String? -> AbstractSourceGenerator.Companion.toJavaName(alias) }))
                    .entries
                    .stream()
                    .filter { e: MutableMap.MutableEntry<String, MutableList<String>>? -> e!!.value.size > 1 }
                    .forEachOrdered { e: MutableMap.MutableEntry<String, MutableList<String>>? ->
                        val javaName = e!!.key
                        val names = e.value
                        errors.add("subprojects " + names + " of project " + project.getPath() + " map to the same method name get" + javaName + "()")
                    }
            }
            if (!errors.isEmpty()) {
                val formatter = TreeFormatter()
                formatter.node("Cannot generate project dependency accessors")
                formatter.startChildren()
                for (error in errors) {
                    formatter.node("Cannot generate project dependency accessors because " + error)
                }
                formatter.endChildren()
                throw InvalidUserDataException(formatter.toString())
            }
            return errors.isEmpty()
        }

        private fun <T> loadFactory(classLoaderScope: ClassLoaderScope, className: String): Class<out T>? {
            val clazz: Class<out T>
            try {
                clazz = uncheckedCast<Class<out T>?>(classLoaderScope.getExportClassLoader().loadClass(className))!!
            } catch (e: ClassNotFoundException) {
                return null
            }
            return clazz
        }
    }
}

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

import com.google.common.base.Splitter
import org.apache.commons.lang3.StringUtils
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemSpecInternal
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.plugin.use.PluginDependency
import org.gradle.util.internal.TextUtil
import org.jspecify.annotations.NullMarked
import java.io.IOException
import java.io.Writer
import java.util.Objects
import java.util.function.Function
import java.util.stream.Collectors
import javax.inject.Inject

class LibrariesSourceGenerator(
    writer: Writer,
    private val config: DefaultVersionCatalog,
    problemsService: Problems
) : AbstractSourceGenerator(writer) {
    private val problemsService: ProblemsInternal

    private val classNameCounter: MutableMap<String, Int> = HashMap<String, Int>()
    private val classNameCache: MutableMap<ClassNode, String> = HashMap<ClassNode, String>()

    init {
        this.problemsService = problemsService as ProblemsInternal
    }

    @Throws(IOException::class)
    private fun generateProjectExtensionFactoryClass(packageName: String, className: String) {
        generateFactoryClass(packageName, LibrariesSourceGenerator.ThrowingConsumer { entryPoints: EntryPoints -> writeEntryPoints(className, entryPoints, false) }
        )
    }

    @Throws(IOException::class)
    private fun generatePluginsBlockFactoryClass(packageName: String, className: String) {
        generateFactoryClass(packageName, LibrariesSourceGenerator.ThrowingConsumer { entryPoints: EntryPoints -> writeEntryPoints(className, entryPoints, true) }
        )
    }

    private class EntryPoints(
        private val librariesEntryPoint: ClassNode,
        private val versionsEntryPoint: ClassNode,
        private val bundlesEntryPoint: ClassNode,
        private val pluginsEntryPoint: ClassNode
    )

    private interface ThrowingConsumer<T> {
        @Throws(IOException::class)
        fun accept(t: T?)
    }

    @Throws(IOException::class)
    private fun generateFactoryClass(packageName: String, entryPointsConsumer: ThrowingConsumer<EntryPoints>) {
        writeLn("package " + packageName + ";")
        writeLn()
        addImports()
        writeLn()
        val description = Objects.requireNonNull<String?>(TextUtil.normaliseLineSeparators(config.getDescription()))
        writeLn("/**")
        for (descLine in Splitter.on('\n').split(description!!)) {
            writeLn(" * " + descLine)
        }
        val libraries = config.getLibraryAliases()
        val bundles = config.getBundleAliases()
        val versions = config.getVersionAliases()
        val plugins = config.getPluginAliases()
        performValidation(libraries, bundles, versions, plugins)
        entryPointsConsumer.accept(
            LibrariesSourceGenerator.EntryPoints(
                toClassNode(libraries, rootNode(AccessorKind.library)),
                org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.toClassNode(
                    versions,
                    org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.rootNode(org.gradle.api.internal.catalog.LibrariesSourceGenerator.AccessorKind.version, "versions")
                ).parent!!,
                org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.toClassNode(
                    bundles,
                    org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.rootNode(org.gradle.api.internal.catalog.LibrariesSourceGenerator.AccessorKind.bundle, "bundles")
                ).parent!!,
                org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.toClassNode(
                    plugins,
                    org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.rootNode(org.gradle.api.internal.catalog.LibrariesSourceGenerator.AccessorKind.plugin, "plugins")
                ).parent!!
            )
        )
    }

    @Throws(IOException::class)
    private fun writeEntryPoints(className: String, entryPoints: EntryPoints, inPluginsBlock: Boolean) {
        writeLn(" */")
        writeLn("@NullMarked")
        writeLn("public class " + className + " extends AbstractExternalDependencyFactory {")
        writeLn()
        indent(WriteAction {
            writeLn("private final AbstractExternalDependencyFactory owner = this;")
            writeSubAccessorFieldsOf(entryPoints.librariesEntryPoint, AccessorKind.library)
            writeSubAccessorFieldsOf(entryPoints.versionsEntryPoint, AccessorKind.version)
            writeSubAccessorFieldsOf(entryPoints.bundlesEntryPoint, AccessorKind.bundle)
            writeSubAccessorFieldsOf(entryPoints.pluginsEntryPoint, AccessorKind.plugin)
            writeLn()
            writeLn("@Inject")
            writeLn("public " + className + "(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, AttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {")
            writeLn("    super(config, providers, objects, attributesFactory, capabilityNotationParser);")
            writeLn("}")
            writeLn()
            writeLibraryAccessors(entryPoints.librariesEntryPoint, inPluginsBlock)
            writeVersionAccessors(entryPoints.versionsEntryPoint)
            writeBundleAccessors(entryPoints.bundlesEntryPoint, inPluginsBlock)
            writePluginAccessors(entryPoints.pluginsEntryPoint)
            writeLibrarySubClasses(entryPoints.librariesEntryPoint, inPluginsBlock)
            writeVersionSubClasses(entryPoints.versionsEntryPoint)
            writeBundleSubClasses(entryPoints.bundlesEntryPoint, inPluginsBlock)
            writePluginSubClasses(entryPoints.pluginsEntryPoint)
        })
        writeLn("}")
    }

    @Throws(IOException::class)
    private fun addImports() {
        addImport(NullMarked::class.java)
        addImport(MinimalExternalModuleDependency::class.java)
        addImport(PluginDependency::class.java)
        addImport(ExternalModuleDependencyBundle::class.java)
        addImport(MutableVersionConstraint::class.java)
        addImport(Provider::class.java)
        addImport(ObjectFactory::class.java)
        addImport(ProviderFactory::class.java)
        addImport(AbstractExternalDependencyFactory::class.java)
        addImport(DefaultVersionCatalog::class.java)
        addImport(MutableMap::class.java)
        addImport(AttributesFactory::class.java)
        addImport(CapabilityNotationParser::class.java)
        addImport(Inject::class.java)
        addImport(GradleException::class.java)
    }

    @Throws(IOException::class)
    private fun writeLibrarySubClasses(classNode: ClassNode, inPluginsBlock: Boolean) {
        for (child in classNode.getChildren()) {
            writeLibraryAccessorClass(child, inPluginsBlock)
            writeLibrarySubClasses(child, inPluginsBlock)
        }
    }

    @Throws(IOException::class)
    private fun writeVersionSubClasses(classNode: ClassNode) {
        for (child in classNode.getChildren()) {
            writeVersionAccessorClass(child)
            writeVersionSubClasses(child)
        }
    }

    @Throws(IOException::class)
    private fun writeBundleSubClasses(classNode: ClassNode, inPluginsBlock: Boolean) {
        for (child in classNode.getChildren()) {
            writeBundleAccessorClass(child, inPluginsBlock)
            writeBundleSubClasses(child, inPluginsBlock)
        }
    }

    @Throws(IOException::class)
    private fun writePluginSubClasses(classNode: ClassNode) {
        for (child in classNode.getChildren()) {
            writePluginAccessorClass(child)
            writePluginSubClasses(child)
        }
    }

    @Throws(IOException::class)
    private fun writeBundleAccessorClass(classNode: ClassNode, inPluginsBlock: Boolean) {
        val isProvider = classNode.isAlsoProvider
        val interfaces = if (isProvider) " implements BundleNotationSupplier" else ""
        val bundleClassName = getClassName(classNode)
        val aliases = classNode.aliases
            .stream()
            .sorted()
            .collect(Collectors.toList())
        writeLn("public static class " + bundleClassName + " extends BundleFactory " + interfaces + "{")
        indent(WriteAction {
            writeSubAccessorFieldsOf(classNode, AccessorKind.bundle)
            writeLn()
            writeLn("public " + bundleClassName + "(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, AttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }")
            writeLn()
            if (isProvider) {
                val path = classNode.fullAlias
                val bundle = config.getBundle(path)
                val coordinates = bundle.getComponents().stream()
                    .map<DependencyModel> { alias: String? -> config.getDependencyData(alias!!) }
                    .map<String> { dependencyData: DependencyModel? -> Companion.coordinatesDescriptorFor(dependencyData!!) }
                    .collect(Collectors.toList())
                writeBundle(path, coordinates, bundle.getContext(), true, inPluginsBlock)
            }
            for (alias in aliases) {
                val childName: String = leafNodeForAlias(alias)
                if (!classNode.hasChild(childName)) {
                    val bundle = config.getBundle(alias)
                    val coordinates = bundle.getComponents().stream()
                        .map<DependencyModel> { alias: String? -> config.getDependencyData(alias!!) }
                        .map<String> { dependencyData: DependencyModel? -> Companion.coordinatesDescriptorFor(dependencyData!!) }
                        .collect(Collectors.toList())
                    writeBundle(alias, coordinates, bundle.getContext(), false, inPluginsBlock)
                }
            }
            for (child in classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.bundle, inPluginsBlock)
            }
        })
        writeLn("}")
        writeLn()
    }

    private fun getClassName(classNode: ClassNode): String {
        return classNameCache.computeIfAbsent(classNode) { classNode: ClassNode? -> this.getClassName0(classNode!!) }
    }

    private fun getClassName0(classNode: ClassNode): String {
        val name = classNode.className
        val loweredName = name.lowercase()
        if (!classNameCounter.containsKey(loweredName)) {
            classNameCounter.put(loweredName, 0)
            return name
        } else {
            val count = classNameCounter.get(loweredName)!! + 1
            classNameCounter.put(loweredName, count)
            return name + "$" + count
        }
    }

    @Throws(IOException::class)
    private fun writePluginAccessorClass(classNode: ClassNode) {
        val isProvider = classNode.isAlsoProvider
        val interfaces = if (isProvider) " implements PluginNotationSupplier" else ""
        val pluginClassName = getClassName(classNode)
        val aliases = classNode.aliases
            .stream()
            .sorted()
            .collect(Collectors.toList())
        writeLn("public static class " + pluginClassName + " extends PluginFactory " + interfaces + "{")
        indent(WriteAction {
            writeSubAccessorFieldsOf(classNode, AccessorKind.plugin)
            writeLn()
            writeLn("public " + pluginClassName + "(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }")
            writeLn()
            if (isProvider) {
                val path = classNode.fullAlias
                val plugin = config.getPlugin(path)
                writePlugin(path, plugin, true)
            }
            for (alias in aliases) {
                val childName: String = leafNodeForAlias(alias)
                if (!classNode.hasChild(childName)) {
                    val plugin = config.getPlugin(alias)
                    writePlugin(alias, plugin, false)
                }
            }
            for (child in classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.plugin)
            }
        })
        writeLn("}")
        writeLn()
    }

    @Throws(IOException::class)
    private fun writeLibraryAccessors(classNode: ClassNode, inPluginsBlock: Boolean) {
        val dependencies = classNode.aliases
        for (alias in dependencies) {
            val childName: String = leafNodeForAlias(alias)
            if (!classNode.hasChild(childName)) {
                val model = config.getDependencyData(alias)
                writeDependencyAccessor(alias, model, false, inPluginsBlock)
            }
        }
        for (child in classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.library, inPluginsBlock)
        }
    }

    @Throws(IOException::class)
    private fun writeVersionAccessors(classNode: ClassNode) {
        val versionsAliases = classNode.aliases
        for (alias in versionsAliases) {
            val childName: String = leafNodeForAlias(alias)
            if (!classNode.hasChild(childName)) {
                val model = config.getVersion(alias)
                writeSingleVersionAccessor(alias, model.getContext(), model.getVersion().getDisplayName(), false)
            }
        }
        for (child in classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.version)
        }
    }

    @Throws(IOException::class)
    private fun writeBundleAccessors(classNode: ClassNode, inPluginsBlock: Boolean) {
        val versionsAliases = classNode.aliases
        for (alias in versionsAliases) {
            val childName: String = leafNodeForAlias(alias)
            if (!classNode.hasChild(childName)) {
                val model = config.getBundle(alias)
                val coordinates = model.getComponents().stream()
                    .map<DependencyModel> { alias: String? -> config.getDependencyData(alias!!) }
                    .map<String> { dependencyData: DependencyModel? -> Companion.coordinatesDescriptorFor(dependencyData!!) }
                    .collect(Collectors.toList())
                writeBundle(alias, coordinates, model.getContext(), false, inPluginsBlock)
            }
        }
        for (child in classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.bundle, inPluginsBlock)
        }
    }

    @Throws(IOException::class)
    private fun writePluginAccessors(classNode: ClassNode) {
        val versionsAliases = classNode.aliases
        for (alias in versionsAliases) {
            val childName: String = leafNodeForAlias(alias)
            if (!classNode.hasChild(childName)) {
                val model = config.getPlugin(alias)
                writePlugin(alias, model, false)
            }
        }
        for (child in classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.plugin)
        }
    }

    @Throws(IOException::class)
    private fun writeSubAccessorFieldFor(classNode: ClassNode, kind: AccessorKind) {
        val className = getClassName(classNode)
        writeLn("private final " + className + " " + kind.accessorVariableNameFor(className) + " = new " + className + "(" + kind.constructorParams + ");")
    }

    @Throws(IOException::class)
    private fun writeSubAccessorFieldsOf(classNode: ClassNode, kind: AccessorKind) {
        for (child in classNode.getChildren()) {
            writeSubAccessorFieldFor(child, kind)
        }
    }

    @Throws(IOException::class)
    private fun writeLibraryAccessorClass(classNode: ClassNode, inPluginsBlock: Boolean) {
        val isProvider = classNode.isAlsoProvider
        val interfaces = if (isProvider) " implements DependencyNotationSupplier" else ""
        writeLn("public static class " + getClassName(classNode) + " extends SubDependencyFactory" + interfaces + " {")
        indent(WriteAction {
            writeSubAccessorFieldsOf(classNode, AccessorKind.library)
            writeLn()
            writeLn("public " + getClassName(classNode) + "(AbstractExternalDependencyFactory owner) { super(owner); }")
            writeLn()
            if (isProvider) {
                val path = classNode.fullAlias
                val model = config.getDependencyData(path)
                writeDependencyAccessor(path, model, true, inPluginsBlock)
            }
            for (alias in classNode.aliases) {
                val childName: String = leafNodeForAlias(alias)
                if (!classNode.hasChild(childName)) {
                    val model = config.getDependencyData(alias)
                    writeDependencyAccessor(alias, model, false, inPluginsBlock)
                }
            }
            for (child in classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.library, inPluginsBlock)
            }
        })
        writeLn("}")
        writeLn()
    }

    @Throws(IOException::class)
    private fun writeVersionAccessorClass(classNode: ClassNode) {
        val isProvider = classNode.isAlsoProvider
        val interfaces = if (isProvider) " implements VersionNotationSupplier" else ""
        val versionsClassName = getClassName(classNode)
        val versionAliases = classNode.aliases
        writeLn("public static class " + versionsClassName + " extends VersionFactory " + interfaces + " {")
        writeLn()
        indent(WriteAction {
            writeSubAccessorFieldsOf(classNode, AccessorKind.version)
            writeLn("public " + versionsClassName + "(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }")
            writeLn()
            if (isProvider) {
                val path = classNode.fullAlias
                val vm = config.getVersion(path)
                val context = vm.getContext()
                writeSingleVersionAccessor(path, context, vm.getVersion().getDisplayName(), true)
            }
            for (alias in versionAliases) {
                val childName: String = leafNodeForAlias(alias)
                if (!classNode.hasChild(childName)) {
                    val vm = config.getVersion(alias)
                    val context = vm.getContext()
                    writeSingleVersionAccessor(alias, context, vm.getVersion().getDisplayName(), false)
                }
            }
            for (child in classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.version)
            }
        })
        writeLn("}")
        writeLn()
    }

    @Throws(IOException::class)
    private fun writeSingleVersionAccessor(versionAlias: String, context: String?, version: String, asProvider: Boolean) {
        writeLn("/**")
        writeLn(" * Version alias <b>" + versionAlias + "</b> with value <b>" + version + "</b>")
        writeLn(" * <p>")
        writeLn(" * If the version is a rich version and cannot be represented as a")
        writeLn(" * single version string, an empty string is returned.")
        if (context != null) {
            writeLn(" * <p>")
            writeLn(" * This version was declared in " + sanitizeUnicodeEscapes(context))
        }
        writeLn(" */")
        val methodName = if (asProvider) "asProvider" else "get" + AbstractSourceGenerator.Companion.toJavaName(leafNodeForAlias(versionAlias))
        writeLn("public Provider<String> " + methodName + "() { return getVersion(\"" + versionAlias + "\"); }")
        writeLn()
    }

    private fun performValidation(libraries: MutableList<String>, bundles: MutableList<String>, versions: MutableList<String>, plugins: MutableList<String>) {
        assertUnique(libraries, "library aliases", "")
        assertUnique(bundles, "dependency bundles", "Bundle")
        assertUnique(versions, "dependency versions", "Version")
        assertUnique(plugins, "plugins", "Plugin")
        val size = libraries.size + bundles.size + versions.size + plugins.size
        if (size > MAX_ENTRIES) {
            throw throwVersionCatalogProblemException(problemsService.internalReporter!!.internalCreate({ builder ->
                org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.configureVersionCatalogError(
                    builder,
                    this.problemPrefix + "version catalog model contains too many entries (" + size + ")",
                    org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.TOO_MANY_ENTRIES
                )
                    .details("The maximum number of aliases in a catalog is " + org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.MAX_ENTRIES)!!
                    .solution("Reduce the number of aliases defined in this catalog")!!
                    .solution("Split the catalog into multiple catalogs")
            })!!)
        }
    }

    private fun throwVersionCatalogProblemException(problem: ProblemInternal): RuntimeException? {
        throw problemsService.reporter!!.throwing(InvalidUserDataException(), problem)
    }

    private fun assertUnique(names: MutableList<String>, prefix: String, suffix: String) {
        val errors: MutableList<ProblemInternal> = names.stream()
            .collect(Collectors.groupingBy(Function { alias: String? -> AbstractSourceGenerator.Companion.toJavaName(alias) }))
            .entries
            .stream()
            .filter { e: MutableMap.MutableEntry<String?, MutableList<String?>?>? -> e!!.value!!.size > 1 }
            .map<Any> { e: MutableMap.MutableEntry<String?, MutableList<String?>?>? ->
                val errorValues: String = e!!.value!!.stream().sorted().collect(oxfordJoin("and"))
                this.problemsService.internalReporter!!.internalCreate({ builder ->
                    org.gradle.api.internal.catalog.LibrariesSourceGenerator.Companion.configureVersionCatalogError(
                        builder,
                        this.problemPrefix + prefix + " " + errorValues + " are mapped to the same accessor name get" + e.key + suffix + "()",
                        org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.ACCESSOR_NAME_CLASH
                    )
                        .details("A name clash was detected")!!
                        .solution("Use a different alias for " + errorValues)
                })
            }
            .collect(Collectors.toList())
        if (!errors.isEmpty()) {
            throw this.problemsService.reporter!!.throwing(InvalidUserDataException(), errors)
        }
    }

    private val problemPrefix: String
        get() = DefaultCatalogProblemBuilder.getProblemInVersionCatalog(config.getName()) + ", "

    @Throws(IOException::class)
    private fun writeDependencyAccessor(alias: String, dependency: DependencyModel, asProvider: Boolean, inPluginsBlock: Boolean) {
        val name: String = leafNodeForAlias(alias)
        writeLn("/**")
        writeLn(" * Dependency provider for <b>" + name + "</b> with <b>" + coordinatesDescriptorFor(dependency) + "</b> coordinates and")
        writeVersionInformation(dependency.getVersionRef(), dependency.getVersion())
        val context = dependency.getContext()
        if (context != null) {
            writeLn(" * <p>")
            writeLn(" * This dependency was declared in " + sanitizeUnicodeEscapes(context))
        }
        writeLn(" */")
        val methodName = if (asProvider) "asProvider" else "get" + AbstractSourceGenerator.Companion.toJavaName(name)
        writeLn("public Provider<MinimalExternalModuleDependency> " + methodName + "() {")
        writeLn(throwForUnsupportedFeatureInPluginsBlockOr(inPluginsBlock, "    return create(\"" + alias + "\");"))
        writeLn("}")
        writeLn()
    }

    @Throws(IOException::class)
    private fun writeVersionInformation(versionRef: String?, version: ImmutableVersionConstraint) {
        if (versionRef != null) {
            writeLn(" * with version reference <b>" + versionRef + "</b>")
        } else {
            val versionDisplay = version.getDisplayName()
            if (versionDisplay.isEmpty()) {
                writeLn(" * with <b>no version specified</b>")
            } else {
                writeLn(" * with version <b>" + versionDisplay + "</b>")
            }
        }
    }

    @Throws(IOException::class)
    private fun writeSubAccessor(classNode: ClassNode, kind: AccessorKind, inPluginsBlock: Boolean = false) {
        val className = getClassName(classNode)
        val getter: String? = classNode.name
        writeLn("/**")
        writeLn(" * Group of " + kind.description + " at <b>" + classNode.path + "</b>")
        writeLn(" */")
        writeLn("public " + className + " get" + AbstractSourceGenerator.Companion.toJavaName(getter) + "() {")
        writeLn(throwForUnsupportedFeatureInPluginsBlockOr(inPluginsBlock, "    return " + kind.accessorVariableNameFor(className) + ";"))
        writeLn("}")
        writeLn()
    }

    @Throws(IOException::class)
    private fun writeBundle(alias: String, coordinates: MutableList<String>, context: String?, asProvider: Boolean, inPluginsBlock: Boolean) {
        writeLn("/**")
        if (coordinates.isEmpty()) {
            writeLn(" * Dependency bundle provider for <b>" + alias + "</b> which contains no dependencies")
        } else {
            writeLn(" * Dependency bundle provider for <b>" + alias + "</b> which contains the following dependencies:")
            writeLn(" * <ul>")
            for (coordinate in coordinates) {
                writeLn(" *    <li>" + coordinate + "</li>")
            }
            writeLn(" * </ul>")
        }
        if (context != null) {
            writeLn(" * <p>")
            writeLn(" * This bundle was declared in " + sanitizeUnicodeEscapes(context))
        }
        writeLn(" */")
        val methodName = if (asProvider) "asProvider" else "get" + AbstractSourceGenerator.Companion.toJavaName(leafNodeForAlias(alias))
        writeLn("public Provider<ExternalModuleDependencyBundle> " + methodName + "() {")
        writeLn(throwForUnsupportedFeatureInPluginsBlockOr(inPluginsBlock, "    return createBundle(\"" + alias + "\");"))
        writeLn("}")
        writeLn()
    }

    @Throws(IOException::class)
    private fun writePlugin(alias: String, plugin: PluginModel, asProvider: Boolean) {
        writeLn("/**")
        writeLn(" * Plugin provider for <b>" + alias + "</b> with plugin id <b>" + plugin.getId() + "</b> and")
        writeVersionInformation(plugin.getVersionRef(), plugin.getVersion())
        val context = plugin.getContext()
        if (context != null) {
            writeLn(" * <p>")
            writeLn(" * This plugin was declared in " + sanitizeUnicodeEscapes(context))
        }
        writeLn(" */")
        val methodName = if (asProvider) "asProvider" else "get" + AbstractSourceGenerator.Companion.toJavaName(leafNodeForAlias(alias))
        writeLn("public Provider<PluginDependency> " + methodName + "() { return createPlugin(\"" + alias + "\"); }")
        writeLn()
    }

    private class ClassNode(private val kind: AccessorKind, private val parent: ClassNode?, private val name: String?) {
        private val children: MutableMap<String, ClassNode> = LinkedHashMap<String, ClassNode>()
        val aliases: MutableSet<String> = LinkedHashSet<String>()
        private val leafAliases: MutableSet<String> = LinkedHashSet<String>()
        var wrapping: Boolean = false

        val simpleName: String
            get() {
                if (parent == null || wrapping) {
                    return ""
                }
                return parent.getSimpleName() + StringUtils.capitalize(name)
            }

        val className: String
            get() = this.simpleName + kind.classNameSuffix

        fun child(name: String): ClassNode {
            return children.computeIfAbsent(name) { n: String? -> ClassNode(kind, this, n) }
        }

        fun addAlias(alias: String) {
            aliases.add(alias)
            leafAliases.add(leafNodeForAlias(alias))
        }

        fun getChildren(): MutableCollection<ClassNode> {
            return children.values
        }

        fun hasChild(name: String): Boolean {
            return children.containsKey(name)
        }

        val path: String
            get() {
                if (parent == null) {
                    return ""
                }
                val parentPath = parent.getPath()
                return (if (parentPath.isEmpty()) name else parentPath + "." + name)!!
            }

        val fullAlias: String
            get() {
                if (parent == null || wrapping) {
                    return ""
                }
                val parentPath = parent.getFullAlias()
                return (if (parentPath.isEmpty()) name else parentPath + "." + name)!!
            }

        val isAlsoProvider: Boolean
            get() = parent != null &&
                    parent.leafAliases.contains(name) &&
                    parent.children.containsKey(name)

        override fun toString(): String {
            return "ClassNode{" +
                    "name='" + name + '\'' +
                    ", aliases=" + aliases +
                    '}'
        }
    }

    private enum class AccessorKind(val description: String, val constructorParams: String) {
        library("libraries", "owner"),
        version("versions", "providers, config"),
        bundle("bundles", "objects, providers, config, attributesFactory, capabilityNotationParser"),
        plugin("plugins", "providers, config");

        private val variablePrefix: String

        init {
            this.variablePrefix = name.get(0).toString() + "acc"
        }

        val classNameSuffix: String
            get() = StringUtils.capitalize(name) + "Accessors"

        fun accessorVariableNameFor(className: String): String {
            return variablePrefix + "For" + className
        }
    }

    companion object {
        private const val MAX_ENTRIES = 30000
        const val ERROR_HEADER: String = "Cannot generate dependency accessors"
        fun generateSource(
            writer: Writer,
            config: DefaultVersionCatalog,
            packageName: String,
            className: String,
            problemsService: Problems
        ) {
            val generator = LibrariesSourceGenerator(writer, config, problemsService)
            try {
                generator.generateProjectExtensionFactoryClass(packageName, className)
                generator.classNameCounter.clear()
                generator.classNameCache.clear()
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }

        fun generatePluginsBlockSource(
            writer: Writer,
            config: DefaultVersionCatalog,
            packageName: String,
            className: String,
            problemsService: Problems
        ) {
            val generator = LibrariesSourceGenerator(writer, config, problemsService)
            try {
                generator.generatePluginsBlockFactoryClass(packageName, className)
                generator.classNameCounter.clear()
                generator.classNameCache.clear()
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }

        private fun configureVersionCatalogError(spec: ProblemSpecInternal, message: String, catalogProblemId: VersionCatalogProblemId): ProblemSpecInternal {
            return spec
                .id(
                    org.gradle.util.internal.TextUtil.screamingSnakeToKebabCase(catalogProblemId.name),
                    catalogProblemId.getDisplayName(),
                    org.gradle.api.problems.internal.GradleCoreProblemGroup.versionCatalog()
                )
                .contextualLabel(message)
                .documentedAt(
                    org.gradle.internal.deprecation.Documentation.Companion.userManual(
                        org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.VERSION_CATALOG_PROBLEMS,
                        catalogProblemId.name.lowercase()
                    )
                )!!
        }

        private fun coordinatesDescriptorFor(dependencyData: DependencyModel): String {
            return dependencyData.getGroup() + ":" + dependencyData.getName()
        }

        private fun leafNodeForAlias(alias: String): String {
            val split: MutableList<String> = AbstractSourceGenerator.Companion.nameSplitter().splitToList(alias)
            return split.get(split.size - 1)
        }

        private fun throwForUnsupportedFeatureInPluginsBlockOr(inPluginsBlock: Boolean, or: String): String {
            return if (inPluginsBlock)
                ("    throw new GradleException(" +
                        "\"Accessing libraries or bundles from version catalogs in the plugins block is not allowed. " +
                        "Only use versions or plugins from catalogs in the plugins block.\");")
            else
                or
        }

        /**
         * Java compiler would fail to compile sources that have illegal unicode escape characters, including in the comments.
         * Such characters could be accidentally introduced by a backslash followed by `'u'`,
         * e.g. in Windows path `'..\\user\dir'`.
         */
        private fun sanitizeUnicodeEscapes(s: String): String {
            // If a backslash precedes 'u', then we replace the backslash with its unicode notation '\\u005c'
            return s.replace("\\u", "\\u005cu")
        }

        private fun rootNode(kind: AccessorKind): ClassNode {
            return ClassNode(kind, null, null)
        }

        private fun rootNode(kind: AccessorKind, nest: String): ClassNode {
            val root: ClassNode = rootNode(kind)
            val wrappingNode = root.child(nest)
            wrappingNode.wrapping = true
            return wrappingNode
        }

        private fun toClassNode(aliases: MutableList<String>, root: ClassNode): ClassNode {
            for (alias in aliases) {
                var current = root
                // foo -> foo is the alias
                // foo.bar.baz --> baz is the alias
                val dotted: MutableList<String> = AbstractSourceGenerator.Companion.nameSplitter().splitToList(alias)
                val last = dotted.size - 1
                for (i in 0..<last) {
                    current = current.child(dotted.get(i))
                }
                current.addAlias(alias)
            }
            return root
        }
    }
}

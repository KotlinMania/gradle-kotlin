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
package org.gradle.buildinit.plugins.internal

import com.google.common.base.Objects
import com.google.common.base.Splitter
import com.google.common.collect.ListMultimap
import com.google.common.collect.MultimapBuilder
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.buildinit.InsecureProtocolOption
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.groovy.scripts.internal.InitialPassStatementTransformer
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.util.internal.GFileUtils
import org.gradle.util.internal.GUtil
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Arrays
import java.util.stream.Collectors

/**
 * Assembles the parts of a build script.
 */
class BuildScriptBuilder internal constructor(
    private val dsl: BuildInitDsl,
    documentationRegistry: DocumentationRegistry,
    buildContentGenerationContext: BuildContentGenerationContext,
    val fileNameWithoutExtension: String,
    private val useIncubatingAPIs: Boolean,
    insecureProtocolOption: InsecureProtocolOption,
    useVersionCatalog: Boolean
) {
    private val mavenRepoURLHandler: MavenRepositoryURLHandler
    private val buildContentGenerationContext: BuildContentGenerationContext
    private var comments = BuildInitComments.ON

    private val headerCommentLines: MutableList<String> = ArrayList<String>()
    private val block: TopLevelBlock

    val isUsingTestSuites: Boolean
    private val useVersionCatalog: Boolean

    init {
        this.isUsingTestSuites = useIncubatingAPIs
        this.mavenRepoURLHandler = MavenRepositoryURLHandler.Companion.forInsecureProtocolOption(
            insecureProtocolOption,
            dsl, documentationRegistry
        )
        this.block = TopLevelBlock(this)
        this.buildContentGenerationContext = buildContentGenerationContext
        this.useVersionCatalog = useVersionCatalog
    }

    fun withComments(comments: BuildInitComments): BuildScriptBuilder {
        this.comments = comments
        return this
    }

    /**
     * Adds a comment to the header of the file.
     */
    fun fileComment(comment: String): BuildScriptBuilder {
        headerCommentLines.addAll(splitComment(comment))
        return this
    }

    val suites: MutableList<SuiteSpec>
        get() = ArrayList<SuiteSpec>(block.testing.suites)

    /**
     * Adds the plugin and config needed to support writing pre-compiled script plugins in the selected DSL in this project.
     */
    fun conventionPluginSupport(comment: String?): BuildScriptBuilder {
        val syntax: Syntax = syntaxFor(dsl)
        block.repositories.gradlePluginPortal("Use the plugin portal to apply community plugins in convention plugins.")
        syntax.configureConventionPlugin(comment, block.plugins, block.repositories)
        return this
    }

    /**
     * Adds a plugin to be applied
     *
     * @param comment A description of why the plugin is required
     */
    /**
     * Adds a plugin to be applied
     *
     * @param comment A description of why the plugin is required
     */
    @JvmOverloads
    fun plugin(comment: String?, pluginId: String, version: String? = null, pluginAlias: String? = null): BuildScriptBuilder {
        val plugin: AbstractStatement?
        if (useVersionCatalog && version != null) {
            val versionCatalogRef = buildContentGenerationContext.getVersionCatalogDependencyRegistry().registerPlugin(pluginId, version, pluginAlias)
            plugin = PluginSpec(versionCatalogRef, comment)
        } else {
            plugin = PluginSpec(pluginId, version, comment)
        }
        block.plugins.add(plugin)
        return this
    }

    /**
     * Adds one or more external dependencies to the specified configuration.
     *
     * @param configuration The configuration where the dependency should be added
     * @param comment A description of why the dependencies are required
     * @param dependencies the dependencies
     */
    fun dependency(configuration: String, comment: String?, vararg dependencies: BuildInitDependency): BuildScriptBuilder {
        dependencies().dependency(configuration, comment, *dependencies)
        return this
    }

    /**
     * Adds one or more external implementation dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    fun implementationDependency(comment: String?, vararg dependencies: BuildInitDependency): BuildScriptBuilder {
        return dependency("implementation", comment, *dependencies)
    }

    /**
     * Adds one or more dependency constraints to the implementation scope.
     *
     * @param comment A description of why the constraints are required
     * @param dependencies The dependency constraints
     */
    fun implementationDependencyConstraint(comment: String?, vararg dependencies: BuildInitDependency): BuildScriptBuilder {
        dependencies().dependencyConstraint("implementation", comment, *dependencies)
        return this
    }

    /**
     * Adds one or more external test implementation dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    fun testImplementationDependency(comment: String?, vararg dependencies: BuildInitDependency): BuildScriptBuilder {
        assert(!this.isUsingTestSuites) { "do not add dependencies directly to testImplementation configuration" }
        return dependency("testImplementation", comment, *dependencies)
    }

    /**
     * Adds one or more external test runtime only dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    fun testRuntimeOnlyDependency(comment: String?, vararg dependencies: BuildInitDependency): BuildScriptBuilder {
        assert(!this.isUsingTestSuites) { "do not add dependencies directly to testRuntimeOnly configuration" }
        return dependency("testRuntimeOnly", comment, *dependencies)
    }

    /**
     * Creates a method invocation expression, to use as a method argument or the RHS of a property assignment.
     */
    fun methodInvocationExpression(methodName: String, vararg methodArgs: Any): Expression {
        return MethodInvocationExpression(null, methodName, expressionValues(*methodArgs))
    }

    /**
     * Creates a property expression, to use as a method argument or the RHS of a property assignment.
     */
    fun propertyExpression(value: String): Expression {
        return LiteralValue(value)
    }

    /**
     * Creates a property expression, to use as a method argument or the RHS of a property assignment.
     */
    fun propertyExpression(expression: Expression, value: String): Expression {
        return ChainedPropertyExpression(expressionValue(expression), LiteralValue(value))
    }

    /**
     * Creates an expression that references an element in a container.
     */
    fun containerElementExpression(container: String, element: String): Expression {
        return ContainerElementExpression(container, element)
    }

    /**
     * Allows repositories to be added to this script.
     */
    fun repositories(): RepositoriesBuilder {
        return block.repositories
    }

    /**
     * Allows dependencies to be added to this script.
     */
    fun dependencies(): DependenciesBuilder {
        return block.dependencies
    }

    /**
     * Allows test suites to be added to this script.
     */
    fun testing(): TestingBuilder {
        return block.testing
    }

    /**
     * Adds a top level method invocation statement.
     *
     * @return this
     */
    fun methodInvocation(comment: String?, methodName: String, vararg methodArgs: Any): BuildScriptBuilder {
        block.methodInvocation(comment, methodName, *methodArgs)
        return this
    }

    /**
     * Adds a top level method invocation statement.
     *
     * @return this
     */
    fun methodInvocation(comment: String?, target: Expression, methodName: String, vararg methodArgs: Any): BuildScriptBuilder {
        block.methodInvocation(comment, target, methodName, *methodArgs)
        return this
    }

    /**
     * Adds a top level property assignment statement.
     *
     * @return this
     */
    fun propertyAssignment(comment: String?, propertyName: String, propertyValue: Any): BuildScriptBuilder {
        block.propertyAssignment(comment, propertyName, propertyValue, true)
        return this
    }

    /**
     * Adds a top level block statement.
     *
     * @return The body of the block, to which further statements can be added.
     */
    fun block(comment: String?, methodName: String): ScriptBlockBuilder {
        return block.block(comment, methodName)
    }

    /**
     * Adds a top level block statement.
     */
    fun block(comment: String?, methodName: String, blockContentBuilder: Action<in ScriptBlockBuilder>): BuildScriptBuilder {
        blockContentBuilder.execute(block.block(comment, methodName))
        return this
    }

    fun javaToolchainFor(languageVersion: JavaLanguageVersion): BuildScriptBuilder {
        return block("Apply a specific Java toolchain to ease working on different environments.", "java", Action { t: ScriptBlockBuilder ->
            t.block(null, "toolchain", Action { t1: ScriptBlockBuilder? ->
                t1!!.propertyAssignment(
                    null, "languageVersion",
                    MethodInvocationExpression(null, "JavaLanguageVersion.of", mutableListOf<ExpressionValue>(LiteralValue(languageVersion.asInt()))),
                    true
                )
            })
        })
    }

    /**
     * Adds a method invocation statement to the configuration of a particular task.
     */
    fun taskMethodInvocation(comment: String?, taskName: String, taskType: String, methodName: String, vararg methodArgs: Any): BuildScriptBuilder {
        block.tasks.add(
            TaskSelector(taskName, taskType),
            BuildScriptBuilder.MethodInvocation(comment!!, MethodInvocationExpression(null, methodName, expressionValues(*methodArgs)))
        )
        return this
    }

    /**
     * Adds a property assignment statement to the configuration of a particular task.
     */
    fun taskPropertyAssignment(comment: String?, taskName: String, taskType: String, propertyName: String, propertyValue: Any): BuildScriptBuilder {
        block.tasks.add(
            TaskSelector(taskName, taskType),
            BuildScriptBuilder.PropertyAssignment(comment!!, propertyName, expressionValue(propertyValue), true)
        )
        return this
    }

    /**
     * Adds a property assignment statement to the configuration of all tasks of a particular type.
     */
    fun taskPropertyAssignment(comment: String?, taskType: String, propertyName: String, propertyValue: Any): BuildScriptBuilder {
        block.taskTypes.add(
            TaskTypeSelector(taskType),
            BuildScriptBuilder.PropertyAssignment(comment!!, propertyName, expressionValue(propertyValue), true)
        )
        return this
    }

    /**
     * Configure an existing task.
     *
     * @return An expression that can be used to refer to the task later.
     */
    fun taskConfiguration(comment: String?, taskName: String, taskType: String, blockContentsBuilder: Action<in ScriptBlockBuilder>): TaskConfiguration {
        val conf = TaskConfiguration(comment, taskName, taskType)
        block.add(conf)
        blockContentsBuilder.execute(conf.body)
        return conf
    }

    /**
     * Configure an existing task within the given block.
     *
     * @return An expression that can be used to refer to the task later.
     */
    fun taskConfiguration(comment: String?, containingBlock: BlockStatement, taskName: String, taskType: String, blockContentsBuilder: Action<in ScriptBlockBuilder>): TaskConfiguration {
        val conf = TaskConfiguration(comment, taskName, taskType)
        containingBlock.add(conf)
        blockContentsBuilder.execute(conf.body)
        return conf
    }

    /**
     * Registers a task.
     *
     * @return An expression that can be used to refer to the task later.
     */
    fun taskRegistration(comment: String?, taskName: String, taskType: String, blockContentsBuilder: Action<in ScriptBlockBuilder>): TaskRegistration {
        val registration = TaskRegistration(comment, taskName, taskType)
        block.add(registration)
        blockContentsBuilder.execute(registration.body)
        return registration
    }

    /**
     * Registers a task within the containing block.
     *
     * @return An expression that can be used to refer to the task later.
     */
    fun taskRegistration(comment: String?, containingBlock: BlockStatement, taskName: String, taskType: String, blockContentsBuilder: Action<in ScriptBlockBuilder>): TaskRegistration {
        val registration = TaskRegistration(comment, taskName, taskType)
        containingBlock.add(registration)
        blockContentsBuilder.execute(registration.body)
        return registration
    }

    /**
     * Configure an existing test suite within the given block.
     *
     * @return An expression that can be used to refer to the task later.
     */
    fun suiteConfiguration(comment: String?, containingBlock: BlockStatement, taskName: String, taskType: String, blockContentsBuilder: Action<in ScriptBlockBuilder>): SuiteConfiguration {
        val conf = SuiteConfiguration(comment, taskName, taskType)
        containingBlock.add(conf)
        blockContentsBuilder.execute(conf.body)
        return conf
    }

    /**
     * Registers a test suite within the containing block.
     *
     * @return An expression that can be used to refer to the task later.
     */
    fun suiteRegistration(comment: String?, containingBlock: BlockStatement, taskName: String, taskType: String, blockContentsBuilder: Action<in ScriptBlockBuilder>): SuiteRegistration {
        val registration = SuiteRegistration(comment, taskName, taskType)
        containingBlock.add(registration)
        blockContentsBuilder.execute(registration.body)
        return registration
    }

    /**
     * Creates an element in the given container.
     *
     * @param varName A variable to use to reference the element, if required by the DSL. If `null`, then use the element name.
     * @return An expression that can be used to refer to the element later in the script.
     */
    fun createContainerElement(comment: String?, container: String, elementName: String, varName: String?): Expression {
        val containerElement = BuildScriptBuilder.ContainerElement(comment!!, container, elementName, null, varName)
        block.add(containerElement)
        return containerElement
    }

    fun create(targetDirectory: Directory): TemplateOperation {
        return TemplateOperation {
            if (useIncubatingAPIs) {
                headerCommentLines.add(incubatingApisWarning)
            }
            val target = getTargetFile(targetDirectory)
            GFileUtils.mkdirs(target.getParentFile())
            try {
                PrintWriter(Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)).use { writer ->
                    val printer = PrettyPrinter(syntaxFor(dsl), writer, comments)
                    if (comments != BuildInitComments.OFF) {
                        printer.printFileHeader(headerCommentLines)
                    }
                    block.writeBodyTo(printer)
                }
            } catch (e: Exception) {
                throw GradleException("Could not generate file " + target + ".", e)
            }
        }
    }

    fun extractComments(): MutableList<String> {
        return block.extractComments()
    }

    private fun getTargetFile(targetDirectory: Directory): File {
        return targetDirectory.file(dsl.fileNameFor(fileNameWithoutExtension)).getAsFile()
    }

    fun includePluginsBuild() {
        block.includePluginsBuild()
    }

    fun useVersionCatalogFromOuterBuild(comment: String) {
        block.useVersionCatalogFromOuterBuild(comment)
    }

    interface Expression

    private interface ExpressionValue : Expression {
        val isBooleanType: Boolean
            get() = false

        fun with(syntax: Syntax): String
    }

    private class ChainedPropertyExpression(private val left: ExpressionValue, private val right: ExpressionValue) : ExpressionValue {
        override fun with(syntax: Syntax): String {
            return left.with(syntax) + "." + right.with(syntax)
        }
    }

    private class StringValue(val value: CharSequence) : ExpressionValue {
        override fun with(syntax: Syntax): String {
            return syntax.string(value.toString())
        }
    }

    private class LiteralValue(val literal: Any) : ExpressionValue {
        override fun isBooleanType(): Boolean {
            return literal is Boolean
        }

        override fun with(syntax: Syntax): String {
            return literal.toString()
        }
    }

    private class EnumValue(literal: Any) : ExpressionValue {
        val literal: Enum<*>

        init {
            this.literal = uncheckedNonnullCast<Enum<*>?>(literal)!!
        }

        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        override fun with(syntax: Syntax): String {
            return literal.javaClass.getSimpleName() + "." + literal.name
        }
    }

    private class MapLiteralValue(val literal: MutableMap<String, ExpressionValue>) : ExpressionValue {
        override fun with(syntax: Syntax): String {
            return syntax.mapLiteral(literal)
        }
    }

    /**
     * This class is part of an attempt to provide the minimal functionality needed to script calling methods
     * which have a no-arg closure as their only parameter.
     *
     * TODO: Improve this to be more general, handle more statements than just method calls,
     * indent multi-statement closures properly, possibly handle args
     */
    private class NoArgClosureExpression(vararg calls: MethodInvocation) : ExpressionValue {
        val calls: MutableList<MethodInvocation> = ArrayList<MethodInvocation>()

        init {
            this.calls.addAll(Arrays.asList<MethodInvocation>(*calls))
        }

        override fun with(syntax: Syntax): String {
            return "{" + calls.stream()
                .map<String> { call: MethodInvocation? -> call!!.invocationExpression.with(syntax) }
                .collect(Collectors.joining("\n", " ", " ")) +
                    "}"
        }
    }

    private class MethodInvocationExpression : ExpressionValue {
        private val target: ExpressionValue?
        val methodName: String
        val arguments: MutableList<ExpressionValue>

        internal constructor(target: ExpressionValue?, methodName: String, arguments: MutableList<ExpressionValue>) {
            this.target = target
            this.methodName = methodName
            this.arguments = arguments
        }

        internal constructor(target: ExpressionValue?, methodName: String, closureArg: NoArgClosureExpression) {
            this.target = target
            this.methodName = methodName
            this.arguments = mutableListOf<ExpressionValue>(closureArg)
        }

        internal constructor(methodName: String) : this(null, methodName, mutableListOf<ExpressionValue>())

        override fun with(syntax: Syntax): String {
            val result = StringBuilder()
            if (target != null) {
                result.append(target.with(syntax))
                result.append('.')
            }
            result.append(methodName)

            val onlyArgIsClosure = arguments.size == 1 && arguments.get(0) is NoArgClosureExpression

            if (onlyArgIsClosure) {
                result.append(' ')
            } else {
                result.append("(")
            }

            for (i in arguments.indices) {
                val argument = arguments.get(i)
                if (i == 0) {
                    result.append(syntax.firstArg(argument))
                } else {
                    result.append(", ")
                    result.append(argument.with(syntax))
                }
            }

            if (onlyArgIsClosure) {
                result.append(' ')
            } else {
                result.append(")")
            }

            return result.toString()
        }
    }

    private class ContainerElementExpression(private val container: String, private val element: String) : ExpressionValue {
        override fun with(syntax: Syntax): String {
            return syntax.containerElement(container, element)
        }
    }

    private class PluginSpec : AbstractStatement {
        val id: String?
        val version: String?
        val versionCatalogRef: String?

        internal constructor(id: String, version: String?, comment: String?) : super(comment) {
            this.id = id
            this.version = version
            this.versionCatalogRef = null
        }

        internal constructor(versionCatalogRef: String, comment: String?) : super(comment) {
            this.id = null
            this.version = null
            this.versionCatalogRef = versionCatalogRef
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            if (versionCatalogRef != null) {
                printer.println(printer.syntax.pluginAliasSpec(versionCatalogRef)!!)
            } else {
                printer.println(printer.syntax.pluginDependencySpec(id!!, version)!!)
            }
        }
    }

    private class DepSpec(
        val configuration: String,
        comment: String?,
        val dependencyOrCatalogReference: String,
        val catalogReference: Boolean,
        val exclusions: MutableCollection<BuildInitDependency.DependencyExclusion>
    ) : AbstractStatement(comment) {
        override fun writeCodeTo(printer: PrettyPrinter) {
            val notation: String?
            if (catalogReference) {
                notation = dependencyOrCatalogReference
            } else {
                notation = printer.syntax.string(dependencyOrCatalogReference)
            }
            if (exclusions.isEmpty()) {
                printer.println(printer.syntax.dependencySpec(configuration, notation)!!)
            } else {
                val dependencyBlock = ScriptBlockImpl()
                for (exclusion in exclusions) {
                    val exclusionConfig: MutableMap<String, String> = LinkedHashMap<String, String>()
                    exclusionConfig.put("group", exclusion.getGroup())
                    exclusionConfig.put("module", exclusion.getModule())

                    val comment = "TODO: This exclude was sourced from a POM exclusion and is NOT exactly equivalent, see: " + userManual("build_init_plugin", "sec:pom_maven_conversion").url
                    dependencyBlock.add(MethodInvocation(comment, MethodInvocationExpression(null, "exclude", expressionValues(exclusionConfig))))
                }
                printer.printBlock(printer.syntax.complexDependencySpec(configuration, notation)!!, dependencyBlock)
                printer.needSeparatorLine = false
            }
        }
    }

    private class PlatformDepSpec(private val configuration: String, comment: String?, private val dependencyOrCatalogReference: String, val catalogReference: Boolean) : AbstractStatement(comment) {
        override fun writeCodeTo(printer: PrettyPrinter) {
            if (catalogReference) {
                printer.println(
                    printer.syntax.dependencySpec(
                        configuration, "platform(" + dependencyOrCatalogReference + ")"
                    )!!
                )
            } else {
                printer.println(
                    printer.syntax.dependencySpec(
                        configuration, "platform(" + printer.syntax.string(dependencyOrCatalogReference) + ")"
                    )!!
                )
            }
        }
    }

    private class SelfDepSpec(private val configuration: String, comment: String?) : AbstractStatement(comment) {
        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.println(printer.syntax.dependencySpec(configuration, "project()")!!)
        }
    }

    private class ProjectDepSpec(private val configuration: String, comment: String, private val projectPath: String) : AbstractStatement(comment) {
        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.println(printer.syntax.dependencySpec(configuration, "project(" + printer.syntax.string(projectPath) + ")")!!)
        }
    }

    private interface ConfigSelector {
        fun codeBlockSelectorFor(syntax: Syntax): String?
    }

    private class TaskSelector(val taskName: String, val taskType: String) : ConfigSelector {
        override fun codeBlockSelectorFor(syntax: Syntax): String? {
            return syntax.taskSelector(this)
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as TaskSelector
            return Objects.equal(taskName, that.taskName) && Objects.equal(taskType, that.taskType)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(taskName, taskType)
        }
    }

    private class TaskTypeSelector(val taskType: String) : ConfigSelector {
        override fun codeBlockSelectorFor(syntax: Syntax): String? {
            return syntax.taskByTypeSelector(taskType)
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as TaskTypeSelector
            return Objects.equal(taskType, that.taskType)
        }

        override fun hashCode(): Int {
            return taskType.hashCode()
        }
    }

    /**
     * Represents a statement in a script. Each statement has an optional comment that explains its purpose.
     */
    interface Statement {
        enum class Type {
            Empty, Single, Group
        }

        val comment: String?

        /**
         * Returns details of the size of this statement. Returns [Type.Empty] when this statement is empty and should not be included in the script.
         */
        fun type(): Type

        /**
         * Writes this statement to the given printer. Should not write the comment. Called only when [.type] returns a value != [Type.Empty]
         */
        fun writeCodeTo(printer: PrettyPrinter)
    }

    private abstract class AbstractStatement(private val comment: String?) : Statement {
        override fun getComment(): String? {
            return comment
        }

        override fun type(): Statement.Type {
            return Statement.Type.Single
        }
    }

    @NullMarked
    private class StatementGroup(comment: String?) : AbstractStatement(comment) {
        private val statements: MutableList<Statement> = ArrayList<Statement>()

        override fun type(): Statement.Type {
            return if (getComment() == null) Statement.Type.Single else Statement.Type.Group
        }

        fun add(statement: Statement): StatementGroup {
            statements.add(statement)
            return this
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            for (statement in statements) {
                statement.writeCodeTo(printer)
            }
        }
    }

    private class MethodInvocation(comment: String, val invocationExpression: MethodInvocationExpression) : AbstractStatement(comment) {
        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.println(invocationExpression.with(printer.syntax))
        }
    }

    private class ContainerElement(
        private val containerComment: String,
        private val container: String,
        private val elementName: String,
        private val elementType: String?,
        private val varName: String?
    ) : AbstractStatement(null), ExpressionValue {
        private val body = ScriptBlockImpl()

        override fun writeCodeTo(printer: PrettyPrinter) {
            val statement = printer.syntax.createContainerElement(containerComment, container, elementName, elementType, varName, body.statements)
            printer.printStatement(statement)
        }

        override fun with(syntax: Syntax): String {
            return syntax.referenceCreatedContainerElement(container, elementName, varName)
        }
    }

    private class PropertyAssignment(comment: String, val propertyName: String, val propertyValue: ExpressionValue, val assignOperator: Boolean) : AbstractStatement(comment) {
        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.println(printer.syntax.propertyAssignment(this)!!)
        }
    }

    private class SingleLineComment(comment: String) : AbstractStatement(comment) {
        override fun writeCodeTo(printer: PrettyPrinter) {
            // NO OP
        }
    }

    /**
     * Represents the contents of a block.
     */
    private interface BlockBody {
        fun writeBodyTo(printer: PrettyPrinter)

        val statements: MutableList<Statement>?
    }

    private open class BlockStatement(private val comment: String?, val blockSelector: String) : Statement {
        val body: ScriptBlockImpl = ScriptBlockImpl()

        internal constructor(blockSelector: String) : this(null, blockSelector)

        override fun getComment(): String? {
            return comment
        }

        override fun type(): Statement.Type {
            return body.type()
        }

        fun add(statement: Statement) {
            body.add(statement)
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(blockSelector, body)
        }
    }

    private class ScriptBlock(comment: String, blockSelector: String) : BlockStatement(comment, blockSelector) {
        override fun type(): Statement.Type {
            // Always treat as non-empty
            return Statement.Type.Group
        }
    }

    private class RepositoriesBlock(private val builder: BuildScriptBuilder) : BlockStatement("repositories"), RepositoriesBuilder {
        override fun mavenLocal(comment: String) {
            add(MethodInvocation(comment, MethodInvocationExpression("mavenLocal")))
        }

        override fun mavenCentral(comment: String?) {
            add(BuildScriptBuilder.MethodInvocation(comment!!, MethodInvocationExpression("mavenCentral")))
        }

        override fun gradlePluginPortal(comment: String?) {
            add(BuildScriptBuilder.MethodInvocation(comment!!, MethodInvocationExpression("gradlePluginPortal")))
        }

        override fun maven(comment: String, url: String) {
            add(MavenRepoExpression(comment, url, builder))
        }
    }

    private class DependenciesBlock(val buildScriptBuilder: BuildScriptBuilder) : DependenciesBuilder, Statement, BlockBody {
        val dependencies: ListMultimap<String, Statement> = MultimapBuilder.linkedHashKeys().arrayListValues().build<String, Statement>()
        val constraints: ListMultimap<String, Statement> = MultimapBuilder.linkedHashKeys().arrayListValues().build<String, Statement>()

        override fun dependency(configuration: String, comment: String?, vararg dependencies: BuildInitDependency) {
            this.dependencies.put(configuration, makeDepSpec(configuration, comment, *dependencies))
        }

        override fun dependencyConstraint(configuration: String, comment: String?, vararg dependencies: BuildInitDependency) {
            this.constraints.put(configuration, makeDepSpec(configuration, comment, *dependencies))
        }

        fun makeDepSpec(configuration: String, comment: String?, vararg dependencies: BuildInitDependency): Statement {
            val statementGroup = StatementGroup(comment)
            for (d in dependencies) {
                if (d.getVersion() != null && buildScriptBuilder.useVersionCatalog) {
                    val versionCatalogRef = buildScriptBuilder.buildContentGenerationContext.getVersionCatalogDependencyRegistry().registerLibrary(d.getModule(), d.getVersion()!!)
                    statementGroup.add(DepSpec(configuration, null, versionCatalogRef, true, d.getExclusions()))
                } else {
                    statementGroup.add(DepSpec(configuration, null, d.toNotation(), false, d.getExclusions()))
                }
            }
            return statementGroup
        }

        override fun platformDependency(configuration: String, comment: String?, vararg dependencies: BuildInitDependency) {
            val statementGroup = StatementGroup(comment)
            for (d in dependencies) {
                if (d.getVersion() != null && buildScriptBuilder.useVersionCatalog) {
                    val versionCatalogRef = buildScriptBuilder.buildContentGenerationContext.getVersionCatalogDependencyRegistry().registerLibrary(d.getModule(), d.getVersion()!!)
                    statementGroup.add(PlatformDepSpec(configuration, comment, versionCatalogRef, true))
                } else {
                    statementGroup.add(PlatformDepSpec(configuration, comment, d.toNotation(), false))
                }
            }
            this.dependencies.put(configuration, statementGroup)
        }

        override fun projectDependency(configuration: String, comment: String?, projectPath: String) {
            this.dependencies.put(configuration, BuildScriptBuilder.ProjectDepSpec(configuration, comment!!, projectPath))
        }

        override fun selfDependency(configuration: String, comment: String?) {
            this.dependencies.put(configuration, SelfDepSpec(configuration, comment))
        }

        override fun getComment(): String? {
            return null
        }

        override fun type(): Statement.Type {
            return if (dependencies.isEmpty() && constraints.isEmpty()) Statement.Type.Empty else Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock("dependencies", this)
        }

        override fun writeBodyTo(printer: PrettyPrinter) {
            if (!this.constraints.isEmpty()) {
                val constraintsBlock = ScriptBlockImpl()
                for (config in this.constraints.keySet()) {
                    for (constraintSpec in this.constraints.get(config)) {
                        constraintsBlock.add(constraintSpec)
                    }
                }
                printer.printBlock("constraints", constraintsBlock)
            }

            for (config in dependencies.keySet()) {
                for (depSpec in dependencies.get(config)) {
                    printer.printStatement(depSpec)
                }
            }
        }

        override fun getStatements(): MutableList<Statement> {
            val statements: MutableList<Statement> = ArrayList<Statement>()
            if (!constraints.isEmpty()) {
                val constraintsBlock = BuildScriptBuilder.ScriptBlock(null, "constraints")
                for (config in constraints.keySet()) {
                    for (statement in constraints.get(config)) {
                        constraintsBlock.add(statement)
                    }
                }
                statements.add(constraintsBlock)
            }

            for (config in dependencies.keySet()) {
                statements.addAll(dependencies.get(config))
            }
            return statements
        }
    }

    private class TestingBlock(private val builder: BuildScriptBuilder) : BlockStatement("testing"), TestingBuilder, BlockBody {
        private val suites: MutableList<SuiteSpec> = ArrayList<SuiteSpec>()

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(blockSelector, this)
        }

        override fun writeBodyTo(printer: PrettyPrinter) {
            if (!suites.isEmpty()) {
                val suitesBlock = ScriptBlockImpl()
                for (suite in suites) {
                    suitesBlock.add(suite)
                }
                printer.printBlock("suites", suitesBlock)
            }
        }

        override fun getStatements(): MutableList<Statement> {
            return ArrayList<Statement>(suites)
        }

        override fun junitSuite(name: String, libraryVersionProvider: TemplateLibraryVersionProvider): SuiteSpec {
            val spec = SuiteSpec(null, name, SuiteSpec.TestSuiteFramework.JUNIT, libraryVersionProvider.getVersion("junit"), builder)
            suites.add(spec)
            return spec
        }

        override fun junitJupiterSuite(name: String, libraryVersionProvider: TemplateLibraryVersionProvider): SuiteSpec {
            val spec = SuiteSpec(null, name, SuiteSpec.TestSuiteFramework.JUNIT_PLATFORM, libraryVersionProvider.getVersion("junit-jupiter"), builder)
            suites.add(spec)
            return spec
        }

        override fun spockSuite(name: String, libraryVersionProvider: TemplateLibraryVersionProvider): SuiteSpec {
            val spec = SuiteSpec(null, name, SuiteSpec.TestSuiteFramework.SPOCK, libraryVersionProvider.getVersion("spock"), builder)
            suites.add(spec)
            return spec
        }

        override fun kotlinTestSuite(name: String, libraryVersionProvider: TemplateLibraryVersionProvider): SuiteSpec {
            val spec = SuiteSpec(null, name, SuiteSpec.TestSuiteFramework.KOTLIN_TEST, libraryVersionProvider.getVersion("kotlin"), builder)
            suites.add(spec)
            return spec
        }

        override fun testNG(name: String, libraryVersionProvider: TemplateLibraryVersionProvider): SuiteSpec {
            val spec = SuiteSpec(null, name, SuiteSpec.TestSuiteFramework.TEST_NG, libraryVersionProvider.getVersion("testng"), builder)
            suites.add(spec)
            return spec
        }
    }

    class SuiteSpec internal constructor(comment: String?, val name: String, private val framework: TestSuiteFramework, private val frameworkVersion: String, private val builder: BuildScriptBuilder) :
        AbstractStatement(comment) {
        private val dependencies: DependenciesBlock
        private val targets: TargetsBlock

        val isDefaultTestSuite: Boolean
        private val isDefaultFramework: Boolean

        init {
            targets = TargetsBlock(builder)
            this.dependencies = DependenciesBlock(builder)

            isDefaultTestSuite = JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME == name
            isDefaultFramework = framework == TestSuiteFramework.Companion.default

            if (!isDefaultTestSuite) {
                dependencies.selfDependency("implementation", name + " test suite depends on the production code in tests")
                targets.all(true)
            }
        }

        private fun buildSuiteConfigurationContents(): Action<in ScriptBlockBuilder> {
            return Action { b: ScriptBlockBuilder ->
                if (isDefaultTestSuite || !isDefaultFramework) {
                    if (frameworkVersion == null) {
                        b.methodInvocation("Use " + framework.displayName + " test framework", framework.method.methodName)
                    } else {
                        b.methodInvocation("Use " + framework.displayName + " test framework", framework.method.methodName, frameworkVersion)
                    }
                }
                if (!dependencies.dependencies.isEmpty()) {
                    b.statement(null, dependencies)
                }
                if (!targets.targets.isEmpty()) {
                    b.statement(null, targets)
                }
            }
        }

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        fun implementation(comment: String, vararg dependencies: BuildInitDependency) {
            this.dependencies.dependency("implementation", comment, *dependencies)
        }

        fun runtimeOnly(comment: String, vararg dependencies: BuildInitDependency) {
            this.dependencies.dependency("runtimeOnly", comment, *dependencies)
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            if (isDefaultTestSuite) {
                printer.printStatement(
                    builder.suiteConfiguration(
                        "Configure the built-in test suite",
                        builder.block.testing,
                        name,
                        JvmTestSuite::class.java.getSimpleName(),
                        buildSuiteConfigurationContents()
                    )
                )
            } else {
                printer.printStatement(builder.suiteRegistration("Create a new test suite", builder.block.testing, name, JvmTestSuite::class.java.getSimpleName(), buildSuiteConfigurationContents()))
            }
        }

        enum class TestSuiteFramework(//TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
            val method: MethodInvocationExpression, val displayName: String
        ) {
            JUNIT(MethodInvocationExpression("useJUnit"), "JUnit4"),
            JUNIT_PLATFORM(MethodInvocationExpression("useJUnitJupiter"), "JUnit Jupiter"),
            SPOCK(MethodInvocationExpression("useSpock"), "Spock"),
            KOTLIN_TEST(MethodInvocationExpression("useKotlinTest"), "Kotlin Test"),
            TEST_NG(MethodInvocationExpression("useTestNG"), "TestNG");

            companion object {
                val default: TestSuiteFramework
                    get() = TestSuiteFramework.JUNIT_PLATFORM
            }
        }
    }

    private class TargetsBlock(private val builder: BuildScriptBuilder) : BlockStatement("targets"), TargetsBuilder, BlockBody {
        private val targets: MutableList<TargetSpec> = ArrayList<TargetSpec>()

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(blockSelector, this)
        }

        override fun writeBodyTo(printer: PrettyPrinter) {
            if (!targets.isEmpty()) {
                for (target in targets) {
                    printer.printStatement(target)
                }
            }
        }

        override fun getStatements(): MutableList<Statement> {
            return ArrayList<Statement>(targets)
        }

        override fun all(testTaskShouldRunAfter: Boolean) {
            targets.add(TargetSpec(null, "all", builder, testTaskShouldRunAfter))
        }
    }

    private class TargetSpec(comment: String?, private val name: String, private val builder: BuildScriptBuilder, testTaskShouldRunAfter: Boolean) : BlockStatement(comment!!), BlockBody {
        init {
            if (testTaskShouldRunAfter) {
                configureShouldRunAfterTest()
            }
        }

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(name, this)
        }

        fun configureShouldRunAfterTest() {
            val shouldRunAfterCall = BuildScriptBuilder.MethodInvocation(null, MethodInvocationExpression(null, "shouldRunAfter", mutableListOf<ExpressionValue>(LiteralValue("test"))))
            val configBlock = NoArgClosureExpression(shouldRunAfterCall)
            val functionalTestConfiguration = MethodInvocation(
                "This test suite should run after the built-in test suite has run its tests",
                MethodInvocationExpression(expressionValue(builder.propertyExpression("testTask")), "configure", configBlock)
            )
            add(functionalTestConfiguration)
        }

        override fun writeBodyTo(printer: PrettyPrinter) {
            for (statement in body.statements) {
                printer.printStatement(statement)
            }
        }

        override fun getStatements(): MutableList<Statement> {
            return body.statements
        }
    }

    private class MavenRepoExpression(comment: String?, url: String, private val builder: BuildScriptBuilder) : AbstractStatement(comment) {
        private val uri: URI

        init {
            this.uri = uriFromString(url)
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            builder.mavenRepoURLHandler.handleURL(uri, printer)
        }
    }

    open class ScriptBlockImpl : ScriptBlockBuilder, BlockBody {
        val statements: MutableList<Statement> = ArrayList<Statement>()

        fun add(statement: Statement) {
            statements.add(statement)
        }

        override fun getStatements(): MutableList<Statement> {
            return statements
        }

        fun type(): Statement.Type {
            for (statement in statements) {
                if (statement.type() != Statement.Type.Empty) {
                    return Statement.Type.Group
                }
            }
            return Statement.Type.Empty
        }

        override fun writeBodyTo(printer: PrettyPrinter) {
            printer.printStatements(statements)
        }

        override fun propertyAssignment(comment: String, propertyName: String, propertyValue: Any, assignOperator: Boolean) {
            statements.add(PropertyAssignment(comment, propertyName, expressionValue(propertyValue), assignOperator))
        }

        override fun methodInvocation(comment: String, methodName: String, vararg methodArgs: Any) {
            statements.add(MethodInvocation(comment, MethodInvocationExpression(null, methodName, expressionValues(*methodArgs))))
        }

        override fun methodInvocation(comment: String?, target: Expression, methodName: String, vararg methodArgs: Any) {
            statements.add(BuildScriptBuilder.MethodInvocation(comment!!, MethodInvocationExpression(expressionValue(target), methodName, expressionValues(*methodArgs))))
        }

        override fun block(comment: String, methodName: String): ScriptBlockBuilder {
            val scriptBlock = ScriptBlock(comment, methodName)
            statements.add(scriptBlock)
            return scriptBlock.body
        }

        override fun statement(comment: String?, statement: Statement) {
            statements.add(statement)
        }

        override fun block(comment: String?, methodName: String, blockContentsBuilder: Action<in ScriptBlockBuilder>) {
            blockContentsBuilder.execute(block(comment, methodName))
        }

        override fun containerElement(comment: String?, container: String, elementName: String, elementType: String?, blockContentsBuilder: Action<in ScriptBlockBuilder>): Expression {
            val containerElement = BuildScriptBuilder.ContainerElement(comment!!, container, elementName, elementType, null)
            statements.add(containerElement)
            blockContentsBuilder.execute(containerElement.body)
            return containerElement
        }

        override fun propertyExpression(value: String): Expression {
            return LiteralValue(value)
        }

        override fun comment(comment: String) {
            statements.add(SingleLineComment(comment))
        }
    }

    private class TopLevelBlock(val builder: BuildScriptBuilder) : ScriptBlockImpl() {
        val pluginsManagement: BlockStatement = BlockStatement(InitialPassStatementTransformer.PLUGIN_MANAGEMENT)
        val plugins: BlockStatement = BlockStatement(InitialPassStatementTransformer.PLUGINS)
        val dependencyResolutionManagement: BlockStatement = BlockStatement("dependencyResolutionManagement")
        val repositories: RepositoriesBlock
        val dependencies: DependenciesBlock
        val testing: TestingBlock
        val taskTypes: ConfigurationStatements<TaskTypeSelector> = ConfigurationStatements<TaskTypeSelector>()
        val tasks: ConfigurationStatements<TaskSelector> = ConfigurationStatements<TaskSelector>()

        init {
            repositories = RepositoriesBlock(builder)
            testing = TestingBlock(builder)
            this.dependencies = DependenciesBlock(builder)
        }

        override fun writeBodyTo(printer: PrettyPrinter) {
            printer.printStatement(pluginsManagement)
            printer.printStatement(plugins)
            printer.printStatement(dependencyResolutionManagement)
            printer.printStatement(repositories)
            printer.printStatement(dependencies)
            if (builder.isUsingTestSuites && !builder.suites.isEmpty()) {
                printer.printStatement(testing)
            }
            super.writeBodyTo(printer)
            printer.printStatement(taskTypes)
            for (suite in testing.suites) {
                if (!suite.isDefaultTestSuite) {
                    addCheckDependsOn(suite)
                }
            }
            printer.printStatement(tasks)
        }

        fun addCheckDependsOn(suite: SuiteSpec) {
            val testSuites: ExpressionValue = expressionValue(builder.propertyExpression(builder.propertyExpression("testing"), "suites"))
            if (builder.dsl == BuildInitDsl.GROOVY) {
                val suiteDependedUpon = builder.propertyExpression(testSuites, suite.name)
                builder.taskMethodInvocation("Include " + suite.name + " as part of the check lifecycle", "check", Task::class.java.getSimpleName(), "dependsOn", suiteDependedUpon)
            } else {
                val namedMethod: ExpressionValue = MethodInvocationExpression(
                    testSuites, "named", mutableListOf<ExpressionValue>(
                        StringValue(
                            suite.name
                        )
                    )
                )
                builder.taskMethodInvocation("Include " + suite.name + " as part of the check lifecycle", "check", Task::class.java.getSimpleName(), "dependsOn", namedMethod)
            }
        }

        fun extractComments(): MutableList<String> {
            val comments: MutableList<String> = ArrayList<String>()
            collectComments(plugins.body.getStatements(), comments)
            collectComments(repositories.body.getStatements(), comments)
            collectComments(dependencies.getStatements(), comments)
            for (otherBlock in getStatements()) {
                if (otherBlock is BlockStatement) {
                    collectComments(otherBlock.body.getStatements(), comments)
                }
            }
            collectComments(tasks.blocks.values(), comments)
            return comments
        }

        fun collectComments(statements: MutableCollection<Statement>, comments: MutableList<String>) {
            for (statement in statements) {
                if (statement.comment != null) {
                    comments.add(statement.comment!!)
                }
            }
        }

        fun includePluginsBuild() {
            pluginsManagement.add(
                MethodInvocation(
                    "Include 'plugins build' to define convention plugins.",
                    MethodInvocationExpression(null, "includeBuild", expressionValues(SimpleGlobalFilesBuildSettingsDescriptor.Companion.PLUGINS_BUILD_LOCATION))
                )
            )
        }

        fun useVersionCatalogFromOuterBuild(comment: String) {
            val vc = BlockStatement(comment, "versionCatalogs")
            vc.body.add(
                BuildScriptBuilder.MethodInvocation(
                    null,
                    MethodInvocationExpression(null, "create", expressionValues("libs", LiteralValue("{ from(files(\"../gradle/libs.versions.toml\")) }")))
                )
            )
            dependencyResolutionManagement.add(vc)
        }
    }

    private class TaskConfiguration(val comment: String?, val taskName: String, val taskType: String) : Statement, ExpressionValue {
        val body: ScriptBlockImpl = ScriptBlockImpl()

        override fun getComment(): String? {
            return comment
        }

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(printer.syntax.taskConfiguration(taskName, taskType)!!, body)
        }

        override fun with(syntax: Syntax): String {
            return syntax.referenceTask(taskName)
        }
    }

    private class TaskRegistration(val comment: String?, val taskName: String, val taskType: String) : Statement, ExpressionValue {
        val body: ScriptBlockImpl = ScriptBlockImpl()

        override fun getComment(): String? {
            return comment
        }

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(printer.syntax.taskRegistration(taskName, taskType)!!, body)
        }

        override fun with(syntax: Syntax): String {
            return syntax.referenceTask(taskName)
        }
    }

    private class SuiteConfiguration(val comment: String?, val suiteName: String, val suiteType: String) : Statement, ExpressionValue {
        val body: ScriptBlockImpl = ScriptBlockImpl()

        override fun getComment(): String? {
            return comment
        }

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(printer.syntax.suiteConfiguration(suiteName, suiteType)!!, body)
        }

        override fun with(syntax: Syntax): String {
            return syntax.referenceSuite(suiteName)
        }
    }

    private class SuiteRegistration(val comment: String?, val suiteName: String, val suiteType: String) : Statement, ExpressionValue {
        val body: ScriptBlockImpl = ScriptBlockImpl()

        override fun getComment(): String? {
            return comment
        }

        override fun type(): Statement.Type {
            return Statement.Type.Group
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            printer.printBlock(printer.syntax.suiteRegistration(suiteName, suiteType)!!, body)
        }

        override fun with(syntax: Syntax): String {
            return syntax.referenceSuite(suiteName)
        }
    }

    private class ConfigurationStatements<T : ConfigSelector?> : Statement {
        val blocks: ListMultimap<T?, Statement> = MultimapBuilder.linkedHashKeys().arrayListValues().build<T?, Statement>()

        fun add(selector: T?, statement: Statement) {
            blocks.put(selector, statement)
        }

        override fun getComment(): String? {
            return null
        }

        override fun type(): Statement.Type {
            return if (blocks.isEmpty()) Statement.Type.Empty else Statement.Type.Single
        }

        override fun writeCodeTo(printer: PrettyPrinter) {
            for (configSelector in blocks.keySet()) {
                val selector = configSelector!!.codeBlockSelectorFor(printer.syntax)
                if (selector != null) {
                    val statement = BlockStatement(selector)
                    statement.body.statements.addAll(blocks.get(configSelector))
                    printer.printStatement(statement)
                } else {
                    printer.printStatements(blocks.get(configSelector))
                }
            }
        }
    }

    private class PrettyPrinter(private val syntax: Syntax, private val writer: PrintWriter, private val comments: BuildInitComments) {
        private var indent = ""
        private var eolComment: String? = null
        private var commentCount = 0
        private var needSeparatorLine = false
        private var firstStatementOfBlock = true
        private var hasSeparatorLine = false

        fun printFileHeader(lines: MutableCollection<String>) {
            if (comments != BuildInitComments.ON) {
                return
            }

            println("/*")
            println(" * This file was generated by the Gradle 'init' task.")
            if (!lines.isEmpty()) {
                println(" *")
                for (headerLine in lines) {
                    if (headerLine.isEmpty()) {
                        println(" *")
                    } else {
                        println(" * " + headerLine)
                    }
                }
            }
            println(" */")

            firstStatementOfBlock = false
            needSeparatorLine = true
        }

        fun printBlock(blockSelector: String, blockBody: BlockBody) {
            val indentBefore = indent

            println(blockSelector + " {")
            indent = indent + "    "
            needSeparatorLine = false
            firstStatementOfBlock = true

            blockBody.writeBodyTo(this)

            indent = indentBefore
            println("}")

            // Write a line separator after any block
            needSeparatorLine = true
        }

        fun printStatements(statements: MutableList<out Statement>) {
            for (statement in statements) {
                printStatement(statement)
            }
        }

        fun printStatementSeparator() {
            if (needSeparatorLine && !hasSeparatorLine) {
                println()
                needSeparatorLine = false
            }
        }

        fun printStatement(statement: Statement) {
            val type = statement.type()
            if (type == Statement.Type.Empty) {
                return
            }

            val hasComment = statement.comment != null

            // Add separators before and after anything with a comment or that is a block or group of statements
            val needsSeparator = type == Statement.Type.Group || (hasComment && comments == BuildInitComments.ON)
            if (needsSeparator && !firstStatementOfBlock) {
                needSeparatorLine = true
            }

            printStatementSeparator()

            if (hasComment) {
                when (comments) {
                    BuildInitComments.ON -> for (line in Companion.splitComment(statement.comment!!)) {
                        println("// " + line)
                    }

                    BuildInitComments.OFF -> {}
                    BuildInitComments.EXTERNAL -> {
                        commentCount++
                        eolComment = " // <" + commentCount + ">"
                    }
                }
            }

            statement.writeCodeTo(this)

            firstStatementOfBlock = false
            if (needsSeparator) {
                needSeparatorLine = true
            }
        }

        fun println(s: String) {
            if (!indent.isEmpty()) {
                writer.print(indent)
            }
            if (eolComment != null) {
                writer.println(s + eolComment)
                eolComment = null
            } else {
                writer.println(s)
            }
            hasSeparatorLine = false
        }

        fun println() {
            writer.println()
            hasSeparatorLine = true
        }
    }

    private interface Syntax {
        fun pluginDependencySpec(pluginId: String, version: String?): String?

        @Suppress("unused")
        fun nestedPluginDependencySpec(pluginId: String, version: String?): String?

        fun pluginAliasSpec(alias: String): String?

        fun dependencySpec(config: String, notation: String): String?

        fun complexDependencySpec(config: String, notation: String): String?

        fun propertyAssignment(expression: PropertyAssignment): String?

        fun taskSelector(selector: TaskSelector): String?

        fun taskByTypeSelector(taskType: String): String?

        fun string(string: String): String

        fun taskRegistration(taskName: String, taskType: String): String?

        fun taskConfiguration(taskName: String, taskType: String): String?

        fun suiteRegistration(taskName: String, taskType: String): String?

        fun suiteConfiguration(taskName: String, taskType: String): String?

        fun referenceTask(taskName: String): String

        fun referenceSuite(taskName: String): String

        fun mapLiteral(map: MutableMap<String, ExpressionValue>): String

        fun firstArg(argument: ExpressionValue): String?

        fun createContainerElement(comment: String?, container: String, elementName: String, elementType: String?, varName: String?, body: MutableList<Statement>): Statement

        fun referenceCreatedContainerElement(container: String, elementName: String, varName: String?): String

        fun containerElement(container: String, element: String): String

        fun configureConventionPlugin(comment: String?, plugins: BlockStatement, repositories: RepositoriesBlock)
    }

    private class KotlinSyntax : Syntax {
        override fun string(string: String): String {
            return '"'.toString() + escapeKotlinStringLiteral(string) + '"'
        }

        fun escapeKotlinStringLiteral(string: String): String {
            return string
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
        }

        override fun mapLiteral(map: MutableMap<String, ExpressionValue>): String {
            val builder = StringBuilder()
            builder.append("mapOf(")
            var first = true
            for (entry in map.entries) {
                if (first) {
                    first = false
                } else {
                    builder.append(", ")
                }
                builder.append(string(entry.key))
                builder.append(" to ")
                builder.append(entry.value.with(this))
            }
            builder.append(")")
            return builder.toString()
        }

        override fun firstArg(argument: ExpressionValue): String {
            return argument.with(this)
        }

        override fun pluginDependencySpec(pluginId: String, version: String?): String {
            if (version != null) {
                return "id(\"" + pluginId + "\") version \"" + version + "\""
            } else if (pluginId.contains(".")) {
                return "id(\"" + pluginId + "\")"
            }
            return if (pluginId.matches("[a-z]+".toRegex())) pluginId else "`" + pluginId + "`"
        }

        override fun nestedPluginDependencySpec(pluginId: String, version: String?): String {
            if (version != null) {
                throw UnsupportedOperationException()
            }
            return "plugins.apply(\"" + pluginId + "\")"
        }

        override fun pluginAliasSpec(alias: String): String {
            return "alias(" + alias + ")"
        }

        override fun dependencySpec(config: String, notation: String): String {
            return config + "(" + notation + ")"
        }

        override fun complexDependencySpec(config: String, notation: String): String {
            return dependencySpec(config, notation)
        }

        override fun propertyAssignment(expression: PropertyAssignment): String {
            val propertyName = expression.propertyName
            val propertyValue = expression.propertyValue
            if (expression.assignOperator) {
                if (propertyValue.isBooleanType) {
                    return booleanPropertyNameFor(propertyName) + " = " + propertyValue.with(this)
                }
                return propertyName + " = " + propertyValue.with(this)
            } else {
                return propertyName + ".set(" + propertyValue.with(this) + ")"
            }
        }

        // In Kotlin:
        //
        // > Boolean accessor methods (where the name of the getter starts with is and the name of
        // > the setter starts with set) are represented as properties which have the same name as
        // > the getter method. Boolean properties are visible with a `is` prefix in Kotlin
        //
        // https://kotlinlang.org/docs/reference/java-interop.html#getters-and-setters
        //
        // This code assumes all configurable Boolean property getters follow the `is` prefix convention.
        //
        fun booleanPropertyNameFor(propertyName: String): String {
            return "is" + StringUtils.capitalize(propertyName)
        }

        override fun taskSelector(selector: TaskSelector): String {
            return "tasks.named<" + selector.taskType + ">(\"" + selector.taskName + "\")"
        }

        override fun taskByTypeSelector(taskType: String): String {
            return "tasks.withType<" + taskType + ">()"
        }

        override fun taskRegistration(taskName: String, taskType: String): String {
            return "val " + taskName + " = tasks.register<" + taskType + ">(" + string(taskName) + ")"
        }

        override fun taskConfiguration(taskName: String, taskType: String): String {
            return "val " + taskName + " = tasks.named<" + taskType + ">(" + string(taskName) + ")"
        }

        override fun suiteRegistration(suiteName: String, suiteType: String): String {
            return "val " + suiteName + " = register<" + suiteType + ">(" + string(suiteName) + ")"
        }

        override fun suiteConfiguration(suiteName: String, suiteType: String): String {
            return "val " + suiteName + " = named<" + suiteType + ">(" + string(suiteName) + ")"
        }

        override fun referenceTask(taskName: String): String {
            return taskName
        }

        override fun referenceSuite(suiteName: String): String {
            return suiteName
        }

        override fun createContainerElement(comment: String, container: String, elementName: String, elementType: String?, varName: String, body: MutableList<Statement>): Statement {
            val literal = getLiteral(container, elementName, elementType, varName)
            val blockStatement: BlockStatement = ScriptBlock(comment, literal)
            for (statement in body) {
                blockStatement.add(statement)
            }
            return blockStatement
        }

        fun getLiteral(container: String, elementName: String, elementType: String?, varName: String): String {
            if (varName == null) {
                if (elementType == null) {
                    return "val " + elementName + " = " + container + ".create(" + string(elementName) + ")"
                }
                return container + ".create<" + elementType + ">(" + string(elementName) + ")"
            }
            if (elementType == null) {
                return "val " + varName + " = " + container + ".create(" + string(elementName) + ")"
            }
            return "val " + varName + " = " + container + ".create<" + elementType + ">(" + string(elementName) + ")"
        }

        override fun referenceCreatedContainerElement(container: String, elementName: String, varName: String): String {
            if (varName == null) {
                return elementName
            } else {
                return varName
            }
        }

        override fun containerElement(container: String, element: String): String {
            return container + "[" + string(element) + "]"
        }

        override fun configureConventionPlugin(comment: String?, plugins: BlockStatement, repositories: RepositoriesBlock) {
            plugins.add(PluginSpec("kotlin-dsl", null, comment))
        }
    }

    private class GroovySyntax : Syntax {
        override fun string(string: String): String {
            return "'" + escapeGroovyStringLiteral(string) + "'"
        }

        fun escapeGroovyStringLiteral(string: String): String {
            return string.replace("\\", "\\\\").replace("'", "\\'")
        }

        override fun mapLiteral(map: MutableMap<String, ExpressionValue>): String {
            val builder = StringBuilder()
            builder.append("[")
            addEntries(map, builder)
            builder.append("]")
            return builder.toString()
        }

        fun addEntries(map: MutableMap<String, ExpressionValue>, builder: StringBuilder) {
            var first = true
            for (entry in map.entries) {
                if (first) {
                    first = false
                } else {
                    builder.append(", ")
                }
                builder.append(entry.key)
                builder.append(": ")
                builder.append(entry.value.with(this))
            }
        }

        override fun firstArg(argument: ExpressionValue): String {
            if (argument is MapLiteralValue) {
                val literalValue = argument
                val builder = StringBuilder()
                addEntries(literalValue.literal, builder)
                return builder.toString()
            } else {
                return argument.with(this)
            }
        }

        override fun pluginDependencySpec(pluginId: String, version: String?): String {
            if (version != null) {
                return "id '" + pluginId + "' version '" + version + "'"
            }
            return "id '" + pluginId + "'"
        }

        override fun nestedPluginDependencySpec(pluginId: String, version: String?): String {
            if (version != null) {
                throw UnsupportedOperationException()
            }
            return "apply plugin: '" + pluginId + "'"
        }

        override fun pluginAliasSpec(alias: String): String {
            return "alias(" + alias + ")"
        }

        override fun dependencySpec(config: String, notation: String): String {
            return config + " " + notation
        }

        override fun complexDependencySpec(config: String, notation: String): String {
            return config + "(" + notation + ")"
        }

        override fun propertyAssignment(expression: PropertyAssignment): String {
            val propertyName = expression.propertyName
            val propertyValue = expression.propertyValue
            return propertyName + " = " + propertyValue.with(this)
        }

        override fun taskSelector(selector: TaskSelector): String {
            return "tasks.named('" + selector.taskName + "')"
        }

        override fun taskByTypeSelector(taskType: String): String {
            return "tasks.withType(" + taskType + ")"
        }

        override fun taskRegistration(taskName: String, taskType: String): String {
            return "tasks.register('" + taskName + "', " + taskType + ")"
        }

        override fun taskConfiguration(taskName: String, taskType: String): String {
            return taskName
        }

        override fun suiteRegistration(suiteName: String, suiteType: String): String {
            return suiteName + "(" + suiteType + ")"
        }

        override fun suiteConfiguration(suiteName: String, suiteType: String): String {
            return suiteName
        }

        override fun referenceTask(taskName: String): String {
            return "tasks." + taskName
        }

        override fun referenceSuite(suiteName: String): String {
            return suiteName
        }

        override fun createContainerElement(comment: String, container: String, elementName: String, elementType: String?, varName: String, body: MutableList<Statement>): Statement {
            val outerBlock = ScriptBlock(comment, container)
            val innerBlock = BuildScriptBuilder.ScriptBlock(null, if (elementType == null) elementName else elementName + "(" + elementType + ")")
            outerBlock.add(innerBlock)
            for (statement in body) {
                innerBlock.add(statement)
            }
            return outerBlock
        }

        override fun referenceCreatedContainerElement(container: String, elementName: String, varName: String): String {
            return container + "." + elementName
        }

        override fun containerElement(container: String, element: String): String {
            return container + "." + element
        }

        override fun configureConventionPlugin(comment: String?, plugins: BlockStatement, repositories: RepositoriesBlock) {
            plugins.add(PluginSpec("groovy-gradle-plugin", null, comment))
        }
    }

    private interface MavenRepositoryURLHandler {
        fun handleURL(repoLocation: URI, printer: PrettyPrinter)

        class AbstractMavenRepositoryURLHandler : MavenRepositoryURLHandler {
            override fun handleURL(repoLocation: URI, printer: PrettyPrinter) {
                val statements = ScriptBlockImpl()

                if (GUtil.isSecureUrl(repoLocation)) {
                    handleSecureURL(repoLocation, statements)
                } else {
                    handleInsecureURL(repoLocation, statements)
                }

                printer.printBlock("maven", statements)
            }

            protected fun handleSecureURL(repoLocation: URI, statements: ScriptBlockImpl) {
                statements.propertyAssignment(null, "url", MethodInvocationExpression(null, "uri", mutableListOf<ExpressionValue>(StringValue(repoLocation.toString()))), true)
            }

            protected abstract fun handleInsecureURL(repoLocation: URI, statements: ScriptBlockImpl)
        }

        class FailingHandler(private val documentationRegistry: DocumentationRegistry) : AbstractMavenRepositoryURLHandler() {
            override fun handleInsecureURL(repoLocation: URI, statements: ScriptBlockImpl) {
                LOGGER.error(
                    "Gradle found an insecure protocol in a repository definition. The current strategy for handling insecure URLs is to fail. {}",
                    documentationRegistry.getDocumentationRecommendationFor("options", "build_init_plugin", "sec:allow_insecure")
                )
                throw GradleException(String.format("Build generation aborted due to insecure protocol in repository: %s", repoLocation))
            }
        }

        class WarningHandler(private val dsl: BuildInitDsl, private val documentationRegistry: DocumentationRegistry) : AbstractMavenRepositoryURLHandler() {
            override fun handleInsecureURL(repoLocation: URI, statements: ScriptBlockImpl) {
                LOGGER.warn(
                    "Gradle found an insecure protocol in a repository definition. You will have to opt into allowing insecure protocols in the generated build file. {}",
                    documentationRegistry.getDocumentationRecommendationFor("information on how to do this", "build_init_plugin", "sec:allow_insecure")
                )
                // use the insecure URL as-is
                statements.propertyAssignment(null, "url", MethodInvocationExpression(null, "uri", mutableListOf<ExpressionValue>(StringValue(repoLocation.toString()))), true)
                // Leave a commented out block for opting into using the insecure repository
                statements.comment(buildAllowInsecureProtocolComment(dsl))
            }

            private fun buildAllowInsecureProtocolComment(dsl: BuildInitDsl): String {
                val assignment = BuildScriptBuilder.PropertyAssignment(null, "allowInsecureProtocol", LiteralValue(true), true)

                val result = StringWriter()
                try {
                    PrintWriter(result).use { writer ->
                        val printer = PrettyPrinter(syntaxFor(dsl), writer, BuildInitComments.OFF)
                        assignment.writeCodeTo(printer)
                        return result.toString()
                    }
                } catch (e: Exception) {
                    throw GradleException("Could not write comment.", e)
                }
            }
        }

        class UpgradingHandler : AbstractMavenRepositoryURLHandler() {
            override fun handleInsecureURL(repoLocation: URI, statements: ScriptBlockImpl) {
                // convert the insecure url for this repository from http to https
                val secureUri = GUtil.toSecureUrl(repoLocation)
                statements.propertyAssignment(null, "url", MethodInvocationExpression(null, "uri", mutableListOf<ExpressionValue>(StringValue(secureUri.toString()))), true)
            }
        }

        class AllowingHandler : AbstractMavenRepositoryURLHandler() {
            override fun handleInsecureURL(repoLocation: URI, statements: ScriptBlockImpl) {
                // use the insecure URL as-is
                statements.propertyAssignment(null, "url", MethodInvocationExpression(null, "uri", mutableListOf<ExpressionValue>(StringValue(repoLocation.toString()))), true)
                // Opt into using an insecure protocol with this repository
                statements.propertyAssignment(null, "allowInsecureProtocol", LiteralValue(true), true)
            }
        }

        companion object {
            fun forInsecureProtocolOption(insecureProtocolOption: InsecureProtocolOption, dsl: BuildInitDsl, documentationRegistry: DocumentationRegistry): MavenRepositoryURLHandler {
                when (insecureProtocolOption) {
                    InsecureProtocolOption.FAIL -> return FailingHandler(documentationRegistry)
                    InsecureProtocolOption.WARN -> return WarningHandler(dsl, documentationRegistry)
                    InsecureProtocolOption.ALLOW -> return AllowingHandler()
                    InsecureProtocolOption.UPGRADE -> return UpgradingHandler()
                    else -> throw IllegalStateException(String.format("Unknown handler: '%s'.", insecureProtocolOption))
                }
            }
        }
    }

    companion object {
        const val incubatingApisWarning: String = "This project uses @Incubating APIs which are subject to change."

        private val LOGGER: Logger = LoggerFactory.getLogger(BuildScriptBuilder::class.java)

        private fun splitComment(comment: String): MutableList<String> {
            return Splitter.on("\n").splitToList(comment.trim { it <= ' ' })
        }

        private fun uriFromString(uriAsString: String): URI {
            try {
                return URI(uriAsString)
            } catch (e: URISyntaxException) {
                throw throwAsUncheckedException(e)
            }
        }

        private fun expressionValues(vararg expressions: Any): MutableList<ExpressionValue> {
            val result: MutableList<ExpressionValue> = ArrayList<ExpressionValue>(expressions.size)
            for (expression in expressions) {
                result.add(expressionValue(expression))
            }
            return result
        }

        private fun expressionMap(expressions: MutableMap<String, *>): MutableMap<String, ExpressionValue> {
            val result = LinkedHashMap<String, ExpressionValue>()
            for (entry in expressions.entries) {
                result.put(entry.key, Companion.expressionValue(entry.value!!))
            }
            return result
        }

        private fun expressionValue(expression: Any): ExpressionValue {
            if (expression is CharSequence) {
                return StringValue(expression)
            }
            if (expression is ExpressionValue) {
                return expression
            }
            if (expression is Number || expression is Boolean) {
                return LiteralValue(expression)
            }
            if (expression is MutableMap<*, *>) {
                return MapLiteralValue(Companion.expressionMap(uncheckedNonnullCast<MutableMap<String, *>?>(expression)!!))
            }
            if (expression is Enum<*>) {
                return EnumValue(expression)
            }
            throw IllegalArgumentException("Don't know how to treat " + expression + " as an expression.")
        }

        private fun syntaxFor(dsl: BuildInitDsl): Syntax {
            when (dsl) {
                BuildInitDsl.KOTLIN -> return KotlinSyntax()
                BuildInitDsl.GROOVY -> return GroovySyntax()
                else -> throw IllegalStateException()
            }
        }
    }
}

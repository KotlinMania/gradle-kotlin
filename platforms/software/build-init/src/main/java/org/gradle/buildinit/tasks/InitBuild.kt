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
package org.gradle.buildinit.tasks

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.internal.tasks.userinput.NonInteractiveUserInputHandler
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.api.internal.tasks.userinput.UserQuestions
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import org.gradle.api.tasks.wrapper.internal.WrapperDefaults
import org.gradle.api.tasks.wrapper.internal.WrapperGenerator
import org.gradle.buildinit.plugins.internal.BuildGenerator
import org.gradle.buildinit.plugins.internal.BuildInitException
import org.gradle.buildinit.plugins.internal.BuildInitializer
import org.gradle.buildinit.plugins.internal.GenerationSettings
import org.gradle.buildinit.plugins.internal.InitSettings
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.buildinit.specs.BuildInitConfig
import org.gradle.buildinit.specs.BuildInitGenerator
import org.gradle.buildinit.specs.BuildInitParameter
import org.gradle.buildinit.specs.BuildInitSpec
import org.gradle.buildinit.specs.internal.BuildInitSpecRegistry
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import org.gradle.nativeplatform.platform.Architecture.getName
import org.gradle.util.GradleVersion
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import javax.inject.Inject
import javax.lang.model.SourceVersion

/**
 * Generates a Gradle project structure.
 */
@DisableCachingByDefault(because = "Not worth caching")
@UntrackedTask(because = "This task will never be up-to-date")
abstract class InitBuild : DefaultTask() {
    private var type: String? = null
    private var dsl: String? = null
    /**
     * The test framework to be used in the generated project.
     *
     * This property can be set via command-line option '--test-framework'
     */
    /**
     * Set the test framework to be used.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    @set:Option(option = "test-framework", description = "Set the test framework to be used.")
    var testFramework: String? = null
    private var projectName: String? = null
    private var packageName: String? = null

    @get:NotToBeReplacedByLazyProperty(because = "Injected service")
    @Internal
    var projectLayoutRegistry: ProjectLayoutSetupRegistry? = null
        get() {
            if (field == null) {
                field = getServices().get<ProjectLayoutSetupRegistry?>(ProjectLayoutSetupRegistry::class.java)
            }

            return field
        }

    @get:Option(option = "use-defaults", description = "Use default values for options not configured explicitly")
    @get:Optional
    @get:Input
    @get:Incubating
    abstract val useDefaults: Property<Boolean?>?

    @get:Option(option = "overwrite", description = "Allow existing files in the build directory to be overwritten?")
    @get:Optional
    @get:Input
    @get:Incubating
    abstract val allowFileOverwrite: Property<Boolean?>?

    /**
     * The desired type of project to generate, such as 'java-application' or 'kotlin-library'.
     *
     *
     * This property can be set via command-line option '--type'.
     *
     *
     * Defaults to 'basic' - a minimal scaffolding, following Gradle best practices.
     * If a `pom.xml` is found in the project root directory, the type defaults to 'pom'
     * and the existing project is converted to Gradle.
     *
     *
     * Possible values for the option are provided by [.getAvailableBuildTypes].
     */
    @Input
    @ToBeReplacedByLazyProperty
    fun getType(): String {
        return (if (com.google.common.base.Strings.isNullOrEmpty(type)) detectType() else type)!!
    }

    @get:Option(option = "split-project", description = "Split functionality across multiple subprojects?")
    @get:Optional
    @get:Input
    abstract val splitProject: Property<Boolean?>?

    /**
     * The desired DSL of build scripts to create, defaults to 'kotlin'.
     *
     * This property can be set via command-line option '--dsl'.
     *
     * @since 4.5
     */
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    fun getDsl(): String {
        return (if (com.google.common.base.Strings.isNullOrEmpty(dsl)) org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN.getId() else dsl)!!
    }

    @get:Option(option = "incubating", description = "Allow the generated build to use new features and APIs.")
    @get:Optional
    @get:Input
    abstract val useIncubating: Property<Boolean?>?

    @get:Option(option = "java-version", description = "Provides java version to use in the project.")
    @get:Incubating
    @get:Optional
    @get:Input
    abstract val javaVersion: Property<String?>?

    @get:Option(option = "into", description = "Set the directory where the project is generated.")
    @get:Incubating
    @get:Internal("Task outcome is decided early and should not rely on comparing file trees")
    abstract val projectDirectory: DirectoryProperty?

    /**
     * The name of the generated project, defaults to the name of the directory the project is generated in.
     *
     * This property can be set via command-line option '--project-name'.
     *
     * @since 5.0
     */
    @Input
    @ToBeReplacedByLazyProperty
    fun getProjectName(): String {
        return (if (projectName == null) this.projectDirectory.get().getAsFile().getName() else projectName)!!
    }

    /**
     * The name of the package to use for generated source.
     *
     * This property can be set via command-line option '--package'.
     *
     * @since 5.0
     */
    @Input
    @ToBeReplacedByLazyProperty
    fun getPackageName(): String {
        return (if (packageName == null) "" else packageName)!!
    }

    @get:Option(option = "insecure-protocol", description = "How to handle insecure URLs used for Maven Repositories.")
    @get:Input
    abstract val insecureProtocol: Property<InsecureProtocolOption?>?

    @get:Option(option = "comments", description = "Include clarifying comments in files.")
    @get:Optional
    @get:Input
    @get:Incubating
    abstract val comments: Property<Boolean?>?

    @TaskAction
    fun setupProjectLayout() {
        val inputHandler = this.effectiveInputHandler
        if (shouldUseInitProjectSpec(inputHandler)) {
            doInitSpecProjectGeneration(inputHandler)
        } else {
            doStandardProjectGeneration(inputHandler)
        }
    }

    private fun shouldUseInitProjectSpec(inputHandler: UserInputHandler): Boolean {
        val templatesAvailable = !this.buildInitSpecRegistry.isEmpty()
        return templatesAvailable && inputHandler.askUser<Boolean?>(Function { uq: UserQuestions? ->
            uq!!.askBooleanQuestion(
                "Additional project types were loaded.  Do you want to generate a project using a contributed project specification?",
                true
            )
        }).get()
    }

    private fun doInitSpecProjectGeneration(inputHandler: UserInputHandler) {
        val config = inputHandler.askUser<BuildInitConfig>(Function { userQuestions: UserQuestions? -> this.selectAndConfigureSpec(userQuestions!!) }).get()
        val generator = createGenerator(config)
        val userInterrupted = inputHandler.interrupted()
        if (userInterrupted) {
            throw BuildCancelledException()
        }
        getLogger().lifecycle("Generate '{}'", config.getBuildSpec().getDisplayName())
        val projectDirectory = this.projectDirectory.get()
        generator.generate(config, projectDirectory)
        generateWrapper(projectDirectory)
    }

    private fun selectAndConfigureSpec(userQuestions: UserQuestions): BuildInitConfig {
        val registry = this.buildInitSpecRegistry

        val spec: BuildInitSpec
        if (type == null) {
            spec = userQuestions.choice<BuildInitSpec>("Select project type", registry.getAllSpecs())
                .renderUsing(Function { obj: BuildInitSpec -> obj.getDisplayName() })
                .ask()
        } else {
            spec = registry.getSpecByType(type!!)
        }

        // TODO: Ask questions for each parameter, and return a configuration object with populated arguments
        return object : BuildInitConfig {
            override fun getBuildSpec(): BuildInitSpec {
                return spec
            }

            override fun getArguments(): MutableMap<BuildInitParameter<*>?, Any?> {
                return mutableMapOf<BuildInitParameter<*>?, Any?>()
            }
        }
    }

    private fun createGenerator(config: BuildInitConfig): BuildInitGenerator {
        val generator = this.buildInitSpecRegistry.getGeneratorForSpec(config.getBuildSpec())
        return this.objectFactory.newInstance(generator)
    }

    private fun doStandardProjectGeneration(inputHandler: UserInputHandler) {
        val settings = inputHandler.askUser<GenerationSettings>(Function { userQuestions: UserQuestions? -> this.calculateGenerationSettings(userQuestions!!) }).get()

        val userInterrupted = inputHandler.interrupted()
        if (userInterrupted) {
            throw BuildCancelledException()
        }

        settings.getInitializer().generate(settings.getSettings())
        generateWrapper(settings.getSettings().getTarget())

        settings.getInitializer().getFurtherReading(settings.getSettings())
            .ifPresent(Consumer { link: String? -> getLogger().lifecycle(link) })
    }

    private fun calculateGenerationSettings(userQuestions: UserQuestions): GenerationSettings {
        validateBuildDirectory(userQuestions)

        val projectLayoutRegistry = this.projectLayoutRegistry

        val initializer = getBuildInitializer(userQuestions, projectLayoutRegistry!!)

        val javaLanguageVersion = getJavaLanguageVersion(userQuestions, initializer)

        val projectName = getEffectiveProjectName(userQuestions, initializer)

        val modularizationOption = getModularizationOption(userQuestions, initializer)

        val dsl = getBuildInitDsl(userQuestions, initializer)

        val testFramework = getBuildInitTestFramework(userQuestions, initializer, modularizationOption)

        val packageName = getEffectivePackageName(initializer)

        validatePackageName(packageName)

        val useIncubatingAPIs = shouldUseIncubatingAPIs(userQuestions)
        val generateComments = this.comments.get()

        val subprojectNames: MutableList<String?> = initializer.getDefaultProjectNames()
        val initSettings = InitSettings(
            projectName,
            useIncubatingAPIs,
            subprojectNames,
            modularizationOption,
            dsl,
            packageName,
            testFramework,
            this.insecureProtocol.get(),
            this.projectDirectory.get(),
            javaLanguageVersion,
            generateComments
        )
        return GenerationSettings(initializer, initSettings)
    }

    private fun generateWrapper(projectDirectory: Directory) {
        val unixScript = projectDirectory.file(WrapperDefaults.SCRIPT_PATH).getAsFile()
        val jarFile = projectDirectory.file(WrapperDefaults.JAR_FILE_PATH).getAsFile()
        val jarFileRelativePath: String = getRelativePath(projectDirectory.getAsFile(), jarFile)
        val propertiesFile = WrapperGenerator.getPropertiesFile(jarFile)
        val distributionUrl = WrapperGenerator.getDistributionUrl(GradleVersion.current(), WrapperDefaults.DISTRIBUTION_TYPE)
        WrapperGenerator.generate(
            WrapperDefaults.ARCHIVE_BASE, WrapperDefaults.ARCHIVE_PATH,
            WrapperDefaults.DISTRIBUTION_BASE, WrapperDefaults.DISTRIBUTION_PATH,
            null,
            propertiesFile,
            jarFile, jarFileRelativePath,
            unixScript, WrapperGenerator.getBatchScript(unixScript),
            distributionUrl,
            true,
            WrapperDefaults.NETWORK_TIMEOUT,
            WrapperDefaults.RETRIES,
            WrapperDefaults.RETRY_BACK_OFF_MS
        )
    }

    private val effectiveInputHandler: UserInputHandler
        get() {
            if (this.useDefaults.get()) {
                return NonInteractiveUserInputHandler()
            }

            return this.userInputHandler
        }

    /**
     * If not converting an existing Maven build, then validate the build directory is either
     * empty, or overwritable before generating the project.
     *
     * A directory considered empty if it contains no files apart the `.gradle` directory.
     *
     * @param userQuestions the user questions to ask if [.getAllowFileOverwrite] is not set and the directory is non-empty
     * @throws BuildInitException if the build directory is non-empty, this isn't a POM conversion and the user does not allow overwriting
     */
    private fun validateBuildDirectory(userQuestions: UserQuestions) {
        if (!this.isPomConversion) {
            val projectDirFile = this.projectDirectory.get().getAsFile()
            val existingProjectFiles = projectDirFile.listFiles()

            val isEmptyDirectory = existingProjectFiles == null || existingProjectFiles.size == 0
            if (!isEmptyDirectory) {
                var fileOverwriteAllowed = this.allowFileOverwrite.get()
                if (!fileOverwriteAllowed) {
                    fileOverwriteAllowed = userQuestions.askBooleanQuestion(
                        "Found existing files in the project directory: '" + projectDirFile +
                                "'." + System.lineSeparator() + "Directory will be modified and existing files may be overwritten.  Continue?", false
                    )
                }

                if (!fileOverwriteAllowed) {
                    abortBuildDueToExistingFiles(projectDirFile)
                }
            }
        }
    }

    private val isPomConversion: Boolean
        get() = getType() == "pom"

    @VisibleForTesting
    fun getJavaLanguageVersion(userQuestions: UserQuestions, initializer: BuildInitializer): JavaLanguageVersion? {
        if (!initializer.supportsJavaTargets()) {
            return null
        }

        val version = this.javaVersion.getOrNull()
        if (Strings.isNullOrEmpty(version)) {
            return of(userQuestions.askIntQuestion("Enter target Java version", MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API, DEFAULT_JAVA_VERSION))
        }

        try {
            val parsedVersion = version!!.toInt()
            if (parsedVersion < MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API) {
                throw GradleException("Target Java version: '" + version + "' is not a supported target version. It must be equal to or greater than " + MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API)
            }
            return of(parsedVersion)
        } catch (e: NumberFormatException) {
            throw GradleException("Invalid target Java version '" + version + "'. The version must be an integer.", e)
        }
    }

    private fun getBuildInitDsl(userQuestions: UserQuestions, initializer: BuildInitializer): BuildInitDsl {
        val dsl: BuildInitDsl
        if (Strings.isNullOrEmpty(this.dsl)) {
            dsl = userQuestions.selectOption<BuildInitDsl>("Select build script DSL", initializer.getDsls(), initializer.getDefaultDsl())
        } else {
            dsl = BuildInitDsl.Companion.fromName(getDsl())
            if (!initializer.getDsls().contains(dsl)) {
                throw GradleException("The requested DSL '" + getDsl() + "' is not supported for '" + initializer.getId() + "' build type")
            }
        }
        return dsl
    }

    private fun getModularizationOption(userQuestions: UserQuestions, initializer: BuildInitializer): ModularizationOption {
        if (this.splitProject.isPresent()) {
            return if (this.splitProject.get()) ModularizationOption.WITH_LIBRARY_PROJECTS else ModularizationOption.SINGLE_PROJECT
        }
        return userQuestions.choice<ModularizationOption>("Select application structure", initializer.getModularizationOptions())
            .renderUsing(Function { obj: ModularizationOption -> obj.getDisplayName() })
            .ask()
    }

    private fun shouldUseIncubatingAPIs(userQuestions: UserQuestions): Boolean {
        if (this.useIncubating.isPresent()) {
            return this.useIncubating.get()
        }
        return userQuestions.askBooleanQuestion("Generate build using new APIs and behavior (some features may change in the next minor release)?", false)
    }

    private fun getBuildInitTestFramework(userQuestions: UserQuestions, initializer: BuildInitializer, modularizationOption: ModularizationOption): BuildInitTestFramework {
        if (!Strings.isNullOrEmpty(this.testFramework)) {
            return initializer.getTestFrameworks(modularizationOption).stream()
                .filter { candidate: BuildInitTestFramework -> this.testFramework == candidate.getId() }
                .findFirst()
                .orElseThrow<GradleException?>(Supplier { createNotSupportedTestFrameWorkException(initializer, modularizationOption) })
        }

        val testFramework = initializer.getDefaultTestFramework(modularizationOption)
        return userQuestions.selectOption<BuildInitTestFramework>("Select test framework", initializer.getTestFrameworks(modularizationOption), testFramework)
    }

    private fun createNotSupportedTestFrameWorkException(initDescriptor: BuildInitializer, modularizationOption: ModularizationOption): GradleException {
        val formatter = TreeFormatter()
        formatter.node("The requested test framework '" + this.testFramework + "' is not supported for '" + initDescriptor.getId() + "' build type. Supported frameworks")
        formatter.startChildren()
        for (framework in initDescriptor.getTestFrameworks(modularizationOption)) {
            formatter.node("'" + framework.getId() + "'")
        }
        formatter.endChildren()
        return GradleException(formatter.toString())
    }

    @VisibleForTesting
    fun getEffectiveProjectName(userQuestions: UserQuestions, initializer: BuildInitializer): String {
        val projectName = this.projectName
        if (initializer.supportsProjectName()) {
            if (Strings.isNullOrEmpty(projectName)) {
                return userQuestions.askQuestion("Project name", getProjectName())
            }
        } else if (!Strings.isNullOrEmpty(projectName)) {
            throw GradleException("Project name is not supported for '" + initializer.getId() + "' build type.")
        }
        return projectName!!
    }

    @VisibleForTesting
    fun getEffectivePackageName(initializer: BuildInitializer): String? {
        val packageName = this.packageName
        if (initializer.supportsPackage()) {
            if (packageName == null) {
                return this.providerFactory.gradleProperty(SOURCE_PACKAGE_PROPERTY).getOrElse(SOURCE_PACKAGE_DEFAULT)
            }
        } else if (!Strings.isNullOrEmpty(packageName)) {
            throw GradleException("Package name is not supported for '" + initializer.getId() + "' build type.")
        }
        return packageName
    }

    private fun getBuildInitializer(userQuestions: UserQuestions, projectLayoutRegistry: ProjectLayoutSetupRegistry): BuildInitializer {
        if (!Strings.isNullOrEmpty(type)) {
            return projectLayoutRegistry.get(type!!)
        }

        val converter = projectLayoutRegistry.getBuildConverter()
        if (converter.canApplyToCurrentDirectory(this.projectDirectory.get())) {
            if (userQuestions.askBooleanQuestion("Found a " + converter.getSourceBuildDescription() + " build. Generate a Gradle build from this?", true)) {
                return converter
            }
        }
        return selectTypeOfBuild(userQuestions, projectLayoutRegistry)
    }

    @Option(option = "type", description = "Set the type of project to generate.")
    fun setType(type: String?) {
        this.type = type
    }

    @get:ToBeReplacedByLazyProperty(comment = "Not yet supported", issue = "https://github.com/gradle/gradle/issues/29341")
    @get:OptionValues("type")
    val availableBuildTypes: MutableList<String?>
        get() = this.projectLayoutRegistry!!.getAllTypes()

    /**
     * Set the build script DSL to be used.
     *
     * @since 4.5
     */
    @Option(option = "dsl", description = "Set the build script DSL to be used in generated scripts.")
    fun setDsl(dsl: String?) {
        this.dsl = dsl
    }

    @get:ToBeReplacedByLazyProperty(comment = "Not yet supported", issue = "https://github.com/gradle/gradle/issues/29341")
    @get:OptionValues("dsl")
    val availableDSLs: MutableList<String?>
        /**
         * Available build script DSLs to be used.
         *
         * @since 4.5
         */
        get() = BuildInitDsl.Companion.listSupported()

    @get:ToBeReplacedByLazyProperty(comment = "Not yet supported", issue = "https://github.com/gradle/gradle/issues/29341")
    @get:OptionValues("test-framework")
    val availableTestFrameworks: MutableList<String?>
        /**
         * Available test frameworks.
         */
        get() = BuildInitTestFramework.Companion.listSupported()

    /**
     * Set the project name.
     *
     * @since 5.0
     */
    @Option(option = "project-name", description = "Set the project name.")
    fun setProjectName(projectName: String?) {
        this.projectName = projectName
    }

    /**
     * Set the package name.
     *
     * @since 5.0
     */
    @Option(option = "package", description = "Set the package for source files.")
    fun setPackageName(packageName: String?) {
        this.packageName = packageName
    }

    private fun detectType(): String {
        val projectLayoutRegistry = this.projectLayoutRegistry
        val buildConverter = projectLayoutRegistry!!.getBuildConverter()
        if (buildConverter.canApplyToCurrentDirectory(this.projectDirectory.get())) {
            return buildConverter.getId()
        }
        return projectLayoutRegistry.getDefault().getId()
    }

    @get:Inject
    protected abstract val providerFactory: ProviderFactory?

    @get:Inject
    protected abstract val userInputHandler: UserInputHandler

    @get:Inject
    protected abstract val layout: ProjectLayout?

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Inject
    protected abstract val buildInitSpecRegistry: BuildInitSpecRegistry

    companion object {
        private const val SOURCE_PACKAGE_DEFAULT = "org.example"
        private const val SOURCE_PACKAGE_PROPERTY = "org.gradle.buildinit.source.package"
        private const val MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API = 7
        private const val DEFAULT_JAVA_VERSION = 21

        private fun getRelativePath(baseDir: File, targetFile: File): String {
            return baseDir.toPath().relativize(targetFile.toPath()).toString()
        }

        private fun abortBuildDueToExistingFiles(projectDirFile: File?) {
            val resolutions =
                mutableListOf<String?>("Remove any existing files in the project directory and run the init task again.", "Enable the --overwrite option to allow existing files to be overwritten.")
            throw BuildInitException("Aborting build initialization due to existing files in the project directory: '" + projectDirFile + "'.", resolutions)
        }

        private fun validatePackageName(packageName: String?) {
            if (!Strings.isNullOrEmpty(packageName) && !SourceVersion.isName(packageName)) {
                throw GradleException("Package name: '" + packageName + "' is not valid - it may contain invalid characters or reserved words.")
            }
        }

        private fun selectTypeOfBuild(userQuestions: UserQuestions, projectLayoutRegistry: ProjectLayoutSetupRegistry): BuildGenerator {
            // Require that the default option is also the first option
            assert(projectLayoutRegistry.getDefaultComponentType() == projectLayoutRegistry.getComponentTypes().get(0))

            val componentType = userQuestions.choice<ComponentType>("Select type of build to generate", projectLayoutRegistry.getComponentTypes())
                .renderUsing(Function { obj: ComponentType -> obj.getDisplayName() })
                .defaultOption(projectLayoutRegistry.getDefaultComponentType())
                .whenNotConnected(projectLayoutRegistry.getDefault().getComponentType())
                .ask()
            val generators = projectLayoutRegistry.getGeneratorsFor(componentType)
            if (generators.size == 1) {
                return generators.get(0)
            }

            val generatorsByLanguage: MutableMap<Language?, BuildGenerator> = LinkedHashMap<Language?, BuildGenerator>()
            for (language in Language.entries) {
                for (generator in generators) {
                    if (generator.productionCodeUses(language)) {
                        generatorsByLanguage.put(language, generator)
                        break
                    }
                }
            }
            val language = userQuestions.choice<Language>("Select implementation language", generatorsByLanguage.keys).ask()
            return generatorsByLanguage.get(language)!!
        }
    }
}

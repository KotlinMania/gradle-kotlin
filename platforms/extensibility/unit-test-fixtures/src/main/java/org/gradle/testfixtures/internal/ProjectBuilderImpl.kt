/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testfixtures.internal

import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.properties.GradlePropertiesController
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.problems.Problems
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestMetaData
import org.gradle.initialization.LegacyTypesSupport
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.FileUtils
import org.gradle.internal.Pair
import org.gradle.internal.SystemProperties
import org.gradle.internal.build.AbstractBuildState
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildprocess.BuildProcessScopeServices
import org.gradle.internal.buildtree.BuildModelParametersFactory
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeServices
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.buildtree.RunTasksRequirements
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.id.UniqueId
import org.gradle.internal.jvm.SupportedJavaVersions
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException
import org.gradle.internal.logging.services.LoggingServiceRegistry.Companion.newNestedLogging
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.initializeOnDaemon
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode.Companion.fromSystemProperties
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.internal.project.ImmutableProjectDescriptor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.scripts.ScriptFileResolver
import org.gradle.internal.scripts.ScriptFileUtil
import org.gradle.internal.scripts.ScriptResolutionResult
import org.gradle.internal.scripts.ScriptResolutionResultReporter
import org.gradle.internal.service.CloseableServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.session.BuildSessionState
import org.gradle.internal.session.CrossBuildSessionState
import org.gradle.internal.time.Time.currentTimeMillis
import org.gradle.internal.work.ProjectParallelExecutionController
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.util.GradleVersion
import org.gradle.util.Path
import java.io.File
import java.util.Objects
import java.util.function.Consumer
import java.util.function.Function

class ProjectBuilderImpl {
    fun createChildProject(name: String, parent: Project, inputProjectDir: File?): Project {
        val parentProject = parent as ProjectInternal
        val projectDir = if (inputProjectDir != null) inputProjectDir.getAbsoluteFile() else File(parentProject.getProjectDir(), name)

        val descriptor: ProjectBuilderProjectDescriptor = descriptorForSubproject(parentProject, projectDir, name)
        val parentDescriptor = parentProject.getOwner().getDescriptor() as ProjectBuilderProjectDescriptor
        // Side effect, required due to projects being created one by one, instead of all together
        parentDescriptor.addChild(descriptor.getIdentity())

        val projectState = parentProject.getServices().get<ProjectStateRegistry?>(ProjectStateRegistry::class.java)!!
            .registerProject(parentProject.getServices().get<BuildState?>(BuildState::class.java)!!, descriptor)
        projectState.createMutableModel(parentProject.getClassLoaderScope().createChild("project-" + name, null), parentProject.getBaseClassLoaderScope())
        val project = projectState.getMutableModel()

        // Lock the project, these won't ever be released as ProjectBuilder has no lifecycle
        val coordinationService = project.getServices().get<ResourceLockCoordinationService?>(ResourceLockCoordinationService::class.java)
        coordinationService!!.withStateLock(DefaultResourceLockCoordinationService.lock(project.getOwner().getAccessLock()))

        return project
    }

    fun createProject(name: String, inputProjectDir: File?, gradleUserHomeDir: File?): ProjectInternal {
        // ProjectBuilder uses daemon classes, so it has the same JVM compatibility.
        UnsupportedJavaRuntimeException.assertCurrentProcessSupportsDaemonJavaVersion()

        val currentMajor = current()!!.majorVersion.toInt()
        if (currentMajor < SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION) {
            val currentMajorGradleVersion = GradleVersion.current().getMajorVersion()

            // We do not use a DeprecationLogger here since the logger is not initialized when using the ProjectBuilder.
            LOGGER.warn(
                "Executing Gradle on JVM versions {} and lower has been deprecated. " +
                        "This will fail with an error in Gradle {}. " +
                        "Use JVM {} or greater to execute Gradle. " +
                        "Projects can continue to use older JVM versions via toolchains. " +
                        "Consult the upgrading guide for further information: {}",
                SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION - 1,
                currentMajorGradleVersion + 1,
                SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION,
                DocumentationRegistry().getDocumentationFor("upgrading_version_" + currentMajorGradleVersion, "minimum_daemon_jvm_version")
            )
        }

        val projectDir = prepareProjectDir(inputProjectDir)
        val userHomeDir = if (gradleUserHomeDir == null) File(projectDir, "userHome") else FileUtils.canonicalize(gradleUserHomeDir)
        val startParameter = StartParameterInternal()
        startParameter.setGradleUserHomeDir(userHomeDir)
        startParameter.setCurrentDir(projectDir)
        startParameter.doNotSearchUpwards()

        // ProjectBuilder tests are more lightweight and native services shouldn't be required, so we disable them by default.
        // Additionally, when they are enabled they are put in the projectDir by default and that can cause issues with test cleanup on Windows.
        // If needed, a test can still enable them by setting the org.gradle.native=true system property.
        // This was also the default behavior before Gradle 8.8 by accident since org.gradle.native=false was always passed to the test executor.
        val nativeServicesMode = if (System.getProperty(NativeServices.NATIVE_SERVICES_OPTION) != null)
            fromSystemProperties()
        else
            NativeServices.NativeServicesMode.DISABLED
        initializeOnDaemon(userHomeDir, nativeServicesMode)

        val globalServices: ServiceRegistry = globalServices!!

        val buildRequestMetaData: BuildRequestMetaData = DefaultBuildRequestMetaData(currentTimeMillis())
        val userActionRootDir = globalServices.get<BuildLayoutFactory?>(BuildLayoutFactory::class.java)!!.getLayoutFor(startParameter.toBuildLayoutConfiguration()).getRootDirectory()
        val crossBuildSessionState = CrossBuildSessionState(globalServices, startParameter, userActionRootDir)
        val userHomeServices = userHomeServicesOf(globalServices)
        val buildSessionState = BuildSessionState(
            userHomeServices,
            crossBuildSessionState,
            startParameter,
            buildRequestMetaData,
            ClassPath.EMPTY,
            DefaultBuildCancellationToken(),
            buildRequestMetaData.getClient(),
            NoOpBuildEventConsumer()
        )
        val buildActionRequirements = RunTasksRequirements(startParameter)
        val internalOptions = crossBuildSessionState.getServices().get<InternalOptions?>(InternalOptions::class.java)
        val buildSessionServices = buildSessionState.getServices()
        val buildModelParameters =
            buildSessionServices.get<BuildModelParametersFactory?>(BuildModelParametersFactory::class.java)!!.parametersForRootBuildTree(buildActionRequirements, internalOptions!!)
        val buildInvocationScopeId = BuildInvocationScopeId(UniqueId.generate())
        val buildTreeState = BuildTreeState(buildSessionServices, buildActionRequirements, buildModelParameters, buildInvocationScopeId)
        val buildTreeServices = buildTreeState.getServices().get<BuildTreeServices?>(BuildTreeServices::class.java)
        val build = ProjectBuilderImpl.TestRootBuild(projectDir, startParameter, buildTreeServices!!)

        val buildServices = build.getBuildServices()
        buildServices.get<BuildStateRegistry?>(BuildStateRegistry::class.java)!!.attachRootBuild(build)

        // Project or applied plugins can emit deprecation warnings, so we need to initialize the deprecation logger
        DeprecationLogger.init(WarningMode.None, null, null, NoOpProblemDiagnosticsFactory.EMPTY_STREAM)

        // Take a root worker lease; this won't ever be released as ProjectBuilder has no lifecycle
        val coordinationService = buildServices.get<ResourceLockCoordinationService?>(ResourceLockCoordinationService::class.java)
        val workerLeaseService = buildServices.get<WorkerLeaseService?>(WorkerLeaseService::class.java)
        val workerLease = workerLeaseService!!.maybeStartWorker()
        buildServices.get<ProjectParallelExecutionController?>(ProjectParallelExecutionController::class.java)!!.startProjectExecution(false)

        val gradle = build.getMutableModel()
        gradle.setIncludedBuilds(mutableListOf<IncludedBuildInternal>())

        val gradlePropertiesController = buildServices.get<GradlePropertiesController?>(GradlePropertiesController::class.java)
        gradlePropertiesController!!.loadGradleProperties(build.getBuildIdentifier(), build.getBuildRootDir(), false)

        val baseScope = gradle.getClassLoaderScope()
        val rootProjectScope = baseScope.createChild("root-project", null)

        val projectStateRegistry = buildServices.get<ProjectStateRegistry?>(ProjectStateRegistry::class.java)
        val rootProjectDescriptor: ImmutableProjectDescriptor = descriptorForRootProject(build.getIdentityPath(), projectDir, name, buildServices)
        val projectState = projectStateRegistry!!.registerProject(build, rootProjectDescriptor)
        projectState.createMutableModel(rootProjectScope, baseScope)
        val project = projectState.getMutableModel()

        gradle.setDefaultProjectState(projectState)

        // Lock root project; this won't ever be released as ProjectBuilder has no lifecycle
        coordinationService!!.withStateLock(DefaultResourceLockCoordinationService.lock(project.getOwner().getAccessLock()))

        val tearDown: Stoppable = CompositeStoppable.stoppable(
            Stoppable { workerLeaseService.runAsIsolatedTask() },
            Stoppable { workerLease.leaseFinish() },
            buildServices,
            buildTreeState,
            buildSessionState,
            crossBuildSessionState
        )

        RootProjectContext.Companion.attach(project, RootProjectContext(tearDown))

        return project
    }

    private fun userHomeServicesOf(globalServices: ServiceRegistry): GradleUserHomeScopeServiceRegistry {
        return globalServices.get<GradleUserHomeScopeServiceRegistry?>(GradleUserHomeScopeServiceRegistry::class.java)!!
    }

    fun prepareProjectDir(projectDir: File?): File {
        if (projectDir != null) {
            return FileUtils.canonicalize(projectDir)
        }

        val temporaryFileProvider: TemporaryFileProvider = DefaultTemporaryFileProvider(org.gradle.internal.Factory {
            var rootTmpDir = SystemProperties.getInstance().getWorkerTmpDir()
            if (rootTmpDir == null) {
                @Suppress("deprecation") val javaIoTmpDir = SystemProperties.getInstance().getJavaIoTmpDir()
                rootTmpDir = javaIoTmpDir
            }
            FileUtils.canonicalize(File(rootTmpDir))
        })
        val tempDirectory = temporaryFileProvider.createTemporaryDirectory("gradle", "projectDir")
        // TODO deleteOnExit won't clean up non-empty directories (and it leaks memory for long-running processes).
        tempDirectory.deleteOnExit()
        return tempDirectory
    }

    private class TestRootBuild(rootProjectDir: File, startParameter: StartParameterInternal, buildTreeServices: BuildTreeServices) :
        AbstractBuildState(buildTreeServices, BuildDefinition.fromStartParameter(startParameter, rootProjectDir, null), null), RootBuildState {
        public override fun getBuildServices(): CloseableServiceRegistry {
            return super.getBuildServices()
        }

        override fun ensureProjectsLoaded() {
        }

        override fun ensureProjectsConfigured() {
        }

        override fun getIdentityPath(): Path {
            return Path.ROOT
        }

        override fun isImplicitBuild(): Boolean {
            return false
        }

        override fun getStartParameter(): StartParameterInternal? {
            throw UnsupportedOperationException()
        }

        override fun <T> run(action: Function<in BuildTreeLifecycleController, T?>): T? {
            throw UnsupportedOperationException()
        }

        override fun getModel(): IncludedBuildInternal? {
            throw UnsupportedOperationException()
        }

        override fun getAvailableModules(): MutableSet<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>>? {
            throw UnsupportedOperationException()
        }
    }

    /**
     * ProjectBuilder has its own simplified lifecycle that compresses all operational service scopes
     * into a single operation. It also skips the Settings phase entirely, so settings-specific
     * services must be created and managed manually.
     */
    @JvmRecord
    private data class RootProjectContext(
        val tearDown: Stoppable
    ) {
        companion object {
            // The context is stored in the ProjectBuilder-created root project's extra properties.
            // We have to store it in the root project state because the API gives out the full instances of the `Project` to the user.
            // That becomes the only state used for further interactions, such as creating new subprojects and the teardown at the end.
            private const val EXT = "ProjectBuilder.context"

            fun attach(rootProject: Project, context: RootProjectContext) {
                assert(rootProject.getParent() == null)
                rootProject.getExtensions().getExtraProperties().set(EXT, context)
            }

            fun obtain(rootProject: Project): RootProjectContext {
                assert(rootProject.getParent() == null)
                return Objects.requireNonNull<Any?>(rootProject.getExtensions().getExtraProperties().get(EXT)) as RootProjectContext
            }
        }
    }

    companion object {
        @get:Synchronized
        var globalServices: ServiceRegistry? = null
            get() {
                if (field == null) {
                    field = createGlobalServices()
                    // Inject missing interfaces to support the usage of plugins compiled with older Gradle versions.
                    // A normal gradle build does this by adding the MixInLegacyTypesClassLoader to the class loader hierarchy.
                    // In a test run, which is essentially a plain Java application, the classpath is flattened and injected
                    // into the system class loader and there exists no Gradle class loader hierarchy in the running test. (See Implementation
                    // in ApplicationClassesInSystemClassLoaderWorkerImplementationFactory, BootstrapSecurityManager and GradleWorkerMain.)
                    // Thus, we inject the missing interfaces directly into the system class loader used to load all classes in the test.
                    field!!.get<LegacyTypesSupport?>(LegacyTypesSupport::class.java)!!
                        .injectEmptyInterfacesIntoClassLoader(ProjectBuilderImpl::class.java.getClassLoader())
                }
                return field
            }
            private set

        private val LOGGER: Logger = getLogger(ProjectBuilderImpl::class.java)!!

        private fun descriptorForRootProject(
            buildPath: Path,
            projectDir: File,
            projectName: String,
            buildServices: ServiceRegistry
        ): ImmutableProjectDescriptor {
            val identity = ProjectIdentity.forRootProject(buildPath, projectName)
            val scriptFileResolver = buildServices.get<ScriptFileResolver?>(ScriptFileResolver::class.java)
            val reporter = ScriptResolutionResultReporter(buildServices.get<Problems?>(Problems::class.java)!!.reporter)
            val buildFile = ScriptFileUtil.resolveBuildFile(projectDir, scriptFileResolver!!, Consumer { result: ScriptResolutionResult? -> reporter.reportResolutionProblemsOf(result!!) })
            return ProjectBuilderProjectDescriptor(identity, projectDir, buildFile, null)
        }

        private fun descriptorForSubproject(
            parent: ProjectInternal,
            projectDir: File,
            projectName: String
        ): ProjectBuilderProjectDescriptor {
            val parentIdentity = parent.getProjectIdentity()
            val projectPath = parentIdentity.getProjectPath().child(projectName)
            val identity = ProjectIdentity.forSubproject(parentIdentity.getBuildPath(), projectPath)
            val scriptFileResolver = parent.getServices().get<ScriptFileResolver?>(ScriptFileResolver::class.java)
            val reporter = ScriptResolutionResultReporter(parent.getServices().get<Problems?>(Problems::class.java)!!.reporter)
            val buildFile = ScriptFileUtil.resolveBuildFile(projectDir, scriptFileResolver!!, Consumer { result: ScriptResolutionResult? -> reporter.reportResolutionProblemsOf(result!!) })
            return ProjectBuilderProjectDescriptor(identity, projectDir, buildFile, parentIdentity)
        }

        fun stop(rootProject: Project) {
            RootProjectContext.Companion.obtain(rootProject).tearDown.stop()
        }

        private fun createGlobalServices(): ServiceRegistry {
            return builder()
                .displayName("global services")
                .parent(newNestedLogging())
                .parent(getInstance())
                .provider(TestGlobalScopeServices())
                .provider(BuildProcessScopeServices())
                .build()
        }
    }
}

/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.jvm.internal.services

import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.cache.FileLockManager
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.initialization.JvmToolchainsConfigurationValidator
import org.gradle.internal.authentication.AuthenticationSchemeRegistry
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.DefaultJavaInstallationRegistry
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmInstallationProblemReporter
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.ExternalResourceFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmToolchainManagement
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainResolverRegistry
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainResolverService
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService
import org.gradle.jvm.toolchain.internal.DefaultJvmToolchainManagement
import org.gradle.jvm.toolchain.internal.DefaultOsXJavaHomeCommand
import org.gradle.jvm.toolchain.internal.DefaultToolchainExternalResourceFactory
import org.gradle.jvm.toolchain.internal.InstallationSupplier
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverService
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier
import org.gradle.jvm.toolchain.internal.MavenToolchainsInstallationSupplier
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier
import org.gradle.jvm.toolchain.internal.OsXJavaHomeCommand
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader
import org.gradle.platform.internal.CurrentBuildPlatform
import org.gradle.process.internal.ClientExecHandleBuilderFactory

class ToolchainsJvmServices : AbstractGradleModuleServices() {
    private class GlobalServices : ServiceRegistrationProvider {
        @Provides
        @Suppress("UNUSED_PARAMETER", "FunctionParameterNaming")
        fun createJavaToolchainSpec(objectFactory: ObjectFactory, currentJvm: Jvm): CurrentJvmToolchainSpec {
            return objectFactory.newInstance<CurrentJvmToolchainSpec>(CurrentJvmToolchainSpec::class.java)
        }

        fun configure(registration: ServiceRegistration) {
            registration.add(CurrentBuildPlatform::class.java)
            registration.add(JavaToolchainHttpRedirectVerifierFactory::class.java)
        }
    }

    private class BuildServices : ServiceRegistrationProvider {
        @Provides
        fun createJavaToolchainResolverRegistry(
            gradle: Gradle,
            instantiator: Instantiator,
            objectFactory: ObjectFactory,
            providerFactory: ProviderFactory,
            authenticationSchemeRegistry: AuthenticationSchemeRegistry
        ): JavaToolchainResolverRegistryInternal {
            return objectFactory.newInstance<DefaultJavaToolchainResolverRegistry>(
                DefaultJavaToolchainResolverRegistry::class.java,
                gradle,
                instantiator,
                objectFactory,
                providerFactory,
                authenticationSchemeRegistry
            )
        }

        @Provides
        fun createToolchainManagement(objectFactory: ObjectFactory, registry: JavaToolchainResolverRegistry): JvmToolchainManagement {
            return objectFactory.newInstance<DefaultJvmToolchainManagement>(DefaultJvmToolchainManagement::class.java, registry)
        }

        @Provides
        @Suppress("UNUSED_PARAMETER", "FunctionParameterNaming")
        fun createJdkCacheDirectory(
            objectFactory: ObjectFactory,
            homeDirProvider: GradleUserHomeDirProvider,
            operations: FileOperations,
            lockManager: FileLockManager,
            execHandleFactory: ClientExecHandleBuilderFactory,
            temporaryFileProvider: GradleUserHomeTemporaryFileProvider
        ): JdkCacheDirectory {
            val silentDetector = DefaultJvmMetadataDetector(execHandleFactory, temporaryFileProvider)
            return DefaultJdkCacheDirectory(homeDirProvider, operations, lockManager, silentDetector, temporaryFileProvider)
        }

        fun configure(registration: ServiceRegistration) {
            registration.add<ProviderBackedToolchainConfiguration?>(ToolchainConfiguration::class.java, ProviderBackedToolchainConfiguration::class.java)
            registration.add<DefaultOsXJavaHomeCommand?>(OsXJavaHomeCommand::class.java, DefaultOsXJavaHomeCommand::class.java)

            // NOTE: These need to be kept in sync with DaemonClientToolchainServices
            registration.add<AsdfInstallationSupplier?>(InstallationSupplier::class.java, AsdfInstallationSupplier::class.java)
            registration.add<IntellijInstallationSupplier?>(InstallationSupplier::class.java, IntellijInstallationSupplier::class.java)
            registration.add<JabbaInstallationSupplier?>(InstallationSupplier::class.java, JabbaInstallationSupplier::class.java)
            registration.add<SdkmanInstallationSupplier?>(InstallationSupplier::class.java, SdkmanInstallationSupplier::class.java)
            registration.add<MavenToolchainsInstallationSupplier?>(InstallationSupplier::class.java, MavenToolchainsInstallationSupplier::class.java)

            registration.add<LinuxInstallationSupplier?>(InstallationSupplier::class.java, LinuxInstallationSupplier::class.java)
            registration.add<OsXInstallationSupplier?>(InstallationSupplier::class.java, OsXInstallationSupplier::class.java)
            registration.add<WindowsInstallationSupplier?>(InstallationSupplier::class.java, WindowsInstallationSupplier::class.java)

            registration.add<DefaultJavaInstallationRegistry?>(JavaInstallationRegistry::class.java, DefaultJavaInstallationRegistry::class.java)
            // This has a dependency on RepositoryTransportFactory, which is build scoped, and is required by the following services as well
            registration.add<DefaultToolchainExternalResourceFactory?>(ExternalResourceFactory::class.java, DefaultToolchainExternalResourceFactory::class.java)
            registration.add(SecureFileDownloader::class.java)
            registration.add<DefaultJavaToolchainProvisioningService?>(JavaToolchainProvisioningService::class.java, DefaultJavaToolchainProvisioningService::class.java)
            registration.add(JavaToolchainQueryService::class.java)
        }
    }

    public override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.addProvider(GlobalServices())
    }

    public override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.add(JvmInstallationProblemReporter::class.java)
        registration.add<DefaultJvmToolchainsConfigurationValidator?>(JvmToolchainsConfigurationValidator::class.java, DefaultJvmToolchainsConfigurationValidator::class.java)
    }

    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildServices())
    }

    public override fun registerProjectServices(registration: ServiceRegistration) {
        registration.add<DefaultJavaToolchainResolverService?>(JavaToolchainResolverService::class.java, DefaultJavaToolchainResolverService::class.java)
        registration.add<DefaultJavaToolchainService?>(JavaToolchainService::class.java, DefaultJavaToolchainService::class.java)
    }
}

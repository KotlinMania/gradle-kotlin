/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.enterprise.impl

import org.gradle.internal.enterprise.DevelocityBuildLifecycleService
import org.gradle.internal.enterprise.DevelocityPluginUnsafeConfigurationService
import org.gradle.internal.enterprise.GradleEnterprisePluginBuildState
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInService
import org.gradle.internal.enterprise.GradleEnterprisePluginConfig
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanBuildStartedTime
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanClock
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanScopeIds
import org.gradle.internal.enterprise.impl.legacy.LegacyGradleEnterprisePluginCheckInService
import org.gradle.internal.scan.config.BuildScanConfigProvider
import org.gradle.internal.scan.eob.BuildScanEndOfBuildNotifier
import org.gradle.internal.scan.scopeids.BuildScanScopeIds
import org.gradle.internal.scan.time.BuildScanBuildStartedTime
import org.gradle.internal.scan.time.BuildScanClock
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractGradleModuleServices

class GradleEnterprisePluginServices : AbstractGradleModuleServices() {
    public override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.add(GradleEnterpriseAutoAppliedPluginRegistry::class.java)
        registration.add(GradleEnterprisePluginAutoAppliedStatus::class.java)
        registration.add<DefaultGradleEnterprisePluginServiceRef?>(GradleEnterprisePluginServiceRefInternal::class.java, DefaultGradleEnterprisePluginServiceRef::class.java)
        registration.add<DefaultGradleEnterprisePluginBuildState?>(GradleEnterprisePluginBuildState::class.java, DefaultGradleEnterprisePluginBuildState::class.java)
        registration.add<DefaultGradleEnterprisePluginConfig?>(GradleEnterprisePluginConfig::class.java, DefaultGradleEnterprisePluginConfig::class.java)
        registration.add<DefaultGradleEnterprisePluginBackgroundJobExecutors?>(
            GradleEnterprisePluginBackgroundJobExecutorsInternal::class.java,
            DefaultGradleEnterprisePluginBackgroundJobExecutors::class.java
        )
        registration.add<DefaultDevelocityPluginUnsafeConfigurationService?>(DevelocityPluginUnsafeConfigurationService::class.java, DefaultDevelocityPluginUnsafeConfigurationService::class.java)

        // legacy
        registration.add<DefaultBuildScanClock?>(BuildScanClock::class.java, DefaultBuildScanClock::class.java)
        registration.add<DefaultBuildScanBuildStartedTime?>(BuildScanBuildStartedTime::class.java, DefaultBuildScanBuildStartedTime::class.java)
    }

    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.add(GradleEnterprisePluginAutoApplicationListener::class.java)
        registration.add(DefaultGradleEnterprisePluginAdapterFactory::class.java)
        registration.add<DefaultGradleEnterprisePluginCheckInService?>(GradleEnterprisePluginCheckInService::class.java, DefaultGradleEnterprisePluginCheckInService::class.java)
        registration.add<DefaultDevelocityBuildLifecycleService?>(DevelocityBuildLifecycleService::class.java, DefaultDevelocityBuildLifecycleService::class.java)
        registration.add<DefaultGradleEnterprisePluginRequiredServices?>(GradleEnterprisePluginRequiredServices::class.java, DefaultGradleEnterprisePluginRequiredServices::class.java)

        // legacy
        registration.add<DefaultBuildScanScopeIds?>(BuildScanScopeIds::class.java, DefaultBuildScanScopeIds::class.java)
        registration.add<LegacyGradleEnterprisePluginCheckInService?>(
            BuildScanConfigProvider::class.java,
            BuildScanEndOfBuildNotifier::class.java,
            LegacyGradleEnterprisePluginCheckInService::class.java
        )
    }
}

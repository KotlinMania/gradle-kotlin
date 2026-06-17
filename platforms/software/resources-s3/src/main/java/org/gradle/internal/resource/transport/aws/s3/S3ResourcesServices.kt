/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.resource.transport.aws.s3

import org.gradle.authentication.aws.AwsImAuthentication
import org.gradle.internal.authentication.AuthenticationSchemeRegistry
import org.gradle.internal.authentication.DefaultAwsImAuthentication
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices

class S3ResourcesServices : AbstractGradleModuleServices() {
    public override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.addProvider(GlobalScopeServices())
    }

    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(AuthenticationSchemeAction())
    }

    private class GlobalScopeServices : ServiceRegistrationProvider {
        @Provides
        fun createS3ConnectorFactory(): ResourceConnectorFactory {
            return S3ConnectorFactory()
        }
    }

    private class AuthenticationSchemeAction : ServiceRegistrationProvider {
        @Provides
        fun configure(registration: ServiceRegistration?, authenticationSchemeRegistry: AuthenticationSchemeRegistry) {
            authenticationSchemeRegistry.registerScheme<AwsImAuthentication?>(AwsImAuthentication::class.java, DefaultAwsImAuthentication::class.java)
        }
    }
}

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
package org.gradle.problems.internal.services

import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractGradleModuleServices

/**
 * Service registration entry point for the Problems API.
 *
 *
 * This class is responsible registering all service providers for the Problems API.
 * See the `META-INF/services` directory to find the respective metadata file triggering the DI framework to load this class.
 */
class ProblemsServices : AbstractGradleModuleServices() {
    public override fun registerBuildTreeServices(registration: ServiceRegistration?) {
        registration!!.addProvider(ProblemsBuildTreeServices())
    }

    public override fun registerBuildSessionServices(registration: ServiceRegistration?) {
        registration!!.addProvider(ProblemsBuildSessionServices())
    }
}

/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.service.scopes

import org.gradle.internal.service.ServiceRegistration

/**
 * Base no-op implementation of the [GradleModuleServices].
 */
open class AbstractGradleModuleServices : GradleModuleServices {
    override fun registerGlobalServices(registration: ServiceRegistration?) {
    }

    override fun registerGradleUserHomeServices(registration: ServiceRegistration?) {
    }

    override fun registerCrossBuildSessionServices(registration: ServiceRegistration?) {
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration?) {
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration?) {
    }

    override fun registerBuildServices(registration: ServiceRegistration?) {
    }

    override fun registerSettingsServices(registration: ServiceRegistration?) {
    }

    override fun registerProjectServices(registration: ServiceRegistration?) {
    }
}

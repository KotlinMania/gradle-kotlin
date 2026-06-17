/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.internal.service.ServiceRegistrationProvider

/**
 * Can be implemented by Gradle modules to provide services in various scopes.
 *
 *
 * Implementations are discovered using the JAR service locator mechanism (see [org.gradle.internal.service.ServiceLocator]).
 *
 * @see Scope
 */
@ServiceScope(Scope.Global::class)
interface GradleModuleServices : ServiceRegistrationProvider {
    /**
     * Called to register services in the [Global][Scope.Global] scope.
     *
     * @see Scope
     *
     * @see Scope.Global
     */
    fun registerGlobalServices(registration: ServiceRegistration?)

    /**
     * Called to register services in the [UserHome][Scope.UserHome] scope.
     *
     * @see Scope
     *
     * @see Scope.UserHome
     */
    fun registerGradleUserHomeServices(registration: ServiceRegistration?)

    /**
     * Called to register services in the [CrossBuildSession][Scope.CrossBuildSession] scope.
     *
     * @see Scope
     *
     * @see Scope.CrossBuildSession
     */
    fun registerCrossBuildSessionServices(registration: ServiceRegistration?)

    /**
     * Called to register services in the [BuildSession][Scope.BuildSession] scope.
     *
     * @see Scope
     *
     * @see Scope.BuildSession
     */
    fun registerBuildSessionServices(registration: ServiceRegistration?)

    /**
     * Called to register services in the [BuildTree][Scope.BuildTree] scope.
     *
     * @see Scope
     *
     * @see Scope.BuildTree
     */
    fun registerBuildTreeServices(registration: ServiceRegistration?)

    /**
     * Called to register services in the [Build][Scope.Build] scope.
     *
     * @see Scope
     *
     * @see Scope.Build
     */
    fun registerBuildServices(registration: ServiceRegistration?)

    /**
     * Called to register services in the [Settings][Scope.Settings] scope.
     *
     * @see Scope
     *
     * @see Scope.Settings
     */
    fun registerSettingsServices(registration: ServiceRegistration?)

    /**
     * Called to register services in the [Project][Scope.Project] scope.
     *
     * @see Scope
     *
     * @see Scope.Project
     */
    fun registerProjectServices(registration: ServiceRegistration?)
}

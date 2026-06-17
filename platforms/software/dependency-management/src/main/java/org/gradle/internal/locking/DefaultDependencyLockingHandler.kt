/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.locking

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.util.function.Supplier

class DefaultDependencyLockingHandler(private val configurationContainer: Supplier<ConfigurationContainer>, private val dependencyLockingProvider: DependencyLockingProvider) :
    DependencyLockingHandler {
    private var configurations: ConfigurationContainer? = null
        get() {
            if (field == null) {
                field = configurationContainer.get()
            }
            return field
        }

    override fun lockAllConfigurations() {
        this.configurations!!.configureEach(Action { configuration: Configuration? -> configuration!!.getResolutionStrategy().activateDependencyLocking() }
        )
    }

    override fun unlockAllConfigurations() {
        this.configurations!!.configureEach(Action { configuration: Configuration? -> configuration!!.getResolutionStrategy().deactivateDependencyLocking() }
        )
    }

    override fun getLockMode(): Property<LockMode> {
        return dependencyLockingProvider.getLockMode()
    }

    override fun getLockFile(): RegularFileProperty {
        return dependencyLockingProvider.getLockFile()
    }

    override fun getIgnoredDependencies(): ListProperty<String> {
        return dependencyLockingProvider.getIgnoredDependencies()
    }
}

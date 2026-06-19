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
package org.gradle.process.internal

import org.gradle.api.internal.ExternalProcessStartedListener
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Manages forking/spawning processes.
 */
@Suppress("deprecation")
@ServiceScope(Scope.Global::class, Scope.UserHome::class, Scope.BuildSession::class, Scope.Build::class, Scope.Project::class)
interface ExecFactory : ExecActionFactory, ExecHandleFactory, JavaExecHandleFactory, JavaForkOptionsFactory, ProcessOperations {
    /**
     * Creates a new factory for the given context. Returns a [Builder] for further configuration of the created instance. You must provide an Instantiator when creating the child factory from
     * the root one.
     */
    fun forContext(): Builder

    /**
     * Builder to configure an instance of the new factory.
     */
    interface Builder {
        fun withFileResolver(fileResolver: FileResolver): Builder

        fun withFileCollectionFactory(fileCollectionFactory: FileCollectionFactory): Builder

        fun withInstantiator(instantiator: Instantiator): Builder

        fun withObjectFactory(objectFactory: ObjectFactory): Builder

        fun withJavaModuleDetector(javaModuleDetector: JavaModuleDetector?): Builder

        fun withBuildCancellationToken(buildCancellationToken: BuildCancellationToken?): Builder

        fun withExternalProcessStartedListener(externalProcessStartedListener: ExternalProcessStartedListener?): Builder

        fun withoutExternalProcessStartedListener(): Builder

        fun build(): ExecFactory
    }
}

/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugin.use.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.plugins.CorePluginRegistryProvider
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.plugin.use.resolve.internal.CompositePluginResolver
import org.gradle.plugin.use.resolve.internal.CorePluginResolver
import org.gradle.plugin.use.resolve.internal.NoopPluginResolver
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositories
import org.gradle.plugin.use.resolve.internal.PluginResolver
import org.gradle.plugin.use.resolve.internal.PluginResolverContributor
import org.gradle.plugin.use.resolve.service.internal.ClientInjectedClasspathPluginResolver
import java.util.LinkedList
import java.util.function.Consumer

@ServiceScope(Scope.Build::class)
class PluginResolverFactory(
    corePluginRegistryProvider: CorePluginRegistryProvider,
    private val documentationRegistry: DocumentationRegistry,
    private val injectedClasspathPluginResolver: ClientInjectedClasspathPluginResolver,
    private val pluginResolverContributors: MutableList<PluginResolverContributor>
) {
    private val corePluginRegistry: PluginRegistry

    init {
        this.corePluginRegistry = corePluginRegistryProvider.getCorePluginRegistry()
    }

    fun create(pluginResolveContext: PluginArtifactRepositories): PluginResolver {
        return CompositePluginResolver(createDefaultResolvers(pluginResolveContext))
    }

    private fun createDefaultResolvers(pluginResolveContext: PluginArtifactRepositories): MutableList<PluginResolver> {
        val resolvers: MutableList<PluginResolver> = LinkedList<PluginResolver>()
        addDefaultResolvers(pluginResolveContext, resolvers)
        return resolvers
    }

    /**
     * Returns the default PluginResolvers used by Gradle.
     *
     *
     * The plugins will be searched in a chain from the first to the last until a plugin is found.
     * So, order matters.
     *
     *  1. [NoopPluginResolver] - Only used in tests.
     *  1. [CorePluginResolver] - distributed with Gradle
     *  1. [DefaultInjectedClasspathPluginResolver] - from a TestKit test's ClassPath
     *  1. Resolvers contributed by this distribution - plugins coming from included builds
     *  1. Resolvers based on the entries of the `pluginRepositories` block
     *  1. [org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver] - from Gradle Plugin Portal if no `pluginRepositories` were defined
     *
     *
     *
     * This order is optimized for both performance and to allow resolvers earlier in the order
     * to mask plugins which would have been found later in the order.
     */
    private fun addDefaultResolvers(pluginResolveContext: PluginArtifactRepositories, resolvers: MutableList<PluginResolver>) {
        resolvers.add(NoopPluginResolver(corePluginRegistry))
        resolvers.add(CorePluginResolver(documentationRegistry, corePluginRegistry))

        injectedClasspathPluginResolver.collectResolversInto(resolvers)

        pluginResolverContributors.forEach(Consumer { contributor: PluginResolverContributor? -> contributor!!.collectResolversInto(resolvers) })
        resolvers.add(pluginResolveContext.getPluginResolver())
    }
}

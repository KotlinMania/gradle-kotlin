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
package org.gradle.api.internal.artifacts.dsl

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.internal.Actions
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.MethodAccess
import org.gradle.internal.metaobject.MethodMixIn
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.internal.GUtil
import java.util.Arrays

class DefaultArtifactHandler(private val configurationContainer: ConfigurationContainer, private val publishArtifactFactory: NotationParser<Any, ConfigurablePublishArtifact>) : ArtifactHandler,
    MethodMixIn {
    private val dynamicMethods: DynamicMethods

    init {
        dynamicMethods = DefaultArtifactHandler.DynamicMethods()
    }

    private fun pushArtifact(configuration: Configuration, notation: Any, configureClosure: Closure<*>): PublishArtifact {
        val configureAction = ConfigureUtil.configureUsing<Any>(configureClosure)
        return pushArtifact(configuration, notation, configureAction)
    }

    private fun pushArtifact(configuration: Configuration, notation: Any, configureAction: Action<in ConfigurablePublishArtifact>): PublishArtifact {
        val publishArtifact = publishArtifactFactory.parseNotation(notation)
        configuration.getArtifacts().add(publishArtifact)
        configureAction.execute(publishArtifact)
        return publishArtifact
    }

    override fun add(configurationName: String, artifactNotation: Any, configureAction: Action<in ConfigurablePublishArtifact>): PublishArtifact {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, configureAction)
    }

    override fun add(configurationName: String, artifactNotation: Any): PublishArtifact {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, Actions.doNothing<ConfigurablePublishArtifact>())
    }

    override fun add(configurationName: String, artifactNotation: Any, configureClosure: Closure<*>): PublishArtifact {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, configureClosure)
    }

    override fun getAdditionalMethods(): MethodAccess {
        return dynamicMethods
    }

    private inner class DynamicMethods : MethodAccess {
        override fun hasMethod(name: String, vararg arguments: Any): Boolean {
            return arguments.size > 0 && configurationContainer.findByName(name) != null
        }

        override fun tryInvokeMethod(name: String, vararg arguments: Any): DynamicInvokeResult {
            if (arguments.size == 0) {
                return DynamicInvokeResult.notFound()
            }
            val configuration = configurationContainer.findByName(name)
            if (configuration == null) {
                return DynamicInvokeResult.notFound()
            }
            val normalizedArgs = GUtil.flatten(Arrays.asList<Any>(*arguments), false)
            if (normalizedArgs.size == 2 && normalizedArgs.get(1) is Closure<*>) {
                return DynamicInvokeResult.found(pushArtifact(configuration, normalizedArgs.get(0), normalizedArgs.get(1) as Closure<*>))
            } else {
                for (notation in normalizedArgs) {
                    pushArtifact(configuration, notation, Actions.doNothing<ConfigurablePublishArtifact>())
                }
                return DynamicInvokeResult.found()
            }
        }
    }
}

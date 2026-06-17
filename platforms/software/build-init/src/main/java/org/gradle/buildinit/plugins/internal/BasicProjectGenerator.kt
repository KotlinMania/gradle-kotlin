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
package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry

class BasicProjectGenerator(private val scriptBuilderFactory: BuildScriptBuilderFactory, private val documentationRegistry: DocumentationRegistry) : BuildContentGenerator {
    override fun generate(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext) {
        scriptBuilderFactory.scriptForNewProjects(settings.getDsl(), buildContentGenerationContext, "build", settings.isUseIncubatingAPIs())
            .withComments(if (settings.isWithComments()) BuildInitComments.ON else BuildInitComments.OFF)
            .fileComment(
                "This is a general purpose Gradle build.\n"
                        + documentationRegistry.sampleForMessage
            )
            .create(settings.getTarget())
            .generate()
    }
}

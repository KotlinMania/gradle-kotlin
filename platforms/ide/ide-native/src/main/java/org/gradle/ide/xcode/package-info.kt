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
/**
 * Model classes for XCode.
 *
 * @since 4.2
 */
package org.gradle.ide.xcode

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject.xml
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.serialization.Cached.get
import org.gradle.plugins.ide.api.XmlGeneratorTask.xmlTransformer
import org.gradle.plugins.ide.internal.IdePlugin.getLifecycleTask
import org.gradle.plugins.ide.internal.IdePlugin.Companion.withDescription
import org.gradle.plugins.ide.internal.IdePlugin.isRoot
import org.gradle.plugins.ide.internal.IdePlugin.Companion.dependsOn
import org.gradle.plugins.ide.internal.IdePlugin.addWorkspace
import org.gradle.plugins.ide.internal.IdePlugin.getCleanTask
import org.gradle.plugins.ide.api.GeneratorTask.outputFile
import org.gradle.plugins.ide.internal.IdeArtifactRegistry.registerIdeProject
import org.gradle.plugins.ide.internal.IdeArtifactRegistry.getIdeProjectFiles
import org.gradle.plugins.ide.internal.IdePlugin.Companion.toGradleCommand
import org.gradle.util.internal.CollectionUtils.findFirst
import org.gradle.plugins.ide.internal.IdeArtifactRegistry.getIdeProject
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.api.logging.Logging.getLogger
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.plugins.ide.internal.IdeProjectMetadata.file
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.util.internal.CollectionUtils.toList
import org.gradle.plugins.ide.internal.IdeArtifactRegistry.getIdeProjects


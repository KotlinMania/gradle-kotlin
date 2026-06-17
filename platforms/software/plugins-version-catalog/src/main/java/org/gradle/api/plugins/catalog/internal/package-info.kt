/*
 * Copyright 2020 the original author or authors.
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
 * Implementation classes for the Gradle platform plugin.
 *
 * @see org.gradle.api.plugins.catalog.VersionCatalogPlugin
 */
package org.gradle.api.plugins.catalog.internal

import org.gradle.api.internal.catalog.DefaultVersionCatalog.versionAliases
import org.gradle.api.internal.catalog.DefaultVersionCatalog.getVersion
import org.gradle.api.internal.catalog.VersionModel.version
import org.gradle.api.internal.catalog.DefaultVersionCatalog.libraryAliases
import org.gradle.api.internal.catalog.DefaultVersionCatalog.getDependencyData
import org.gradle.api.internal.catalog.DependencyModel.group
import org.gradle.api.internal.catalog.DependencyModel.name
import org.gradle.api.internal.catalog.DependencyModel.versionRef
import org.gradle.api.internal.catalog.DependencyModel.getVersion
import org.gradle.api.internal.catalog.DefaultVersionCatalog.bundleAliases
import org.gradle.api.internal.catalog.DefaultVersionCatalog.getBundle
import org.gradle.api.internal.catalog.BundleModel.components
import org.gradle.api.internal.catalog.DefaultVersionCatalog.pluginAliases
import org.gradle.api.internal.catalog.DefaultVersionCatalog.getPlugin
import org.gradle.api.internal.catalog.PluginModel.id
import org.gradle.api.internal.catalog.PluginModel.versionRef
import org.gradle.api.internal.catalog.PluginModel.getVersion
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.build
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.containsLibraryAlias
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.library


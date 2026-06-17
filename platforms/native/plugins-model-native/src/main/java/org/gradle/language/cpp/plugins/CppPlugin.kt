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
package org.gradle.language.cpp.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin

/**
 * A plugin for projects wishing to build native binary components from C++ sources.
 *
 *
 * Automatically includes the [CppLangPlugin] for core C++ support and the [NativeComponentModelPlugin] for native component support.
 *
 *
 *  * Creates a [org.gradle.language.cpp.tasks.CppCompile] task for each [org.gradle.language.cpp.CppSourceSet] to compile the C++ sources.
 *
 */
@Incubating
abstract class CppPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeComponentModelPlugin::class.java)
        project.getPluginManager().apply(CppLangPlugin::class.java)
    }
}

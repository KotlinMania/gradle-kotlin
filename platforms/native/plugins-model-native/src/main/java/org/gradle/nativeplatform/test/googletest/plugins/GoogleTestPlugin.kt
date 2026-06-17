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
package org.gradle.nativeplatform.test.googletest.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.cpp.plugins.CppLangPlugin
import org.gradle.model.ModelMap
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteBinary
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteSpec
import org.gradle.nativeplatform.test.internal.NativeTestSuites
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin
import org.gradle.platform.base.ComponentBinaries
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.TypeBuilder
import java.io.File

/**
 * A plugin that sets up the infrastructure for testing native binaries with GoogleTest.
 */
@Incubating
abstract class GoogleTestPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin::class.java)
        project.getPluginManager().apply(CppLangPlugin::class.java)
    }

    internal class Rules : RuleSource() {
        @ComponentType
        fun registerGoogleTestSuiteSpecTest(builder: TypeBuilder<GoogleTestTestSuiteSpec?>) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteSpec::class.java)
        }

        @ComponentType
        fun registerGoogleTestSuiteBinaryType(builder: TypeBuilder<GoogleTestTestSuiteBinarySpec?>) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteBinary::class.java)
        }

        @ComponentBinaries
        fun createGoogleTestTestBinaries(
            binaries: ModelMap<GoogleTestTestSuiteBinarySpec?>?,
            testSuite: GoogleTestTestSuiteSpec?,
            @Path("buildDir") buildDir: File?,
            serviceRegistry: ServiceRegistry?
        ) {
            NativeTestSuites.createNativeTestSuiteBinaries<GoogleTestTestSuiteBinarySpec?>(binaries, testSuite, GoogleTestTestSuiteBinarySpec::class.java, "GoogleTestExe", buildDir, serviceRegistry)
        }
    }
}

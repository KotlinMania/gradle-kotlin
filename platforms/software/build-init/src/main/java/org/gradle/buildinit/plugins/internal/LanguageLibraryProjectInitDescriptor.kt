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

import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import java.util.Optional

abstract class LanguageLibraryProjectInitDescriptor : LanguageSpecificProjectGenerator {
    protected fun withPackage(settings: InitSettings, className: String): String {
        if (settings.getPackageName().isEmpty()) {
            return className
        } else {
            return settings.getPackageName() + "." + className
        }
    }

    override fun getModularizationOptions(): MutableSet<ModularizationOption> {
        return mutableSetOf<ModularizationOption>(ModularizationOption.SINGLE_PROJECT)
    }

    override fun getFurtherReading(settings: InitSettings): Optional<String> {
        return Optional.empty<String>()
    }

    protected fun configureDefaultTestSuite(
        buildScriptBuilder: BuildScriptBuilder,
        testFramework: BuildInitTestFramework,
        libraryVersionProvider: TemplateLibraryVersionProvider
    ): BuildScriptBuilder.SuiteSpec {
        return addTestSuite(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME, buildScriptBuilder, testFramework, libraryVersionProvider)
    }

    protected fun addTestSuite(
        name: String,
        buildScriptBuilder: BuildScriptBuilder,
        testFramework: BuildInitTestFramework,
        libraryVersionProvider: TemplateLibraryVersionProvider
    ): BuildScriptBuilder.SuiteSpec {
        when (testFramework) {
            BuildInitTestFramework.JUNIT -> return buildScriptBuilder.testing().junitSuite(name, libraryVersionProvider)
            BuildInitTestFramework.JUNIT_JUPITER -> return buildScriptBuilder.testing().junitJupiterSuite(name, libraryVersionProvider)
            BuildInitTestFramework.SPOCK -> return buildScriptBuilder.testing().spockSuite(name, libraryVersionProvider)
            BuildInitTestFramework.KOTLINTEST -> return buildScriptBuilder.testing().kotlinTestSuite(name, libraryVersionProvider)
            BuildInitTestFramework.TESTNG -> return buildScriptBuilder.testing().testNG(name, libraryVersionProvider)
            BuildInitTestFramework.SCALATEST -> {
                val suiteSpec = buildScriptBuilder.testing().junitSuite(name, libraryVersionProvider)
                val scalaVersion = libraryVersionProvider.getVersion("scala")
                val scalaTestVersion = libraryVersionProvider.getVersion("scalatest")
                val scalaTestPlusJunitVersion = libraryVersionProvider.getVersion("scalatestplus-junit")
                val scalaXmlVersion = libraryVersionProvider.getVersion("scala-xml")
                suiteSpec.implementation(
                    "Use Scalatest for testing our library",
                    BuildInitDependency.Companion.of("org.scalatest:scalatest_" + scalaVersion, scalaTestVersion),
                    BuildInitDependency.Companion.of("org.scalatestplus:junit-4-13_" + scalaVersion, scalaTestPlusJunitVersion)
                )
                suiteSpec.runtimeOnly("Need scala-xml at test runtime", BuildInitDependency.Companion.of("org.scala-lang.modules:scala-xml_" + scalaVersion, scalaXmlVersion))
                return suiteSpec
            }

            else -> throw IllegalArgumentException(testFramework.toString() + " is not yet supported.")
        }
    }
}

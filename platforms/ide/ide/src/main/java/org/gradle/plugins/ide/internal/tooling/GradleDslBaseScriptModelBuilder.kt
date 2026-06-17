/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.internal.GradleApiImplicitImportsProvider
import org.gradle.api.internal.classpath.GradleApiClasspathProvider
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.classpath.ClassPath
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import org.jspecify.annotations.NullMarked
import java.util.function.BiFunction
import java.util.function.BinaryOperator
import java.util.stream.Stream

@NullMarked
class GradleDslBaseScriptModelBuilder : BuildScopeModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return MODEL_NAME == modelName
    }

    override fun create(target: BuildState): Any {
        val gradle = target.getMutableModel()
        val moduleRegistry = gradle.getServices().get<ModuleRegistry?>(ModuleRegistry::class.java)
        val implicitImports = gradle.getServices().get<GradleApiImplicitImportsProvider?>(GradleApiImplicitImportsProvider::class.java)
        val apiClasspathProvider = gradle.getServices().get<GradleApiClasspathProvider?>(GradleApiClasspathProvider::class.java)

        val kotlinDslBaseScriptModel = DefaultKotlinDslBaseScriptModel(
            Companion.getKotlinScriptTemplatesClassPath(moduleRegistry!!),
            apiClasspathProvider!!.getGradleKotlinDslAbi(),
            implicitImports!!.getKotlinDslImplicitImports()
        )

        val groovyDslBaseScriptModel = DefaultGroovyDslBaseScriptModel(
            apiClasspathProvider.getGradleApi(),
            implicitImports.getGroovyDslImplicitImports()
        )

        return DefaultGradleDslBaseScriptModel(
            groovyDslBaseScriptModel,
            kotlinDslBaseScriptModel
        )
    }

    companion object {
        private val MODEL_NAME: String = GradleDslBaseScriptModel::class.java.getName()

        private fun getKotlinScriptTemplatesClassPath(moduleRegistry: ModuleRegistry): ClassPath {
            // TODO: We should allow the ModuleRegistry to generate this list instead of
            // controlling it manually. We should have a separate project for our script templates,
            // where its runtime classpath contains only dependencies we want, so when loading the
            // template module from the registry we get this list auto-generated for us.
            val moduleNames = Stream.of<String>(
                "gradle-base-services",
                "gradle-base-services-groovy",
                "gradle-core-api",
                "gradle-kotlin-dsl",
                "gradle-kotlin-dsl-shared-runtime",
                "gradle-kotlin-dsl-tooling-models",
                "kotlin-script-runtime"
            )

            return moduleNames.map<ClassPath> { name: String? -> moduleRegistry.getModule(name).getImplementationClasspath() }
                .reduce<ClassPath>(
                    ClassPath.EMPTY,
                    BiFunction { obj: ClassPath, classPath: ClassPath? -> obj.plus(classPath) },
                    BinaryOperator { obj: ClassPath?, classPath: ClassPath? -> obj!!.plus(classPath) })
        }
    }
}

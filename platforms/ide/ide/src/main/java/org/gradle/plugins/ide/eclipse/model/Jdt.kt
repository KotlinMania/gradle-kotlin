/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.JavaVersion
import org.gradle.api.internal.PropertiesTransformer
import org.gradle.plugins.ide.eclipse.model.internal.EclipseJavaVersionMapper
import org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject
import java.util.Properties

/**
 * Represents the Eclipse JDT settings.
 */
class Jdt(transformer: PropertiesTransformer?) : PropertiesPersistableConfigurationObject(transformer) {
    private var sourceCompatibility: JavaVersion? = null
    private var targetCompatibility: JavaVersion? = null

    /**
     * Sets the source compatibility for the compiler.
     */
    fun setSourceCompatibility(sourceCompatibility: JavaVersion) {
        this.sourceCompatibility = sourceCompatibility
    }

    /**
     * Sets the target compatibility for the compiler.
     */
    fun setTargetCompatibility(targetCompatibility: JavaVersion) {
        this.targetCompatibility = targetCompatibility
    }

    override fun getDefaultResourceName(): String {
        return "defaultJdtPrefs.properties"
    }

    override fun load(properties: Properties?) {
    }

    override fun store(properties: Properties) {
        val sourceVersion = EclipseJavaVersionMapper.toEclipseJavaVersion(sourceCompatibility)
        val targetVersion = EclipseJavaVersionMapper.toEclipseJavaVersion(targetCompatibility)

        properties.put("org.eclipse.jdt.core.compiler.compliance", sourceVersion)
        properties.put("org.eclipse.jdt.core.compiler.source", sourceVersion)

        if (sourceCompatibility!!.compareTo(JavaVersion.VERSION_1_3) <= 0) {
            properties.put("org.eclipse.jdt.core.compiler.problem.assertIdentifier", "ignore")
            properties.put("org.eclipse.jdt.core.compiler.problem.enumIdentifier", "ignore")
        } else if (sourceCompatibility == JavaVersion.VERSION_1_4) {
            properties.put("org.eclipse.jdt.core.compiler.problem.assertIdentifier", "error")
            properties.put("org.eclipse.jdt.core.compiler.problem.enumIdentifier", "warning")
        } else {
            properties.put("org.eclipse.jdt.core.compiler.problem.assertIdentifier", "error")
            properties.put("org.eclipse.jdt.core.compiler.problem.enumIdentifier", "error")
        }

        properties.put("org.eclipse.jdt.core.compiler.codegen.targetPlatform", targetVersion)
    }
}

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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.Action
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper.addExplicitArtifactsIfDefined
import org.gradle.internal.Actions
import org.gradle.util.internal.GUtil

class DefaultMinimalDependencyVariant : DefaultExternalModuleDependency, MinimalExternalModuleDependencyInternal, DependencyVariant {
    private var attributesMutator: Action<in AttributeContainer>? = null
    private var capabilitiesMutator: Action<in ModuleDependencyCapabilitiesHandler>? = null
    private var classifier: String? = null
    private var artifactType: String? = null

    constructor(
        delegate: MinimalExternalModuleDependency,
        attributesMutator: Action<in AttributeContainer>?,
        capabilitiesMutator: Action<in ModuleDependencyCapabilitiesHandler>?,
        classifier: String?,
        artifactType: String?
    ) : super(delegate.getModule(), DefaultMutableVersionConstraint(delegate.getVersionConstraint()), delegate.getTargetConfiguration()) {
        var attributesMutator = attributesMutator
        var capabilitiesMutator = capabilitiesMutator
        attributesMutator = GUtil.elvis<T>(attributesMutator, Actions.doNothing<AttributeContainer>())
        capabilitiesMutator = GUtil.elvis<T>(capabilitiesMutator, Actions.doNothing<ModuleDependencyCapabilitiesHandler>())

        if (delegate is DefaultMinimalDependencyVariant) {
            this.attributesMutator = Actions.composite<AttributeContainer>(delegate.attributesMutator!!, attributesMutator!!)
            this.capabilitiesMutator = Actions.composite<ModuleDependencyCapabilitiesHandler>(delegate.capabilitiesMutator!!, capabilitiesMutator!!)
            this.classifier = GUtil.elvis<String?>(classifier, delegate.getClassifier())
            this.artifactType = GUtil.elvis<String?>(classifier, delegate.getArtifactType())
        } else {
            this.attributesMutator = attributesMutator
            this.capabilitiesMutator = capabilitiesMutator
            this.classifier = classifier
            this.artifactType = artifactType
        }

        val internal = delegate as MinimalExternalModuleDependencyInternal
        setAttributesFactory(internal.getAttributesFactory())
        setCapabilityNotationParser(internal.getCapabilityNotationParser())
        setObjectFactory(internal.getObjectFactory())
    }

    private constructor(
        id: ModuleIdentifier,
        versionConstraint: MutableVersionConstraint,
        configuration: String?,
        attributesMutator: Action<in AttributeContainer>,
        capabilitiesMutator: Action<in ModuleDependencyCapabilitiesHandler>,
        classifier: String?,
        artifactType: String?
    ) : super(id, versionConstraint, configuration) {
        this.attributesMutator = attributesMutator
        this.capabilitiesMutator = capabilitiesMutator
        this.classifier = classifier
        this.artifactType = artifactType
    }

    override fun copyTo(target: AbstractExternalModuleDependency) {
        super.copyTo(target)
        if (target is DefaultMinimalDependencyVariant) {
            val depVariant = target
            depVariant.attributesMutator = attributesMutator
            depVariant.capabilitiesMutator = capabilitiesMutator
            depVariant.classifier = classifier
            depVariant.artifactType = artifactType
        } else {
            target.attributes(attributesMutator!!)
            target.capabilities(capabilitiesMutator!!)
            if (classifier != null || artifactType != null) {
                addExplicitArtifactsIfDefined(target, artifactType, classifier)
            }
        }
    }

    override fun copy(): MinimalExternalModuleDependency {
        val dependency = DefaultMinimalDependencyVariant(
            getModule(), DefaultMutableVersionConstraint(getVersionConstraint()), getTargetConfiguration(),
            attributesMutator!!, capabilitiesMutator!!, classifier, artifactType
        )
        copyTo(dependency)
        return dependency
    }

    override fun because(reason: String) {
        validateMutation()
    }

    override fun validateMutation() {
        throw UnsupportedOperationException("Minimal dependencies are immutable.")
    }

    override fun validateMutation(currentValue: Any, newValue: Any) {
        validateMutation()
    }

    override fun mutateAttributes(attributes: AttributeContainer) {
        attributesMutator!!.execute(attributes)
    }

    override fun mutateCapabilities(capabilitiesHandler: ModuleDependencyCapabilitiesHandler) {
        capabilitiesMutator!!.execute(capabilitiesHandler)
    }

    override fun getClassifier(): String? {
        return classifier
    }

    override fun getArtifactType(): String? {
        return artifactType
    }

    override fun toString(): String {
        return "DefaultMinimalDependencyVariant{" +
                ", attributesMutator=" + attributesMutator +
                ", capabilitiesMutator=" + capabilitiesMutator +
                ", classifier='" + classifier + '\'' +
                ", artifactType='" + artifactType + '\'' +
                "} " + super.toString()
    }
}

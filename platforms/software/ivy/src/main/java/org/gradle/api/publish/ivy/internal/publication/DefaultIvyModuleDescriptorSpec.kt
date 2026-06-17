/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.ivy.internal.publication

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.XmlProvider
import org.gradle.api.internal.UserCodeAction
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.IvyExtraInfoSpec
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense
import org.gradle.api.publish.ivy.internal.dependency.IvyDependency
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import org.gradle.internal.MutableActionSet
import javax.inject.Inject

abstract class DefaultIvyModuleDescriptorSpec @Inject constructor(private val objectFactory: ObjectFactory, private val ivyPublicationCoordinates: IvyPublicationCoordinates) :
    IvyModuleDescriptorSpecInternal {
    private val xmlActions = MutableActionSet<XmlProvider>()
    var status: String? = null
    var branch: String? = null
    val extraInfo: IvyExtraInfoSpec = DefaultIvyExtraInfoSpec()
    private val authors: MutableList<IvyModuleDescriptorAuthor> = ArrayList<IvyModuleDescriptorAuthor>()
    private val licenses: MutableList<IvyModuleDescriptorLicense> = ArrayList<IvyModuleDescriptorLicense>()
    private var description: IvyModuleDescriptorDescription? = null

    override fun extraInfo(namespace: String, elementName: String, value: String) {
        if (elementName == null) {
            throw InvalidUserDataException("Cannot add an extra info element with null element name")
        }
        if (namespace == null) {
            throw InvalidUserDataException("Cannot add an extra info element with null namespace")
        }
        extraInfo.add(namespace, elementName, value)
    }

    override fun getCoordinates(): IvyPublicationCoordinates {
        return ivyPublicationCoordinates
    }

    override fun withXml(action: Action<in XmlProvider>) {
        xmlActions.add(UserCodeAction<XmlProvider?>("Could not apply withXml() to Ivy module descriptor", action))
    }

    override fun getXmlAction(): Action<XmlProvider> {
        return xmlActions
    }

    abstract override fun getConfigurations(): SetProperty<IvyConfiguration>?

    abstract override fun getArtifacts(): SetProperty<IvyArtifact>?

    abstract override fun getDependencies(): SetProperty<IvyDependency>?

    abstract override fun getGlobalExcludes(): SetProperty<IvyExcludeRule>?

    override fun license(action: Action<in IvyModuleDescriptorLicense>) {
        configureAndAdd<IvyModuleDescriptorLicense>(IvyModuleDescriptorLicense::class.java, action, licenses)
    }

    override fun getLicenses(): MutableList<IvyModuleDescriptorLicense> {
        return licenses
    }

    override fun author(action: Action<in IvyModuleDescriptorAuthor>) {
        configureAndAdd<IvyModuleDescriptorAuthor>(IvyModuleDescriptorAuthor::class.java, action, authors)
    }

    override fun getAuthors(): MutableList<IvyModuleDescriptorAuthor> {
        return authors
    }

    override fun description(action: Action<in IvyModuleDescriptorDescription>) {
        if (description == null) {
            description = objectFactory.newInstance<IvyModuleDescriptorDescription>(IvyModuleDescriptorDescription::class.java)
        }
        action.execute(description)
    }

    override fun getDescription(): IvyModuleDescriptorDescription {
        return description!!
    }

    abstract override fun getWriteGradleMetadataMarker(): Property<Boolean>?

    private fun <T> configureAndAdd(clazz: Class<out T>, action: Action<in T>, items: MutableList<T?>) {
        val item: T? = objectFactory.newInstance(clazz)
        action.execute(item)
        items.add(item)
    }
}

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
package org.gradle.api.publish.maven.internal.publication

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.internal.UserCodeAction
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPomCiManagement
import org.gradle.api.publish.maven.MavenPomContributor
import org.gradle.api.publish.maven.MavenPomContributorSpec
import org.gradle.api.publish.maven.MavenPomDeveloper
import org.gradle.api.publish.maven.MavenPomDeveloperSpec
import org.gradle.api.publish.maven.MavenPomDistributionManagement
import org.gradle.api.publish.maven.MavenPomIssueManagement
import org.gradle.api.publish.maven.MavenPomLicense
import org.gradle.api.publish.maven.MavenPomLicenseSpec
import org.gradle.api.publish.maven.MavenPomMailingList
import org.gradle.api.publish.maven.MavenPomMailingListSpec
import org.gradle.api.publish.maven.MavenPomOrganization
import org.gradle.api.publish.maven.MavenPomScm
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies
import org.gradle.internal.MutableActionSet
import javax.inject.Inject

abstract class DefaultMavenPom : MavenPomInternal, MavenPomLicenseSpec, MavenPomDeveloperSpec, MavenPomContributorSpec, MavenPomMailingListSpec {
    private val xmlAction = MutableActionSet<XmlProvider>()
    private val licenses: MutableList<MavenPomLicense> = ArrayList<MavenPomLicense>()
    private var organization: MavenPomOrganization? = null
    private val developers: MutableList<MavenPomDeveloper> = ArrayList<MavenPomDeveloper>()
    private val contributors: MutableList<MavenPomContributor> = ArrayList<MavenPomContributor>()
    private var scm: MavenPomScm? = null
    private var issueManagement: MavenPomIssueManagement? = null
    private var ciManagement: MavenPomCiManagement? = null
    private var distributionManagement: MavenPomDistributionManagementInternal? = null
    private val mailingLists: MutableList<MavenPomMailingList> = ArrayList<MavenPomMailingList>()

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    override fun withXml(action: Action<in XmlProvider>) {
        xmlAction.add(UserCodeAction<XmlProvider?>("Could not apply withXml() to generated POM", action))
    }

    override fun getXmlAction(): Action<XmlProvider> {
        return xmlAction
    }

    override fun getPackaging(): String {
        return getPackagingProperty().get()
    }

    override fun setPackaging(packaging: String) {
        getPackagingProperty().set(packaging)
    }

    override fun licenses(action: Action<in MavenPomLicenseSpec>) {
        action.execute(this)
    }

    override fun license(action: Action<in MavenPomLicense>) {
        configureAndAdd<MavenPomLicense>(MavenPomLicense::class.java, action, licenses)
    }

    override fun getLicenses(): MutableList<MavenPomLicense> {
        return licenses
    }

    override fun organization(action: Action<in MavenPomOrganization>) {
        if (organization == null) {
            organization = this.objectFactory.newInstance<MavenPomOrganization>(MavenPomOrganization::class.java)
        }
        action.execute(organization)
    }

    override fun getOrganization(): MavenPomOrganization {
        return organization!!
    }

    override fun developers(action: Action<in MavenPomDeveloperSpec>) {
        action.execute(this)
    }

    override fun developer(action: Action<in MavenPomDeveloper>) {
        configureAndAdd<MavenPomDeveloper>(MavenPomDeveloper::class.java, action, developers)
    }

    override fun getDevelopers(): MutableList<MavenPomDeveloper> {
        return developers
    }

    override fun contributors(action: Action<in MavenPomContributorSpec>) {
        action.execute(this)
    }

    override fun contributor(action: Action<in MavenPomContributor>) {
        configureAndAdd<MavenPomContributor>(MavenPomContributor::class.java, action, contributors)
    }

    override fun getContributors(): MutableList<MavenPomContributor> {
        return contributors
    }

    override fun getScm(): MavenPomScm {
        return scm!!
    }

    override fun scm(action: Action<in MavenPomScm>) {
        if (scm == null) {
            scm = this.objectFactory.newInstance<MavenPomScm>(MavenPomScm::class.java)
        }
        action.execute(scm)
    }

    override fun issueManagement(action: Action<in MavenPomIssueManagement>) {
        if (issueManagement == null) {
            issueManagement = this.objectFactory.newInstance<MavenPomIssueManagement>(MavenPomIssueManagement::class.java)
        }
        action.execute(issueManagement)
    }

    override fun getIssueManagement(): MavenPomIssueManagement {
        return issueManagement!!
    }

    override fun ciManagement(action: Action<in MavenPomCiManagement>) {
        if (ciManagement == null) {
            ciManagement = this.objectFactory.newInstance<MavenPomCiManagement>(MavenPomCiManagement::class.java)
        }
        action.execute(ciManagement)
    }

    override fun getCiManagement(): MavenPomCiManagement {
        return ciManagement!!
    }

    override fun distributionManagement(action: Action<in MavenPomDistributionManagement>) {
        if (distributionManagement == null) {
            distributionManagement = this.objectFactory.newInstance<DefaultMavenPomDistributionManagement>(
                DefaultMavenPomDistributionManagement::class.java,
                this.objectFactory
            )
        }
        action.execute(distributionManagement)
    }

    override fun getDistributionManagement(): MavenPomDistributionManagementInternal {
        return distributionManagement!!
    }

    override fun mailingLists(action: Action<in MavenPomMailingListSpec>) {
        action.execute(this)
    }

    override fun mailingList(action: Action<in MavenPomMailingList>) {
        configureAndAdd<MavenPomMailingList>(MavenPomMailingList::class.java, action, mailingLists)
    }

    override fun getMailingLists(): MutableList<MavenPomMailingList> {
        return mailingLists
    }

    abstract override fun getDependencies(): Property<MavenPomDependencies>?

    private fun <T> configureAndAdd(clazz: Class<out T>, action: Action<in T>, items: MutableList<T?>) {
        val item: T? = this.objectFactory.newInstance(clazz)
        action.execute(item)
        items.add(item)
    }
}

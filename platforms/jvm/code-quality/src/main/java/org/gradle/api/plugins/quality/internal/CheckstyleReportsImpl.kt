/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.quality.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.Describable
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.quality.CheckstyleReports
import org.gradle.api.reporting.CustomizableHtmlReport
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.reporting.internal.CustomizableHtmlReportImpl
import org.gradle.api.reporting.internal.DefaultReportContainer
import org.gradle.api.reporting.internal.DefaultSingleFileReport
import org.gradle.api.reporting.internal.DelegatingReportContainer
import javax.inject.Inject

class CheckstyleReportsImpl @Inject constructor(owner: Describable, objectFactory: ObjectFactory) : DelegatingReportContainer<SingleFileReport>(
    DefaultReportContainer.create<SingleFileReport>(
        objectFactory,
        SingleFileReport::class.java,
        DefaultReportContainer.ReportGenerator { factory: DefaultReportContainer.ReportFactory<SingleFileReport>? ->
            ImmutableList.of<SingleFileReport>(
                factory!!.instantiateReport<CustomizableHtmlReportImpl>(CustomizableHtmlReportImpl::class.java, "html", owner),
                factory.instantiateReport<DefaultSingleFileReport>(DefaultSingleFileReport::class.java, "xml", owner),
                factory.instantiateReport<DefaultSingleFileReport>(DefaultSingleFileReport::class.java, "sarif", owner)
            )
        })
), CheckstyleReports {
    override fun getHtml(): CustomizableHtmlReport {
        return withType<CustomizableHtmlReport>(CustomizableHtmlReport::class.java).getByName("html")
    }

    override fun getXml(): SingleFileReport {
        return getByName("xml")
    }

    override fun getSarif(): SingleFileReport {
        return getByName("sarif")
    }
}

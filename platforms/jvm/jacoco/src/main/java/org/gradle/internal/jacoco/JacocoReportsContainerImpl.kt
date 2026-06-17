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
package org.gradle.internal.jacoco

import com.google.common.collect.ImmutableList
import org.gradle.api.Describable
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reporting.ConfigurableReport
import org.gradle.api.reporting.DirectoryReport
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.reporting.internal.DefaultReportContainer
import org.gradle.api.reporting.internal.DefaultSingleFileReport
import org.gradle.api.reporting.internal.DelegatingReportContainer
import org.gradle.api.reporting.internal.SingleDirectoryReport
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer
import javax.inject.Inject

class JacocoReportsContainerImpl @Inject constructor(owner: Describable, objectFactory: ObjectFactory) : DelegatingReportContainer<ConfigurableReport>(
    DefaultReportContainer.create<ConfigurableReport>(
        objectFactory,
        ConfigurableReport::class.java,
        DefaultReportContainer.ReportGenerator { factory: DefaultReportContainer.ReportFactory<ConfigurableReport>? ->
            ImmutableList.of<ConfigurableReport>(
                factory!!.instantiateReport<SingleDirectoryReport>(SingleDirectoryReport::class.java, "html", owner, "index.html"),
                factory.instantiateReport<DefaultSingleFileReport>(DefaultSingleFileReport::class.java, "xml", owner),
                factory.instantiateReport<DefaultSingleFileReport>(DefaultSingleFileReport::class.java, "csv", owner)
            )
        })
), JacocoReportsContainer {
    override fun getHtml(): DirectoryReport {
        return getByName("html") as DirectoryReport
    }

    override fun getXml(): SingleFileReport {
        return getByName("xml") as SingleFileReport
    }

    override fun getCsv(): SingleFileReport {
        return getByName("csv") as SingleFileReport
    }
}

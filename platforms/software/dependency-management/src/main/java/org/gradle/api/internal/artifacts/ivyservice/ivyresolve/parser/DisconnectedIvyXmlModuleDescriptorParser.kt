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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.xml.sax.SAXException
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.text.ParseException
import java.util.Date

class DisconnectedIvyXmlModuleDescriptorParser(
    private val moduleDescriptorConverter: IvyModuleDescriptorConverter?,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
    fileResourceRepository: FileResourceRepository?,
    private val metadataFactory: IvyMutableModuleMetadataFactory?
) : IvyXmlModuleDescriptorParser(
    moduleDescriptorConverter,
    moduleIdentifierFactory, fileResourceRepository,
    metadataFactory
) {
    @Throws(MalformedURLException::class)
    override fun createParser(parseContext: DescriptorParseContext?, resource: LocallyAvailableExternalResource, properties: MutableMap<String?, String?>?): Parser {
        return DisconnectedParser(parseContext, moduleDescriptorConverter, resource, resource.getFile().toURI().toURL(), properties, moduleIdentifierFactory, metadataFactory)
    }

    private class DisconnectedParser(
        parseContext: DescriptorParseContext?,
        private val moduleDescriptorConverter: IvyModuleDescriptorConverter?,
        res: ExternalResource?,
        descriptorURL: URL?,
        properties: MutableMap<String?, String?>?,
        private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
        private val metadataFactory: IvyMutableModuleMetadataFactory?
    ) : Parser(
        parseContext,
        moduleDescriptorConverter, res, descriptorURL,
        moduleIdentifierFactory,
        metadataFactory, properties
    ) {
        override fun newParser(res: ExternalResource?, descriptorURL: URL?): Parser {
            val parser: Parser = DisconnectedParser(getParseContext(), moduleDescriptorConverter, res, descriptorURL, properties, moduleIdentifierFactory, metadataFactory)
            parser.setValidate(isValidate())
            return parser
        }

        @Throws(IOException::class, ParseException::class, SAXException::class)
        override fun parseOtherIvyFile(
            parentOrganisation: String?,
            parentModule: String?, parentRevision: String?
        ): ModuleDescriptor {
            val parentMrid: ModuleRevisionId = createModuleRevisionId(parentOrganisation, parentModule, parentRevision)
            return DefaultModuleDescriptor(parentMrid, "release", Date())
        }
    }
}

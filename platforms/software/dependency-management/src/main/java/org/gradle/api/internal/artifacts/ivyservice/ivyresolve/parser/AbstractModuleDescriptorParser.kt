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

import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import java.io.File

abstract class AbstractModuleDescriptorParser<T : MutableModuleComponentResolveMetadata?>(private val fileResourceRepository: FileResourceRepository) : MetaDataParser<T?> {
    @Throws(MetaDataParseException::class)
    override fun parseMetaData(ivySettings: DescriptorParseContext?, descriptorFile: File?, validate: Boolean): MetaDataParser.ParseResult<T?>? {
        val resource = fileResourceRepository.resource(descriptorFile)
        return parseDescriptor(ivySettings, resource, validate)
    }

    @Throws(MetaDataParseException::class)
    override fun parseMetaData(ivySettings: DescriptorParseContext?, descriptorFile: File?): MetaDataParser.ParseResult<T?>? {
        return parseMetaData(ivySettings, descriptorFile, false)
    }

    @Throws(MetaDataParseException::class)
    override fun parseMetaData(ivySettings: DescriptorParseContext?, resource: LocallyAvailableExternalResource): MetaDataParser.ParseResult<T?>? {
        return parseDescriptor(ivySettings, resource, false)
    }

    @Throws(MetaDataParseException::class)
    protected fun parseDescriptor(ivySettings: DescriptorParseContext?, resource: LocallyAvailableExternalResource, validate: Boolean): MetaDataParser.ParseResult<T?>? {
        try {
            return doParseDescriptor(ivySettings, resource, validate)
        } catch (e: MetaDataParseException) {
            throw e
        } catch (e: Exception) {
            throw MetaDataParseException(this.typeName, resource, e)
        }
    }

    protected abstract val typeName: String?

    @Throws(Exception::class)
    protected abstract fun doParseDescriptor(ivySettings: DescriptorParseContext?, resource: LocallyAvailableExternalResource?, validate: Boolean): MetaDataParser.ParseResult<T?>?
}

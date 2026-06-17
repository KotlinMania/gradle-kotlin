/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.maven

import org.apache.ivy.util.ContextualSAXHandler
import org.apache.ivy.util.XMLHelper
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ResourceException
import org.gradle.internal.ErroringAction
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.ParserConfigurationException

class MavenMetadataLoader(private val cacheAwareExternalResourceAccessor: CacheAwareExternalResourceAccessor, private val resourcesFileStore: FileStore<String?>) {
    @Throws(ResourceException::class)
    fun load(metadataLocation: ExternalResourceName): MavenMetadata {
        val metadata = MavenMetadata()
        try {
            parseMavenMetadataInfo(metadataLocation, metadata)
        } catch (e: MissingResourceException) {
            throw e
        } catch (e: Exception) {
            throw ResourceException(metadataLocation.getUri(), String.format("Unable to load Maven meta-data from %s.", metadataLocation), e)
        }
        return metadata
    }

    @Throws(IOException::class)
    private fun parseMavenMetadataInfo(metadataLocation: ExternalResourceName, metadata: MavenMetadata) {
        val resource: ExternalResource? =
            cacheAwareExternalResourceAccessor.getResource(metadataLocation, null, object : CacheAwareExternalResourceAccessor.DefaultResourceFileStore<String?>(resourcesFileStore) {
                override fun computeKey(): String? {
                    return metadataLocation.toString()
                }
            }, null)
        if (resource == null) {
            throw MissingResourceException(metadataLocation.getUri(), String.format("Maven meta-data not available at %s", metadataLocation))
        }
        parseMavenMetadataInto(resource, metadata)
    }

    private fun parseMavenMetadataInto(metadataResource: ExternalResource, mavenMetadata: MavenMetadata) {
        LOGGER.debug("parsing maven-metadata: {}", metadataResource)
        metadataResource.withContent(object : ErroringAction<InputStream?>() {
            @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
            public override fun doExecute(inputStream: InputStream?) {
                XMLHelper.parse(inputStream, null, object : ContextualSAXHandler() {
                    @Throws(SAXException::class)
                    override fun endElement(uri: String?, localName: String?, qName: String?) {
                        if ("metadata/versioning/snapshot/timestamp" == getContext()) {
                            mavenMetadata.timestamp = getText()
                        }
                        if ("metadata/versioning/snapshot/buildNumber" == getContext()) {
                            mavenMetadata.buildNumber = getText()
                        }
                        if ("metadata/versioning/versions/version" == getContext()) {
                            mavenMetadata.versions.add(getText().trim { it <= ' ' })
                        }
                        super.endElement(uri, localName, qName)
                    }
                }, null)
            }
        })
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(MavenMetadataLoader::class.java)
    }
}

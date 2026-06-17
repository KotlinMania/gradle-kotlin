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
package org.gradle.internal.resource.transport.http

import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.UriTextResource
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

class ApacheDirectoryListingParser {
    @Throws(Exception::class)
    fun parse(baseURI: URI, content: InputStream, contentType: String): MutableList<String?> {
        var baseURI = baseURI
        baseURI = addTrailingSlashes(baseURI)
        if (contentType == null || !contentType.startsWith("text/html")) {
            throw ResourceException(baseURI, String.format("Unsupported ContentType %s for directory listing '%s'", contentType, baseURI))
        }
        val contentEncoding = UriTextResource.extractCharacterEncoding(contentType, StandardCharsets.UTF_8)
        val document = Jsoup.parse(content, contentEncoding.name(), baseURI.toString())
        val elements = document.select("a[href]")
        val hrefs = elements.stream()
            .map<String?> { it: Element? -> it!!.attr("href") }
            .collect(Collectors.toList())
        val uris = resolveURIs(baseURI, hrefs)
        return filterNonDirectChilds(baseURI, uris)
    }

    @Throws(IOException::class, URISyntaxException::class)
    private fun addTrailingSlashes(uri: URI): URI {
        var uri = uri
        if (uri.getPath() == null) {
            uri = URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), "/", uri.getQuery(), uri.getFragment())
        } else if (!uri.getPath().endsWith("/") && !uri.getPath().endsWith(".html")) {
            uri = URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath() + "/", uri.getQuery(), uri.getFragment())
        }
        return uri
    }

    @Throws(MalformedURLException::class)
    private fun filterNonDirectChilds(baseURI: URI, inputURIs: MutableList<URI>): MutableList<String?> {
        val baseURIPort = baseURI.getPort()
        val baseURIHost = baseURI.getHost()
        val baseURIScheme = baseURI.getScheme()

        val uris: MutableList<String?> = ArrayList<String?>()
        val prefixPath = baseURI.getPath()
        for (parsedURI in inputURIs) {
            if (parsedURI.getHost() != null && parsedURI.getHost() != baseURIHost) {
                continue
            }
            if (parsedURI.getScheme() != null && parsedURI.getScheme() != baseURIScheme) {
                continue
            }
            if (parsedURI.getPort() != baseURIPort) {
                continue
            }
            if (parsedURI.getPath() != null && !parsedURI.getPath().startsWith(prefixPath)) {
                continue
            }
            val childPathPart = parsedURI.getPath().substring(prefixPath.length, parsedURI.getPath().length)
            if (childPathPart.startsWith("../")) {
                continue
            }
            if (childPathPart == "" || childPathPart.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size > 1) {
                continue
            }

            val path = parsedURI.getPath()
            val pos = path.lastIndexOf('/')
            if (pos < 0) {
                uris.add(path)
            } else if (pos == path.length - 1) {
                val start = path.lastIndexOf('/', pos - 1)
                if (start < 0) {
                    uris.add(path.substring(0, pos))
                } else {
                    uris.add(path.substring(start + 1, pos))
                }
            } else {
                uris.add(path.substring(pos + 1))
            }
        }
        return uris
    }

    private fun resolveURIs(baseURI: URI, hrefs: MutableList<String>): MutableList<URI> {
        val uris: MutableList<URI> = ArrayList<URI>()
        for (href in hrefs) {
            try {
                uris.add(baseURI.resolve(href))
            } catch (ex: IllegalArgumentException) {
                LOGGER.debug("Cannot resolve anchor: {}", href)
            }
        }
        return uris
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ApacheDirectoryListingParser::class.java)
    }
}

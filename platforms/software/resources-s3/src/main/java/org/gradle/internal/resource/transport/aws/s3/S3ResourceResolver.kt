/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.internal.resource.transport.aws.s3

import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.google.common.base.Function
import com.google.common.base.Predicates
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import java.util.regex.Matcher
import java.util.regex.Pattern

class S3ResourceResolver {
    fun resolveResourceNames(objectListing: ObjectListing): MutableList<String?> {
        val results: MutableList<String?> = ArrayList<String?>()

        results.addAll(resolveFileResourceNames(objectListing))
        results.addAll(resolveDirectoryResourceNames(objectListing))

        return results
    }

    private fun resolveFileResourceNames(objectListing: ObjectListing): MutableList<String?> {
        val objectSummaries = objectListing.getObjectSummaries()
        if (null != objectSummaries) {
            return ImmutableList.copyOf<String?>(
                Iterables.filter<String?>(
                    Iterables.transform<S3ObjectSummary?, String?>(objectSummaries, EXTRACT_FILE_NAME),
                    Predicates.notNull<String?>()
                )
            )
        }
        return mutableListOf<String?>()
    }

    private fun resolveDirectoryResourceNames(objectListing: ObjectListing): MutableList<String?> {
        val builder = ImmutableList.builder<String?>()
        if (objectListing.getCommonPrefixes() != null) {
            for (prefix in objectListing.getCommonPrefixes()) {
                // The common prefixes will also include the prefix of the <code>ObjectListing</code>
                val directChild = prefix.split(Pattern.quote(objectListing.getPrefix()).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                if (directChild.endsWith("/")) {
                    builder.add(directChild.substring(0, directChild.length - 1))
                } else {
                    builder.add(directChild)
                }
            }
            return builder.build()
        }
        return mutableListOf<String?>()
    }

    companion object {
        private val FILENAME_PATTERN: Pattern = Pattern.compile("[^/]+\\.*$")

        private val EXTRACT_FILE_NAME: Function<S3ObjectSummary?, String?> = object : Function<S3ObjectSummary?, String?> {
            override fun apply(input: S3ObjectSummary): String? {
                val matcher: Matcher = FILENAME_PATTERN.matcher(input.getKey())
                if (matcher.find()) {
                    val group = matcher.group(0)
                    return if (group.contains(".")) group else null
                }
                return null
            }
        }
    }
}

/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.java.archives.internal

import com.google.common.collect.Sets
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeDetails
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.provider.Provider
import org.gradle.internal.file.PathToFileResolver
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.WrapUtil
import java.nio.charset.Charset

class DefaultManifestMergeSpec : ManifestMergeSpec {
    private val actions: MutableList<Action<in ManifestMergeDetails?>> = ArrayList<Action<in ManifestMergeDetails?>>()

    val mergePaths: MutableList<Any?> = ArrayList<Any?>()
    private var contentCharset: String = ManifestInternal.Companion.DEFAULT_CONTENT_CHARSET

    override fun getContentCharset(): String {
        return this.contentCharset
    }

    override fun setContentCharset(contentCharset: String) {
        if (contentCharset == null) {
            throw InvalidUserDataException("contentCharset must not be null")
        }
        if (!Charset.isSupported(contentCharset)) {
            throw InvalidUserDataException(String.format("Charset for contentCharset '%s' is not supported by your JVM", contentCharset))
        }
        this.contentCharset = contentCharset
    }

    override fun from(vararg mergePaths: Any?): ManifestMergeSpec {
        GUtil.flatten<MutableList<Any?>?>(mergePaths, this.mergePaths)
        return this
    }

    override fun eachEntry(mergeAction: Action<in ManifestMergeDetails?>?): ManifestMergeSpec {
        actions.add(mergeAction!!)
        return this
    }

    override fun eachEntry(mergeAction: Closure<*>?): ManifestMergeSpec {
        return eachEntry(ConfigureUtil.configureUsing<ManifestMergeDetails?>(mergeAction))
    }

    fun merge(baseManifest: Manifest, fileResolver: PathToFileResolver?): DefaultManifest {
        val baseContentCharset: Provider<String?>? = if (baseManifest is ManifestInternal) baseManifest.getContentCharsetProvider() else null
        var mergedManifest: DefaultManifest = DefaultManifest(fileResolver, baseContentCharset)
        mergedManifest.getAttributes().putAll(baseManifest.getAttributes())
        mergedManifest.getSections().putAll(baseManifest.getSections())
        for (mergePath in mergePaths) {
            val manifestToMerge = createManifest(mergePath, fileResolver, contentCharset)
            mergedManifest = mergeManifest(mergedManifest, manifestToMerge, fileResolver)
        }
        return mergedManifest
    }

    private fun mergeManifest(baseManifest: Manifest, toMergeManifest: Manifest, fileResolver: PathToFileResolver?): DefaultManifest {
        val mergedManifest = DefaultManifest(fileResolver)
        mergeSection(null, mergedManifest, baseManifest.getAttributes(), toMergeManifest.getAttributes())
        val allSections: MutableSet<String?> = Sets.union<String?>(baseManifest.getSections().keys, toMergeManifest.getSections().keys)
        for (section in allSections) {
            mergeSection(
                section, mergedManifest,
                GUtil.getOrDefault<Attributes?>(
                    baseManifest.getSections().get(section),
                    org.gradle.internal.Factory { org.gradle.api.java.archives.internal.DefaultAttributes() })!!,
                GUtil.getOrDefault<Attributes?>(
                    toMergeManifest.getSections().get(section),
                    org.gradle.internal.Factory { org.gradle.api.java.archives.internal.DefaultAttributes() })!!
            )
        }
        return mergedManifest
    }

    private fun mergeSection(section: String?, mergedManifest: Manifest, baseAttributes: Attributes, mergeAttributes: Attributes) {
        val mergeOnlyAttributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>(mergeAttributes)
        val mergeDetailsSet: MutableSet<DefaultManifestMergeDetails> = LinkedHashSet<DefaultManifestMergeDetails>()

        for (baseEntry in baseAttributes.entries) {
            val mergeValue = mergeAttributes.get(baseEntry.key)
            mergeDetailsSet.add(getMergeDetails(section, baseEntry.key, baseEntry.value, mergeValue))
            mergeOnlyAttributes.remove(baseEntry.key)
        }
        for (mergeEntry in mergeOnlyAttributes.entries) {
            mergeDetailsSet.add(getMergeDetails(section, mergeEntry.key, null, mergeEntry.value))
        }

        for (mergeDetails in mergeDetailsSet) {
            for (action in actions) {
                action.execute(mergeDetails)
            }
            addMergeDetailToManifest(section, mergedManifest, mergeDetails)
        }
    }

    private fun getMergeDetails(section: String?, key: String?, baseValue: Any?, mergeValue: Any?): DefaultManifestMergeDetails {
        val baseValueString: String? = resolveValueToString(baseValue)
        val mergeValueString: String? = resolveValueToString(mergeValue)
        val value = if (mergeValueString == null) baseValueString else mergeValueString
        return DefaultManifestMergeDetails(section, key, baseValueString, mergeValueString, value)
    }

    private fun addMergeDetailToManifest(section: String?, mergedManifest: Manifest, mergeDetails: DefaultManifestMergeDetails) {
        if (!mergeDetails.isExcluded()) {
            if (section == null) {
                mergedManifest.attributes(WrapUtil.toMap<String?, String?>(mergeDetails.getKey(), mergeDetails.getValue()))
            } else {
                mergedManifest.attributes(WrapUtil.toMap<String?, String?>(mergeDetails.getKey(), mergeDetails.getValue()), section)
            }
        }
    }

    private fun createManifest(mergePath: Any?, fileResolver: PathToFileResolver?, contentCharset: String?): Manifest {
        if (mergePath is Manifest) {
            return mergePath.getEffectiveManifest()
        }
        return DefaultManifest(fileResolver, contentCharset).read(mergePath)
    }

    companion object {
        private fun resolveValueToString(value: Any?): String? {
            if (value == null) {
                return null
            } else if (value is Provider<*>) {
                val providedValue: Any? = value.getOrNull()
                return resolveValueToString(providedValue)
            } else {
                return value.toString()
            }
        }
    }
}

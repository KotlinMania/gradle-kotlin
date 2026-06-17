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
package org.gradle.ide.xcode.internal.xcodeproj

import com.google.common.base.Optional
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.util.Collections

/**
 * List of build configurations.
 */
class XCConfigurationList : PBXProjectItem() {
    val buildConfigurationsByName: LoadingCache<String?, XCBuildConfiguration?>
    private val buildConfigurations: MutableList<XCBuildConfiguration?>
    private val defaultConfigurationName: Optional<String?>
    private val defaultConfigurationIsVisible = false

    init {
        buildConfigurations = ArrayList<XCBuildConfiguration?>()
        defaultConfigurationName = Optional.absent<String?>()

        buildConfigurationsByName = CacheBuilder.newBuilder().build<String?, XCBuildConfiguration?>(
            object : CacheLoader<String?, XCBuildConfiguration?>() {
                @Throws(Exception::class)
                override fun load(key: String?): XCBuildConfiguration {
                    val configuration = XCBuildConfiguration(key)
                    buildConfigurations.add(configuration)
                    return configuration
                }
            })
    }

    override fun isa(): String {
        return "XCConfigurationList"
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        Collections.sort<XCBuildConfiguration?>(buildConfigurations, object : Comparator<XCBuildConfiguration?> {
            override fun compare(o1: XCBuildConfiguration, o2: XCBuildConfiguration): Int {
                return o1.getName().compareTo(o2.getName())
            }
        })
        s.addField("buildConfigurations", buildConfigurations)

        if (defaultConfigurationName.isPresent()) {
            s.addField("defaultConfigurationName", defaultConfigurationName.get())
        }
        s.addField("defaultConfigurationIsVisible", defaultConfigurationIsVisible)
    }
}

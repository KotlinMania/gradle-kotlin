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

import com.google.common.base.Preconditions

/**
 * Information for building a specific artifact (a library, binary, or test).
 */
abstract class PBXTarget(name: String?, productType: ProductType?) : PBXProjectItem() {
    val name: String
    val productType: ProductType?
    val buildPhases: MutableList<PBXBuildPhase?>
    val buildConfigurationList: XCConfigurationList?
    var productName: String? = null
    var productReference: PBXFileReference? = null

    init {
        this.name = Preconditions.checkNotNull<String>(name)
        this.productType = Preconditions.checkNotNull<ProductType?>(productType)
        this.buildPhases = ArrayList<PBXBuildPhase?>()
        this.buildConfigurationList = XCConfigurationList()
    }

    override fun isa(): String? {
        return "PBXTarget"
    }

    override fun stableHash(): Int {
        return name.hashCode()
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        s.addField("name", name)
        if (productType != null) {
            s.addField("productType", productType.toString())
        }
        if (productName != null) {
            s.addField("productName", productName)
        }
        if (productReference != null) {
            s.addField("productReference", productReference)
        }
        s.addField("buildPhases", buildPhases)
        if (buildConfigurationList != null) {
            s.addField("buildConfigurationList", buildConfigurationList)
        }
    }

    enum class ProductType(identifier: String) {
        INDEXER("org.gradle.product-type.indexer"),

        STATIC_LIBRARY("com.apple.product-type.library.static"),
        DYNAMIC_LIBRARY("com.apple.product-type.library.dynamic"),
        TOOL("com.apple.product-type.tool"),
        BUNDLE("com.apple.product-type.bundle"),
        FRAMEWORK("com.apple.product-type.framework"),
        STATIC_FRAMEWORK("com.apple.product-type.framework.static"),
        APPLICATION("com.apple.product-type.application"),
        UNIT_TEST("com.apple.product-type.bundle.unit-test"),
        IN_APP_PURCHASE_CONTENT("com.apple.product-type.in-app-purchase-content"),
        APP_EXTENSION("com.apple.product-type.app-extension"),
        WATCH_OS1_APPLICATION("com.apple.product-type.application.watchapp"),
        WATCH_OS1_EXTENSION("com.apple.product-type.watchkit-extension");

        val identifier: String?

        init {
            this.identifier = identifier
        }

        override fun toString(): String {
            return identifier!!
        }
    }
}

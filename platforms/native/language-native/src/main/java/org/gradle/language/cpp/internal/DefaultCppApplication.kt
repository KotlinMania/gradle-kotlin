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
package org.gradle.language.cpp.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.language.ComponentDependencies
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.internal.DefaultComponentDependencies
import org.gradle.language.nativeplatform.internal.PublicationAwareComponent
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import javax.inject.Inject

abstract class DefaultCppApplication @Inject constructor(name: String?) : DefaultCppComponent(name), CppApplication, PublicationAwareComponent {
    private val mainVariant: MainExecutableVariant
    private val dependencies: DefaultComponentDependencies

    init {
        this.dependencies = getObjectFactory().newInstance<DefaultComponentDependencies>(DefaultComponentDependencies::class.java, getNames().withSuffix("implementation"))
        this.mainVariant = MainExecutableVariant(getObjectFactory())
    }

    fun addExecutable(identity: NativeVariantIdentity, targetPlatform: CppPlatform?, toolChain: NativeToolChainInternal?, platformToolProvider: PlatformToolProvider?): DefaultCppExecutable {
        val result = getObjectFactory().newInstance<DefaultCppExecutable>(
            DefaultCppExecutable::class.java,
            getNames().append(identity.getName()),
            getBaseName(),
            getCppSource(),
            getPrivateHeaderDirs(),
            getImplementationDependencies()!!,
            targetPlatform!!,
            toolChain!!,
            platformToolProvider!!,
            identity
        )
        getBinaries().add(result)
        return result
    }

    override fun getDisplayName(): DisplayName {
        return Describables.withTypeAndName("C++ application", getName())
    }

    override fun getImplementationDependencies(): Configuration? {
        return dependencies.getImplementationDependencies()
    }

    override fun getDependencies(): ComponentDependencies {
        return dependencies
    }

    fun dependencies(action: Action<in ComponentDependencies?>) {
        action.execute(dependencies)
    }

    override fun getMainPublication(): MainExecutableVariant {
        return mainVariant
    }

    abstract override fun getDevelopmentBinary(): Property<CppExecutable?>?
}

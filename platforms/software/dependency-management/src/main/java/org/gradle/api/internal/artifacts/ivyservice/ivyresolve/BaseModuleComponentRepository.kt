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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.internal.action.InstantiatingAction

open class BaseModuleComponentRepository<T> : ModuleComponentRepository<T?> {
    protected val delegate: ModuleComponentRepository<T?>
    private val localAccess: ModuleComponentRepositoryAccess<T?>?
    private val remoteAccess: ModuleComponentRepositoryAccess<T?>?

    constructor(delegate: ModuleComponentRepository<T?>, localAccess: ModuleComponentRepositoryAccess<T?>?, remoteAccess: ModuleComponentRepositoryAccess<T?>?) {
        this.delegate = delegate
        this.localAccess = localAccess
        this.remoteAccess = remoteAccess
    }

    constructor(delegate: ModuleComponentRepository<T?>) {
        this.delegate = delegate
        this.localAccess = delegate.getLocalAccess()
        this.remoteAccess = delegate.getRemoteAccess()
    }

    override fun toString(): String {
        return delegate.toString()
    }

    override fun getId(): String? {
        return delegate.getId()
    }

    override fun getName(): String? {
        return delegate.getName()
    }

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<T?>? {
        return localAccess
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<T?>? {
        return remoteAccess
    }

    override fun getArtifactCache(): MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>? {
        return delegate.getArtifactCache()
    }

    override fun getComponentMetadataSupplier(): InstantiatingAction<ComponentMetadataSupplierDetails?>? {
        return delegate.getComponentMetadataSupplier()
    }

    override fun isContinueOnConnectionFailure(): Boolean {
        return delegate.isContinueOnConnectionFailure()
    }

    override fun isRepositoryDisabled(): Boolean {
        return delegate.isRepositoryDisabled()
    }
}

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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.scan.UsedByScanPlugin

/**
 * A component identifier for a Maven unique snapshot module.
 */
@UsedByScanPlugin("scan")
class MavenUniqueSnapshotComponentIdentifier : DefaultModuleComponentIdentifier {
    @JvmField
    val timestamp: String
    private val hashCode: Int

    constructor(module: ModuleIdentifier, version: String, timestamp: String) : super(module, version) {
        this.timestamp = timestamp
        this.hashCode = super.hashCode() + timestamp.hashCode()
    }

    constructor(baseIdentifier: ModuleComponentIdentifier, timestamp: String) : super(baseIdentifier.getModuleIdentifier(), baseIdentifier.getVersion()) {
        this.timestamp = timestamp
        this.hashCode = super.hashCode() + timestamp.hashCode()
    }

    public override fun equals(o: Any): Boolean {
        return super.equals(o) && (o as MavenUniqueSnapshotComponentIdentifier).timestamp == timestamp
    }

    public override fun hashCode(): Int {
        return hashCode
    }

    public override fun getDisplayName(): String {
        return String.format("%s:%s:%s:%s", getGroup(), getModule(), this.snapshotVersion, timestamp)
    }

    val snapshotVersion: String
        get() = getVersion().replace(timestamp, "SNAPSHOT")

    val snapshotComponent: ModuleComponentIdentifier
        get() = newId(getModuleIdentifier(), this.snapshotVersion)

    val timestampedVersion: String
        get() = getVersion().replace("SNAPSHOT", timestamp)
}

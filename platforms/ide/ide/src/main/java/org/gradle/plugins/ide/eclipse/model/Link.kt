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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.base.Objects
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil

/**
 * Link.
 */
class Link(name: String, type: String, location: String?, locationUri: String?) {
    var name: String
    var type: String
    var location: String?
    var locationUri: String?

    init {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name))
        Preconditions.checkArgument(!Strings.isNullOrEmpty(type))
        Preconditions.checkArgument(Strings.isNullOrEmpty(location) || Strings.isNullOrEmpty(locationUri))
        Preconditions.checkArgument(!Strings.isNullOrEmpty(location) || !Strings.isNullOrEmpty(locationUri))
        this.name = name
        this.type = type
        this.location = PathUtil.normalizePath(Strings.emptyToNull(location))
        this.locationUri = Strings.emptyToNull(locationUri)
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val link = o as Link
        return Objects.equal(name, link.name)
                && Objects.equal(type, link.type)
                && Objects.equal(location, link.location)
                && Objects.equal(locationUri, link.locationUri)
    }

    override fun hashCode(): Int {
        var result: Int
        result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (if (location != null) location.hashCode() else 0)
        result = 31 * result + (if (locationUri != null) locationUri.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return ("Link{"
                + "name='" + name + '\''
                + ", type='" + type + '\''
                + ", location='" + location + '\''
                + ", locationUri='" + locationUri + '\''
                + '}')
    }
}

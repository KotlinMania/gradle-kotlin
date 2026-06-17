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
/**
 * Model classes for managing language sources.
 */
package org.gradle.language.nativeplatform

import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Cast.cast
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.isWindows
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.os.OperatingSystem.isMacOsX
import org.gradle.internal.os.OperatingSystem.isLinux
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.util.internal.CollectionUtils.filter
import org.gradle.internal.serialize.BaseSerializerFactory.getSerializerFor
import org.gradle.internal.serialize.ListSerializer.read
import org.gradle.internal.serialize.AbstractCollectionSerializer.read
import org.gradle.internal.serialize.ListSerializer.write
import org.gradle.internal.serialize.AbstractCollectionSerializer.write
import org.gradle.internal.serialize.Decoder.readByte
import org.gradle.internal.serialize.Decoder.readNullableString
import org.gradle.internal.serialize.Serializer.read
import org.gradle.internal.serialize.Encoder.writeByte
import org.gradle.internal.serialize.Encoder.writeNullableString
import org.gradle.internal.serialize.Serializer.write
import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.serialize.Decoder.readBoolean
import org.gradle.internal.serialize.Decoder.readSmallInt
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.internal.serialize.Encoder.writeBoolean
import org.gradle.internal.serialize.Encoder.writeSmallInt


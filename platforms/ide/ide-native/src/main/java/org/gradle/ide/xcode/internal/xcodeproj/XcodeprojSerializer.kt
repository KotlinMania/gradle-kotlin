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

import com.dd.plist.NSArray
import com.dd.plist.NSDictionary
import com.dd.plist.NSObject
import com.dd.plist.NSString
import org.gradle.api.logging.Logging.getLogger
import javax.annotation.concurrent.NotThreadSafe

/**
 * Serializer that handles conversion of an in-memory object graph representation of an xcode
 * project (instances of [PBXObject]) into an Apple property list.
 *
 * Serialization proceeds from the root object, a ${link PBXProject} instance, to all of its
 * referenced objects. Each object being visited calls back into this class ([.addField]) to
 * populate the plist representation with its fields.
 */
@NotThreadSafe
class XcodeprojSerializer(private val gidGenerator: GidGenerator, private val rootObject: PBXProject) {
    private val objects: NSDictionary
    private var currentObject: NSDictionary? = null

    init {
        objects = NSDictionary()
    }

    /**
     * Generate a plist serialization of project bound to this serializer.
     */
    fun toPlist(): NSDictionary {
        serializeObject(rootObject)

        val root = NSDictionary()
        root.put("archiveVersion", "1")
        root.put("classes", NSDictionary())
        root.put("objectVersion", "46")
        root.put("objects", objects)
        root.put("rootObject", rootObject.getGlobalID())

        return root
    }

    /**
     * Serialize a [PBXObject] and its recursive descendants into the object dictionary.
     *
     * @return the GID of the serialized object
     * @see PBXObject.serializeInto
     */
    private fun serializeObject(obj: PBXObject): String? {
        if (obj.getGlobalID() == null) {
            obj.setGlobalID(obj.generateGid(gidGenerator))
            LOG!!.trace("Set new object GID: {}", obj)
        } else {
            // Check that the object has already been serialized.
            val `object` = objects.get(obj.getGlobalID())
            if (`object` != null) {
                LOG!!.trace("Object {} found, returning existing object {}", obj, `object`)
                return obj.getGlobalID()
            } else {
                LOG!!.trace("Object already had GID set: {}", obj)
            }
        }

        // Save the existing object being deserialized.
        val stack = currentObject!!

        currentObject = NSDictionary()
        currentObject!!.put("isa", obj.isa())
        obj.serializeInto(this)
        objects.put(obj.getGlobalID(), currentObject)

        // Restore the existing object being deserialized.
        currentObject = stack
        return obj.getGlobalID()
    }

    fun addField(name: String?, obj: PBXObject?) {
        if (obj != null) {
            val gid = serializeObject(obj)
            currentObject!!.put(name, gid)
        }
    }

    fun addField(name: String?, `val`: Int) {
        currentObject!!.put(name, `val`)
    }

    fun addField(name: String?, `val`: String?) {
        if (`val` != null) {
            currentObject!!.put(name, `val`)
        }
    }

    fun addField(name: String?, `val`: Boolean) {
        currentObject!!.put(name, `val`)
    }

    fun addField(name: String?, objectList: MutableList<out PBXObject?>) {
        val array = NSArray(objectList.size)
        for (i in objectList.indices) {
            val gid = serializeObject(objectList.get(i)!!)
            array.setValue(i, NSString(gid))
        }
        currentObject!!.put(name, array)
    }

    fun addField(name: String?, v: NSObject?) {
        if (v != null) {
            currentObject!!.put(name, v)
        }
    }

    companion object {
        private val LOG = getLogger(XcodeprojSerializer::class.java)
    }
}

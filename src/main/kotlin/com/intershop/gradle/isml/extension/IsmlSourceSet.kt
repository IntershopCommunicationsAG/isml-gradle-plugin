/*
 * Copyright 2018 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intershop.gradle.isml.extension

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

class IsmlSourceSet(project: Project, val srcname: String) : Named {

    override fun getName() : String {
        return srcname
    }

    private val srcDirectoryProperty: DirectoryProperty = project.layout.directoryProperty()
    private val outputDirProperty: DirectoryProperty = project.layout.directoryProperty()

    // Jsp Package name
    private val jspPackageProperty: Property<String> = project.objects.property(String::class.java)

    init {
        outputDirProperty.set(project.layout.buildDirectory.dir(IsmlExtension.ISML_OUTPUTPATH))
    }

    // isml source directory
    val srcDirectoryProvider: Provider<Directory>
        get() = srcDirectoryProperty

    var srcDir: File
        get() = srcDirectoryProperty.get().asFile
        set(value) = srcDirectoryProperty.set(value)

    // output directory
    val outputDirProvider: Provider<Directory>
        get() = outputDirProperty

    var outputDir: File
        get() = outputDirProperty.get().asFile
        set(value) = outputDirProperty.set(value)

    /**
     * Jsp Package name
     */
    val jspPackageProvider: Provider<String>
        get() = jspPackageProperty

    var jspPackage: String
        get() = jspPackageProperty.get()
        set(value) = jspPackageProperty.set(value)

    // Tasknames
    fun getIsmlTaskName(): String {
        return "isml2class${srcname.toCamelCase()}"
    }

    private fun String.toCamelCase() : String {
        return split(" ").joinToString("") { it.capitalize() }
    }
}
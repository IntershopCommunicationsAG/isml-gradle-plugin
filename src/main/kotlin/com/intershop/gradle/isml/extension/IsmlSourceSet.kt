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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import java.io.File


class IsmlSourceSet(project: Project, val srcname: String) : Named {

    override fun getName() : String {
        return srcname
    }

    val srcDirectoryProvider: DirectoryProperty = project.layout.directoryProperty()
    val outputDirProvider: DirectoryProperty = project.layout.directoryProperty()

    // Jsp Package name
    val jspPackageProvider: Property<String> = project.objects.property(String::class.java)

    init {
        outputDirProvider.set(project.layout.buildDirectory.dir(IsmlExtension.ISML_OUTPUTPATH))
    }

    var srcDir: File
        get() {
            return srcDirectoryProvider.get().asFile
        }
        set(value) {
            this.srcDirectoryProvider.set(value)
        }

    var outputDir: File
        get() {
            return outputDirProvider.get().asFile
        }
        set(value) {
            this.outputDirProvider.set(value)
        }

    /**
     * Jsp Package name
     */
     var jspPackage: String
        get() {
            return jspPackageProvider.get()
        }
        set(value) {
            jspPackageProvider.set(value)
        }

    // Tasknames
    fun getIsmlTaskName(): String {
        return "isml2class${srcname.toCamelCase()}"
    }

    private fun String.toCamelCase() : String {
        return split("_").joinToString("") { it.capitalize() }
    }
}
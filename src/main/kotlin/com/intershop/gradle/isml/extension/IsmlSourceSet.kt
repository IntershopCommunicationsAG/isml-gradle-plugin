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

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import java.io.File
import java.util.*

/**
 * Configuration container for a special ISMl
 * source set to compile isml included files.
 */
open class IsmlSourceSet(objectFactory: ObjectFactory,
                         projectLayout: ProjectLayout, @Internal val name: String) {

    private val srcDirectoryProperty: DirectoryProperty = objectFactory.directoryProperty()
    private val outputDirProperty: DirectoryProperty = objectFactory.directoryProperty()

    // Jsp Package name
    private val jspPackageProperty: Property<String> = objectFactory.property(String::class.java)

    init {
        outputDirProperty.set(projectLayout.buildDirectory.dir(IsmlExtension.ISML_OUTPUTPATH))
    }

    /**
     * Provider for srcDir property.
     */
    val srcDirectoryProvider: Provider<Directory>
        get() = srcDirectoryProperty

    /**
     * Source directory with ISM files.
     *
     * @property srcDir
     */
    var srcDir: File
        get() = srcDirectoryProperty.get().asFile
        set(value) = srcDirectoryProperty.set(value)

    /**
     * Provider for output directory property.
     */
    val outputDirProvider: Provider<Directory>
        get() = outputDirProperty

    /**
     * Output directory with class and jsp files.
     *
     * @property outputDir
     */
    var outputDir: File
        get() = outputDirProperty.get().asFile
        set(value) = outputDirProperty.set(value)

    /**
     * Jsp Package name provider.
     */
    val jspPackageProvider: Provider<String>
        get() = jspPackageProperty

    /**
     * Jsp Package name property.
     */
    var jspPackage: String
        get() = jspPackageProperty.get()
        set(value) = jspPackageProperty.set(value)

    /**
     * Calculate the task name for the isml task.
     * @return task name for configuration
     */
    fun getIsmlTaskName(): String {
        return "isml2jsp${name.toCamelCase()}"
    }

    /**
     * Calculate the task name for the jsp task.
     * @return task name for configuration
     */
    fun getJspTaskName(): String {
        return "jsp2java${name.toCamelCase()}"
    }

    private fun String.toCamelCase() : String {
        return split(" ").joinToString("") {
            it.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }
}

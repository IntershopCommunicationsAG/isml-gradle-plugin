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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import java.util.Locale

/**
 * Configuration container for a special ISMl
 * source set to compile isml included files.
 * @param objectFactory
 * @param projectLayout
 * @param name  Name of the sourceset with isml files.
 */
open class IsmlSourceSet(objectFactory: ObjectFactory,
                         projectLayout: ProjectLayout, @Internal val name: String) {

    /**
     * Source directory with ISM files.
     *
     * @property srcDir
     */
    val srcDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Output directory with jsp files.
     *
     * @property ismlOutputDir
     */
    val ismlOutputDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Output directory with jsp files.
     *
     * @property jspOutputDir
     */
    val jspOutputDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Jsp Package name property.
     */
    val jspPackage: Property<String> = objectFactory.property(String::class.java)

    init {
        ismlOutputDir.set(projectLayout.buildDirectory.dir(IsmlExtension.ISML_OUTPUTPATH))
        jspOutputDir.set(projectLayout.buildDirectory.dir(IsmlExtension.JSP_OUTPUTPATH))
    }

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

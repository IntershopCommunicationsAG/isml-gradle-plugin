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
package com.intershop.gradle.isml

import com.intershop.gradle.isml.extension.IsmlExtension
import com.intershop.gradle.isml.tasks.PrepareTagLibs
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin Class implementation.
 */
class IsmlTagLibPlugin : Plugin<Project> {

    companion object {
        const val TASKDESCRIPTION = "Prepare directory with tag libs for ISML compilation"
        const val TASKNAME = "prepareTagLibs"
    }

    override fun apply(project: Project) {
        with(project) {
            logger.info("ISML TagLib plugin adds extension {} to {}", IsmlExtension.ISML_EXTENSION_NAME, project.name)
            val extension = extensions.findByType(IsmlExtension::class.java) ?:
                        project.extensions.create(IsmlExtension.ISML_EXTENSION_NAME, IsmlExtension::class.java, project)

            tasks.maybeCreate(TASKNAME, PrepareTagLibs::class.java).apply {
                description = TASKDESCRIPTION
                group = IsmlExtension.ISML_GROUP_NAME

                provideIsmlConfiguration(extension.ismlConfigurationNameProvider)
                provideOutputDir(extension.taglibFolderProvider)
            }
        }
    }
}

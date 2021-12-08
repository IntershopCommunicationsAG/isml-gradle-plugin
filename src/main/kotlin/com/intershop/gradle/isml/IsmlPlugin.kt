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
import com.intershop.gradle.isml.tasks.Isml2Jsp
import com.intershop.gradle.isml.tasks.Jsp2Java
import com.intershop.gradle.isml.tasks.PrepareTagLibs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import java.util.*

/**
 * Plugin Class implementation.
 */
open class IsmlPlugin : Plugin<Project> {

    companion object {
        /**
         * Task description of this task.
         */
        const val TASKDESCRIPTION = "Compiles ISML template files to java files"

        /**
         * Task name of this task.
         */
        const val TASKNAME = "isml"

        /**
         * Variable for default page compile folder name.
         */
        const val PAGECOMPILE_FOLDER = "pagecompile"
    }

    override fun apply(project: Project) {
        with(project) {
            logger.info("Isml plugin adds extension {} to {}", IsmlExtension.ISML_EXTENSION_NAME, name)
            val extension = extensions.findByType(IsmlExtension::class.java) ?: extensions.create(
                IsmlExtension.ISML_EXTENSION_NAME,
                IsmlExtension::class.java
            )

            addJSPJasperCompilerConfiguration(this, extension)

            if (extension.sourceSets.findByName(IsmlExtension.ISML_MAIN_SOURCESET) == null) {
                val mainIsmlSourceSet = extension.sourceSets.create(IsmlExtension.ISML_MAIN_SOURCESET)
                mainIsmlSourceSet.srcDir.set(layout.projectDirectory.dir(IsmlExtension.MAIN_TEMPLATE_PATH))
                mainIsmlSourceSet.ismlOutputDir.set(layout.buildDirectory.dir(
                    "${IsmlExtension.ISML_OUTPUTPATH}/${IsmlExtension.ISML_MAIN_SOURCESET}"
                ))
                mainIsmlSourceSet.jspOutputDir.set(layout.buildDirectory.dir(
                    "${IsmlExtension.JSP_OUTPUTPATH}/${IsmlExtension.ISML_MAIN_SOURCESET}"
                ))
                mainIsmlSourceSet.jspPackage.set("org.apache.jsp.${project.name}")
            }

            configureTask(this, extension)
        }
    }

    private fun configureTask(project: Project, extension: IsmlExtension) {
        with(project) {
            val ismlMain = tasks.register(TASKNAME) {
                it.description = TASKDESCRIPTION
                it.group = IsmlExtension.ISML_GROUP_NAME
            }

            addIsmlConfiguration(this)
            addJSPJasperCompilerConfiguration(this, extension)
            extension.sourceSets.all { ismlSourceSet ->

                val ismlTask = tasks.register(ismlSourceSet.getIsmlTaskName(), Isml2Jsp::class.java) { ismltask ->
                    ismltask.group = IsmlExtension.ISML_GROUP_NAME

                    ismltask.provideInputDir(ismlSourceSet.srcDir)
                    ismltask.outputDir.set( project.layout.buildDirectory.dir("generated/isml/${ismlSourceSet.name}") )
                    ismltask.provideEncoding(extension.encodingProvider)
                }
                val jspTask = tasks.register(ismlSourceSet.getJspTaskName(), Jsp2Java::class.java) { jsptask ->
                    jsptask.group = IsmlExtension.ISML_GROUP_NAME

                    jsptask.provideInputDir(project.provider { ismlTask.get().outputDir.get() })

                    jsptask.outputDir.set( project.layout.buildDirectory.dir("generated/jsp/${ismlSourceSet.name}"))
                    jsptask.provideJspPackage(ismlSourceSet.jspPackageProvider)

                    jsptask.provideSourceCompatibility(extension.sourceCompatibilityProvider)
                    jsptask.provideTargetCompatibility(extension.targetCompatibilityProvider)
                    jsptask.provideEncoding(extension.encodingProvider)

                    jsptask.provideEnableTldScan(extension.enableTldScanProvider)
                    jsptask.provideEncoding(extension.encodingProvider)

                    jsptask.provideSourceCompatibility(extension.sourceCompatibilityProvider)
                    jsptask.provideTargetCompatibility(extension.targetCompatibilityProvider)

                    jsptask.provideSourceSetName(extension.sourceSetNameProvider)

                    project.plugins.withType(JavaBasePlugin::class.java) {
                        project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.matching {
                            it.name == SourceSet.MAIN_SOURCE_SET_NAME
                        }.forEach {
                            it.java.srcDir(jsptask.outputs)
                        }
                    }
                    project.plugins.withType(IsmlTagLibPlugin::class.java) {
                        val ismlTagLib = tasks.named(IsmlTagLibPlugin.TASKNAME, PrepareTagLibs::class.java)
                        jsptask.tagLibsInputDir.set(project.provider { ismlTagLib.get().outputDir.get() })
                        jsptask.dependsOn(ismlTagLib)
                    }
                    jsptask.dependsOn(ismlTask)
                }

                ismlMain.configure {
                    it.dependsOn(jspTask)
                }
            }
        }
    }

    private fun addIsmlConfiguration(project: Project) {
        val configuration = project.configurations.maybeCreate("isml")
        configuration
            .setVisible(false)
            .setTransitive(true)
            .setDescription("ISML parser configuration is used for jsp generation")
            .defaultDependencies { ds ->
                // this will be executed if configuration is empty
                val dependencyHandler = project.dependencies
                ds.add(dependencyHandler.create("com.intershop.icm:isml-parser:1.0.0-SNAPSHOT"))
            }
    }

    private fun addJSPJasperCompilerConfiguration(project: Project, extension: IsmlExtension) {
        val configuration = project.configurations.maybeCreate(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME)
        configuration.setVisible(false)
                .setTransitive(true)
                .setDescription("Configuration for JSP compiler")
                .defaultDependencies { ds ->
                    val dependencyHandler = project.dependencies
                    ds.add(dependencyHandler.create("org.apache.tomcat:tomcat-jasper:".
                            plus(extension.jspCompilerVersion)))
                    ds.removeIf {it.group == "ch.qos.logback" && it.name == "logback-classic" }
                }
    }
}

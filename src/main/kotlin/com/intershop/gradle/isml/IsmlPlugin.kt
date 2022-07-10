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
import com.intershop.gradle.isml.extension.IsmlSourceSet
import com.intershop.gradle.isml.tasks.Isml2Jsp
import com.intershop.gradle.isml.tasks.Jsp2Java
import com.intershop.gradle.resourcelist.extension.ResourceListExtension
import com.intershop.gradle.resourcelist.task.ResourceListFileTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
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
    }

    override fun apply(project: Project) {
        with(project) {
            logger.info("Isml plugin adds extension {} to {}", IsmlExtension.ISML_EXTENSION_NAME, name)
            val extension = extensions.findByType(IsmlExtension::class.java) ?: extensions.create(
                IsmlExtension.ISML_EXTENSION_NAME,
                IsmlExtension::class.java
            )

            addJSPJasperCompilerConfiguration(this, extension)
            addIsmlConfiguration(this, extension)

            afterEvaluate {
                plugins.withType(JavaBasePlugin::class.java) {
                    project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.matching {
                        it.name == SourceSet.MAIN_SOURCE_SET_NAME
                    }.forEach {
                        addJavaDependencies(this, it.implementationConfigurationName)
                    }
                }
            }

            if (extension.sourceSets.findByName(IsmlExtension.ISML_MAIN_SOURCESET) == null) {
                val mainIsmlSourceSet = extension.sourceSets.create(IsmlExtension.ISML_MAIN_SOURCESET)
                mainIsmlSourceSet.srcDir.set(
                    layout.projectDirectory.dir(IsmlExtension.MAIN_TEMPLATE_PATH + "/" + project.name))
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

            val ismlSourceJar = tasks.register("ismlSourcesJar", Jar::class.java) { sourceJar ->
                sourceJar.group = IsmlExtension.ISML_GROUP_NAME
                sourceJar.archiveClassifier.set("ismlSources")
            }

            extension.sourceSets.all { ismlSourceSet ->

                val ismlTask = configureISMLTask(this, extension, ismlSourceSet)
                val jspTask = cnfigureJSPTask(this, extension, ismlSourceSet, ismlTask)

                ismlSourceJar.configure {
                    it.from(ismlSourceSet.srcDir)
                }

                tasks.whenTaskAdded { task ->
                    if (task.name == "sourcesJar" && task is Jar) {
                        task.exclude("default/**/*.jsp")
                        task.exclude("org/apache/jsp/")
                        task.includeEmptyDirs = false
                    }
                }

                this.afterEvaluate {
                    plugins.withType(JavaBasePlugin::class.java) {
                        extensions.getByType(JavaPluginExtension::class.java).sourceSets.matching {
                            it.name == SourceSet.MAIN_SOURCE_SET_NAME
                        }.forEach {
                            it.java.srcDir(jspTask)

                            val ismlListTask = createISMLResourceTask(project).get()
                            it.java.srcDir(ismlTask.get().outputDir.get())
                            it.output.dir(ismlListTask.outputs)
                            ismlListTask.dependsOn(ismlTask)
                        }

                    }
                }

                ismlMain.configure {
                    it.dependsOn(jspTask)
                }
            }
        }
    }

    private fun configureISMLTask(project: Project,
                                  extension: IsmlExtension,
                                  srcSet: IsmlSourceSet): TaskProvider<Isml2Jsp> {
        return project.tasks.register(srcSet.getIsmlTaskName(), Isml2Jsp::class.java) { ismltask ->
            ismltask.group = IsmlExtension.ISML_GROUP_NAME

            ismltask.inputDir.set(srcSet.srcDir)
            ismltask.outputDir.set( project.layout.buildDirectory.dir("generated/isml/${srcSet.name}") )
            ismltask.encoding.set(extension.encoding)
        }

    }

    private fun cnfigureJSPTask(project: Project,
                                extension: IsmlExtension,
                                srcSet: IsmlSourceSet,
                                ismlTask: TaskProvider<Isml2Jsp>): TaskProvider<Jsp2Java> {
        return project.tasks.register(srcSet.getJspTaskName(), Jsp2Java::class.java) { jsptask ->
            jsptask.group = IsmlExtension.ISML_GROUP_NAME

            jsptask.inputDir.set(project.provider { ismlTask.get().outputDir.get() })

            jsptask.outputDir.set( project.layout.buildDirectory.dir("generated/jsp/${srcSet.name}"))
            jsptask.jspPackage.set(srcSet.jspPackage)

            jsptask.jspConfigurationName.set(extension.jspConfigurationName)
            jsptask.sourceCompatibility.set(extension.sourceCompatibility)
            jsptask.targetCompatibility.set(extension.targetCompatibility)
            jsptask.encoding.set(extension.encoding)

            jsptask.encoding.set(extension.encoding)

            jsptask.sourceCompatibility.set(extension.sourceCompatibility)
            jsptask.targetCompatibility.set(extension.targetCompatibility)

            jsptask.sourceSetName.set(extension.sourceSetName)
            jsptask.dependsOn(ismlTask)
        }
    }

    private fun addIsmlConfiguration(project: Project, extension: IsmlExtension) {
        val configuration = project.configurations.maybeCreate(IsmlExtension.ISMLCOMPILER_CONFIGURATION_NAME)
        configuration
            .setVisible(false)
            .setTransitive(true)
            .setDescription("ISML parser configuration is used for jsp generation")
            .defaultDependencies { ds ->
                // this will be executed if configuration is empty
                val dependencyHandler = project.dependencies
                ds.add(
                    dependencyHandler.create(
                        "com.intershop.icm:isml-parser:${extension.ismlCompilerVersion.get()}"))
            }
    }

    private fun addJSPJasperCompilerConfiguration(project: Project, extension: IsmlExtension) {
        val configuration = project.configurations.maybeCreate(IsmlExtension.JASPERCOMPILER_CONFIGURATION_NAME)
        configuration.setVisible(false)
                .setTransitive(true)
                .setDescription("Configuration for JSP compiler")
                .defaultDependencies { ds ->
                    val dependencyHandler = project.dependencies
                    ds.add(
                        dependencyHandler.create(
                            "org.apache.tomcat:tomcat-jasper:${extension.jspCompilerVersion.get()}"))
                    ds.removeIf {it.group == "ch.qos.logback" && it.name == "logback-classic" }
                }
    }

    private fun addJavaDependencies(project: Project, configName: String) {
        val configuration = project.configurations.getByName(configName)
        val dependencyHandler = project.dependencies
        configuration.dependencies.add( dependencyHandler.create("org.apache.tomcat:tomcat-jasper") )
        configuration.dependencies.add( dependencyHandler.create("org.slf4j:slf4j-api") )
    }

    private fun createISMLResourceTask(project: Project): TaskProvider<ResourceListFileTask> {
        return project.tasks.register("resourceListISML", ResourceListFileTask::class.java) { task ->
            task.description = "Creates a resource file with a list of ISML templates"
            task.group = IsmlExtension.ISML_GROUP_NAME
            task.fileExtension = "jsp"
            task.resourceListFileName =
                String.format(Locale.getDefault() ,"resources/%s/isml/isml.resource", project.name)
            task.sourceSetName = "main"
            task.include("**/**/*.jsp")
            task.outputDir.set(
                project.layout.buildDirectory.dir(
                    "${ResourceListExtension.RESOURCELIST_OUTPUTPATH}/isml").get())
        }
    }
}

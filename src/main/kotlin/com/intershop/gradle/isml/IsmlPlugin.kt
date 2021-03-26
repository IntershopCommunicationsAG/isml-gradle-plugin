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
import com.intershop.gradle.isml.tasks.IsmlCompile
import com.intershop.gradle.isml.tasks.PrepareTagLibs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Plugin Class implementation.
 */
open class IsmlPlugin : Plugin<Project> {

    companion object {
        /**
         * Task description of this task.
         */
        const val TASKDESCRIPTION = "Compiles ISML template files to class files"

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

            addEclipseCompilerConfiguration(this, extension)
            addJSPJasperCompilerConfiguration(this, extension)

            if (extension.sourceSets.findByName(IsmlExtension.ISML_MAIN_SOURCESET) == null) {
                val mainIsmlSourceSet = extension.sourceSets.create(IsmlExtension.ISML_MAIN_SOURCESET)
                mainIsmlSourceSet.srcDir = layout.projectDirectory.dir(IsmlExtension.MAIN_TEMPLATE_PATH).asFile
                mainIsmlSourceSet.outputDir = layout.buildDirectory.dir(
                    "${IsmlExtension.ISML_OUTPUTPATH}/${IsmlExtension.ISML_MAIN_SOURCESET}"
                ).get().asFile
                mainIsmlSourceSet.jspPackage = "org.apache.jsp.${project.name}"
            }

            configureTask(this, extension)
        }
    }

    private fun configureTask(project: Project, extension: IsmlExtension) {
        with(project) {
            val ismlMain = tasks.register(TASKNAME) {
                description = TASKDESCRIPTION
                group = IsmlExtension.ISML_GROUP_NAME
            }

            extension.sourceSets.all { ismlSourceSet ->
                val isml = tasks.register(ismlSourceSet.getIsmlTaskName(), IsmlCompile::class.java) { ismlc ->
                    ismlc.group = IsmlExtension.ISML_GROUP_NAME

                    ismlc.provideIsmlConfiguration(extension.ismlConfigurationNameProvider)
                    ismlc.provideOutputDir(ismlSourceSet.outputDirProvider)
                    ismlc.provideInputDir(ismlSourceSet.srcDirectoryProvider)
                    ismlc.provideJspPackage(ismlSourceSet.jspPackageProvider)

                    ismlc.provideSourceSetName(extension.sourceSetNameProvider)

                    ismlc.provideSourceCompatibility(extension.sourceCompatibilityProvider)
                    ismlc.provideTargetCompatibility(extension.targetCompatibilityProvider)
                    ismlc.provideEncoding(extension.encodingProvider)

                    ismlc.provideEnableTldScan(extension.enableTldScanProvider)
                }

                project.plugins.withType(IsmlTagLibPlugin::class.java) {
                    val ismlTagLib = tasks.named(IsmlTagLibPlugin.TASKNAME, PrepareTagLibs::class.java)
                    isml.configure {
                        it.tagLibsInputDir.value(project.provider { ismlTagLib.get().outputDir.get() })
                        it.dependsOn(ismlTagLib)
                    }
                }

                ismlMain.configure {
                    it.dependsOn(isml)
                }

                project.plugins.withType(BasePlugin::class.java) {
                    val assemble = tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
                    assemble.configure {
                        it.dependsOn(isml)
                    }
                }
            }
        }
    }

    private fun addEclipseCompilerConfiguration(project: Project, extension: IsmlExtension) {
        val configuration = project.configurations.maybeCreate(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME)
        configuration.setVisible(false)
                .setTransitive(true)
                .setDescription("Configuration for Eclipse compiler")
                .defaultDependencies { ds ->
                    val dependencyHandler = project.dependencies
                    ds.add(dependencyHandler.create("org.eclipse.jdt:ecj:".
                            plus(extension.eclipseCompilerVersion)))
                    ds.removeIf {it.group == "ch.qos.logback" && it.name == "logback-classic" }
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

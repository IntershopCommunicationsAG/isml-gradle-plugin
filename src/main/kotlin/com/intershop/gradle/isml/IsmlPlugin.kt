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
import org.gradle.language.assembler.tasks.Assemble

class IsmlPlugin : Plugin<Project> {

    companion object {
        const val TASKDESCRIPTION = "Compiles ISML template files to class files"
        const val TASKNAME = "isml"
    }

    override fun apply(project: Project) {
        with(project) {
            logger.info("Isml plugin adds extension {} to {}", IsmlExtension.ISML_EXTENSION_NAME, project.name)
            val extension = extensions.findByType(IsmlExtension::class.java) ?: project.extensions.create(IsmlExtension.ISML_EXTENSION_NAME, IsmlExtension::class.java, project)

            addEclipseCompilerConfiguration(project, extension)
            addJSPJasperCompilerConfiguration(project, extension)

            if(extension.sourceSets.findByName(IsmlExtension.ISML_MAIN_SOURCESET) == null) {
                val mainIsmlSourceSet = extension.sourceSets.create(IsmlExtension.ISML_MAIN_SOURCESET)
                mainIsmlSourceSet.srcDirectoryProvider.set(layout.projectDirectory.dir(IsmlExtension.MAIN_TEMPLATE_PATH))
                mainIsmlSourceSet.outputDirProvider.set(layout.buildDirectory.dir("${IsmlExtension.ISML_OUTPUTPATH}/${IsmlExtension.ISML_MAIN_SOURCESET}"))
                mainIsmlSourceSet.jspPackageProvider.set("ish.cartridges.${project.name}")
            }

            configureTask(this, extension)
        }
    }

    private fun configureTask(project: Project, extension: IsmlExtension) {
        with(project) {
            val ismlMain = tasks.maybeCreate(TASKNAME).apply {
                description = TASKDESCRIPTION
                group = IsmlExtension.ISML_GROUP_NAME
            }
            val assemble = tasks.findByName(Assemble.TASK_NAME)
            val prepareTaglibs = tasks.findByName(IsmlTagLibPlugin.TASKNAME)


            extension.sourceSets.all {ismlSourceSet ->
                tasks.maybeCreate(ismlSourceSet.getIsmlTaskName(), IsmlCompile::class.java).apply {
                    group = IsmlExtension.ISML_GROUP_NAME

                    ismlConfigurationProperty.set(extension.ismlConfigurationNameProvider)
                    outputDirProperty.set(ismlSourceSet.outputDirProvider)
                    inputDirProperty.set(ismlSourceSet.srcDirectoryProvider)
                    jspPackageProperty.set(ismlSourceSet.jspPackageProvider)

                    soureSetNameProperty.set(extension.sourceSetNameProvider)

                    sourceCompatibilityProperty.set(extension.sourceCompatibilityProvider)
                    targetCompatibilityProperty.set(extension.targetCompatibilityProvider)
                    encodingProperty.set(extension.encodingProvider)

                    if(prepareTaglibs != null && prepareTaglibs is PrepareTagLibs) {
                        tagLibsInputDirProperty.set(prepareTaglibs.outputDirProperty)
                    }

                    ismlMain.dependsOn(this)
                    assemble?.dependsOn(this)
                }
            }
        }
    }

    private fun addEclipseCompilerConfiguration(project: Project, extension: IsmlExtension) {
        val configuration = project.configurations.maybeCreate(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME)
        configuration.setVisible(false)
                .setTransitive(true)
                .setDescription("Configuration for Eclipse compiler")
                .defaultDependencies {
                    val dependencyHandler = project.dependencies
                    it.add(dependencyHandler.create("org.eclipse.jdt.core.compiler:ecj:".plus(extension.eclipseCompilerVersion)))
                }
    }

    private fun addJSPJasperCompilerConfiguration(project: Project, extension: IsmlExtension) {
        val configuration = project.configurations.maybeCreate(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME)
        configuration.setVisible(false)
                .setTransitive(true)
                .setDescription("Configuration for JSP compiler")
                .defaultDependencies {
                    val dependencyHandler = project.dependencies
                    it.add(dependencyHandler.create("org.apache.tomcat:tomcat-jasper:".plus(extension.jspCompilerVersion)))
                }
    }
}
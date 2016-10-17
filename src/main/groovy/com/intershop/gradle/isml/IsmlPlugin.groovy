/*
 * Copyright 2015 Intershop Communications AG.
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
 *  limitations under the License.
 */
package com.intershop.gradle.isml

import com.intershop.gradle.isml.extension.IsmlExtension
import com.intershop.gradle.isml.task.IsmlCompile
import com.intershop.gradle.isml.extension.IsmlSourceSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaPlugin
/**
 * Plugin implementation
 */
class IsmlPlugin implements Plugin<Project> {

    /**
     * ISML extension
     */
    private IsmlExtension extension

    /**
     * plugin method
     * @param project
     */
    void apply (Project project) {
        project.logger.info("Create extension ${IsmlExtension.ISML_EXTENSION_NAME}")
        extension = project.extensions.findByType(IsmlExtension) ?: project.extensions.create(IsmlExtension.ISML_EXTENSION_NAME, IsmlExtension, project)

        // Add configuration
        addEclipseCompilerConfiguration(project, extension)
        addJSPJasperCompilerConfiguration(project, extension)

        extension.sourceSets.create(IsmlExtension.ISML_MAIN_SOURCESET) {
            srcDirectory = new File(project.projectDir, IsmlExtension.DEFAULT_TEMPLATEPATH)
        }

        // configure template source set
        configureSourceSets(project)
    }
    
    /**
     * configure template source sets and tasks
     */
    private void configureSourceSets(final Project project) {
        // Task creation
        Task ismlMain = project.getTasks().create(IsmlExtension.ISML_TASK_NAME).configure {
            description = IsmlExtension.ISML_TASK_DESCRIPTION
            group = IsmlExtension.ISML_GROUP_NAME
        }

        // configure template source sets
        extension.getSourceSets().all { IsmlSourceSet ismlSourceSet ->
            // Generate jsp, java and class files to the correct folder
            IsmlCompile task = project.getTasks().create(ismlSourceSet.getTaskName(),  IsmlCompile.class)

            task.onlyIf { ismlSourceSet.getSrcDirectory().exists() }

            task.outputDirectory = new File(project.buildDir, "${IsmlExtension.ISML_OUTPUTPATH}/${ismlSourceSet.name}")
            task.conventionMapping.srcDirectory = { ismlSourceSet.getSrcDirectory() }
            task.conventionMapping.ismlConfigurationName = { extension.getIsmlConfigurationName() }
            task.conventionMapping.sourceSetName = { extension.getJavaSourceSetName() }
            task.conventionMapping.encoding = { extension.getTemplateEncoding() }

            task.dependsOn {
                return project.tasks.findByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
            }

            ismlMain.dependsOn(task)
        }
    }

    private addEclipseCompilerConfiguration(final Project project, IsmlExtension extension) {
        final Configuration configuration =
                project.getConfigurations().findByName(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME) ?:
                        project.getConfigurations().create(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME)

        configuration
                .setVisible(false)
                .setTransitive(true)
                .setDescription('Configuration for Eclipse compiler')
                .defaultDependencies { dependencies ->
            DependencyHandler dependencyHandler = project.getDependencies()
            dependencies.add(dependencyHandler.create('org.eclipse.jdt.core.compiler:ecj:' + extension.getEclipseCompilerVersion()))
        }
    }

    private addJSPJasperCompilerConfiguration(final Project project, IsmlExtension extension) {
        final Configuration configuration =
                project.getConfigurations().findByName(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME) ?:
                        project.getConfigurations().create(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME)

        configuration
                .setVisible(false)
                .setTransitive(true)
                .setDescription('Configuration for JSP compiler')
                .defaultDependencies { dependencies ->
            DependencyHandler dependencyHandler = project.getDependencies()
            dependencies.add(dependencyHandler.create('org.apache.tomcat:tomcat-jasper:' + extension.getJspCompilerVersion()))
        }
    }
}

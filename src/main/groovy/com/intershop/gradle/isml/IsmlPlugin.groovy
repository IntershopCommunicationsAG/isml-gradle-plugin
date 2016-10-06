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

import com.intershop.gradle.isml.tasks.IsmlCompile
import com.intershop.gradle.isml.util.IsmlSourceSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet

class IsmlPlugin implements Plugin<Project> {

    /**
     * Name of the extension
     */
    final static String EXTENSION_NAME = 'isml'

    private IsmlExtension extension


    // folder names
    static final String PAGECOMPILE_FOLDER = 'pagecompile'
    
    // output folder in project.builddir
    static final String DEFAULT_ISML_OUTPUTPATH = 'generated/isml'
    
    // group and task names
    static final String ISML_GROUP = 'ISML compile'
    static final String ISML_TASK_NAME = 'isml'
    
    // ISML main sourceset name 
    static final String ISML_MAIN_SOURCESET = 'main'

    void apply (Project project) {
        project.plugins.apply(JavaBasePlugin)

        project.logger.info("Create extension ${EXTENSION_NAME}")
        this.extension = project.extensions.findByType(IsmlExtension) ?: project.extensions.create(EXTENSION_NAME, IsmlExtension, project)

        // Add configuration
        addEclipseCompilerConfiguration(project)
        addJSPJasperCompilerConfiguration(project)

        // configure defaults for Intershop cartridges
        extension.getSourceSets().create(ISML_MAIN_SOURCESET)

        // configure template source set
        configureSourceSets(project)
    }
    
    /*
     * configure template source sets and tasks
     */
    private void configureSourceSets(Project project) {
        // Task creation
        Task ismlMain = project.getTasks().create(ISML_TASK_NAME)
        ismlMain.group = ISML_GROUP


        // configure template source sets
        extension.getSourceSets().all { IsmlSourceSet ismlSourceSet ->
            // Generate jsp, java and class files to the correct folder
            IsmlCompile task = project.getTasks().create(ismlSourceSet.ismlTaskName,  IsmlCompile.class).configure {
                description = "Precompile isml templates for ${ismlSourceSet.name}"
                group = ISML_GROUP
            }

            task.conventionMapping.outputDirectory = {
                File outputDir = new File(project.buildDir, "${DEFAULT_ISML_OUTPUTPATH}/${ismlSourceSet.name}")
                outputDir.mkdirs()
                project.fileTree(outputDir)
            }

            task.conventionMapping.sourceDir = { project.fileTree(ismlSourceSet.getIsmlPath()) }
            task.conventionMapping.classpath = {
                JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention.class)
                SourceSet main = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                Set<File> classpathFiles = main.compileClasspath.filter({it.name.endsWith('.jar')}).files
                classpathFiles.add(main.output.classesDir)
                classpathFiles.add(main.output.resourcesDir)
                classpathFiles
            }

            project.afterEvaluate {
                if (project.plugins.hasPlugin(JavaBasePlugin) && ! project.convention.getPlugin(JavaPluginConvention.class).sourceSets.isEmpty()) {
                    SourceSet sourceSet = project.convention.getPlugin(JavaPluginConvention.class).sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)
                    if(sourceSet != null) {
                        task.dependsOn(project.tasks.getByName(sourceSet.compileJavaTaskName))
                    }
                }
            }

            ismlMain.dependsOn(task)
        }
    }

    private addEclipseCompilerConfiguration(final Project project) {
        final Configuration configuration =
                project.getConfigurations().findByName(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME) ?:
                        project.getConfigurations().create(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME)

        configuration
                .setVisible(false)
                .setTransitive(true)
                .setDescription("WSDL Axis2 configuration is used for code generation")
                .defaultDependencies { dependencies ->
            DependencyHandler dependencyHandler = project.getDependencies()
            dependencies.add(dependencyHandler.create('org.eclipse.jdt.core.compiler:ecj:' + extension.getEclipseCompilerVersion()))
        }
    }

    private addJSPJasperCompilerConfiguration(final Project project) {
        final Configuration configuration =
                project.getConfigurations().findByName(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME) ?:
                        project.getConfigurations().create(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME)

        configuration
                .setVisible(false)
                .setTransitive(true)
                .setDescription("WSDL Axis2 configuration is used for code generation")
                .defaultDependencies { dependencies ->
            DependencyHandler dependencyHandler = project.getDependencies()
            dependencies.add(dependencyHandler.create('org.apache.tomcat:tomcat-jasper:' + extension.getJspCompilerVersion()))
        }
    }
}

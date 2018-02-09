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

import groovy.lang.Closure
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File

open class IsmlExtension(project: Project) {

    companion object {
        // names for the plugin
        const val ISML_EXTENSION_NAME = "isml"
        const val ISML_GROUP_NAME = "ISML Template Compilation"

        // default versions
        const val JSP_COMPILER_VERSION = "7.0.42"
        const val ECLIPSE_COMPILER_VERSION = "4.2.2"

        //configuration names
        const val ECLIPSECOMPILER_CONFIGURATION_NAME = "ismlJavaCompiler"
        const val JSPJASPERCOMPILER_CONFIGURATION_NAME = "ismlJspCompiler"

        //'main' ISML template configuration
        const val ISML_MAIN_SOURCESET = "main"
        const val MAIN_TEMPLATE_PATH = "staticfiles/cartridge/templates"

        // output folder path
        const val ISML_OUTPUTPATH = "generated/isml"
        const val ISMLTAGLIB_OUTPUTPATH = "generated/isml-taglibs"

        //file encoding
        const val DEFAULT_FILEENCODING = "UTF-8"

        // WEB-INF for jasper
        const val WEB_XML_PATH = "WEB-INF/web.xml"
        val WEB_XML_CONTENT = """|<?xml version="1.0" encoding="ISO-8859-1"?>
                                 |<web-app xmlns="http://java.sun.com/xml/ns/javaee"
                                 |         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 |         xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
                                 |                             http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
                                 |         version="3.0">
                                 |</web-app>""".trimMargin()
    }

    /**
     * sourceSets with ISML files
     */
    val sourceSets: NamedDomainObjectContainer<IsmlSourceSet> = project.container(IsmlSourceSet::class.java, IsmlSourceSetFactory(project))

    fun sourceSets(closure: Closure<Unit>) {
        sourceSets.configure(closure)
    }

    // Taglib folder
    val taglibFolderProvider: DirectoryProperty = project.layout.directoryProperty()

    // JSP compiler version / Tomcat version
    val jspCompilerVersionProvider: Property<String> = project.objects.property(String::class.java)
    // Eclipse compiler version depends on the Tomcat version
    val eclipseCompilerVersionProvider: Property<String> = project.objects.property(String::class.java)
    // Java SourceSet name which is used for template compilation
    val sourceSetNameProvider: Property<String> = project.objects.property(String::class.java)
    // Configuration name which is used for template compilation
    val ismlConfigurationNameProvider: Property<String> = project.objects.property(String::class.java)

    // Source compatibility of java files (result of Jsp2Java)
    val sourceCompatibilityProvider: Property<String> = project.objects.property(String::class.java)
    // Target compatibility of java files (result of Jsp2Java)
    val targetCompatibilityProvider: Property<String> = project.objects.property(String::class.java)
    // File encoding
    val encodingProvider: Property<String> = project.objects.property(String::class.java)

    init {
        taglibFolderProvider.set(project.layout.buildDirectory.dir(IsmlExtension.ISMLTAGLIB_OUTPUTPATH))

        jspCompilerVersionProvider.set(JSP_COMPILER_VERSION)
        eclipseCompilerVersionProvider.set(ECLIPSE_COMPILER_VERSION)
        sourceSetNameProvider.set(SourceSet.MAIN_SOURCE_SET_NAME)
        ismlConfigurationNameProvider.set("compile")
        sourceCompatibilityProvider.set("1.6")
        targetCompatibilityProvider.set("1.6")
        encodingProvider.set(DEFAULT_FILEENCODING)
    }

    /**
     * Folder with tag lib configuration
     */
    var taglibFolder: File
        get() {
            return taglibFolderProvider.get().asFile
        }
        set(value) {
            this.taglibFolderProvider.set(value)
        }

    /**
     * JSP compiler version / Tomcat version
     */
    var jspCompilerVersion: String
        get() {
            return jspCompilerVersionProvider.get()
        }
        set(value) {
            jspCompilerVersionProvider.set(value)
        }

    /**
     * Eclipse compiler version depends on the Tomcat version
     */
    var eclipseCompilerVersion: String
        get() {
            return eclipseCompilerVersionProvider.get()
        }
        set(value) {
            eclipseCompilerVersionProvider.set(value)
        }

    /**
     * Java SourceSet name which is used for template compilation
     * Default value is 'main'
     */
    var sourceSetName: String
        get() {
            return sourceSetNameProvider.get()
        }
        set(value) {
            sourceSetNameProvider.set(value)
        }

    /**
     * Configuration name which is used for template compilation
     * Default value is 'runtimeElements'
     */
    var ismlConfigurationName: String
        get() {
            return ismlConfigurationNameProvider.get()
        }
        set(value) {
            ismlConfigurationNameProvider.set(value)
        }

    /**
     * Source compatibility of java files (result of Jsp2Java)
     */
    var sourceCompatibility: String
        get() {
            return sourceCompatibilityProvider.get()
        }
        set(value) {
            sourceCompatibilityProvider.set(value)
        }

    /**
     * Target compatibility of java files (result of Jsp2Java)
     */
    var targetCompatibility: String
        get() {
            return targetCompatibilityProvider.get()
        }
        set(value) {
            targetCompatibilityProvider.set(value)
        }

    /**
     * File encoding
     */
    var encoding: String
        get() {
            return encodingProvider.get()
        }
        set(value) {
            encodingProvider.set(value)
        }

    @TaskAction
    fun generate() {

    }
}
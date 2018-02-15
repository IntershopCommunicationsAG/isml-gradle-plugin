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

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import java.io.File
import kotlin.reflect.KProperty

operator fun <T> Property<T>.setValue(receiver: Any?, property: KProperty<*>, value: T) = set(value)
operator fun <T> Property<T>.getValue(receiver: Any?, property: KProperty<*>): T = get()

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

    fun sourceSets(configureAction: Action<in NamedDomainObjectContainer<IsmlSourceSet>>) {
        configureAction.execute(sourceSets)
    }

    // Taglib folder
    private val taglibFolderProperty: DirectoryProperty = project.layout.directoryProperty()

    // JSP compiler version / Tomcat version
    private val jspCompilerVersionProperty: Property<String> = project.objects.property(String::class.java)
    // Eclipse compiler version depends on the Tomcat version
    private val eclipseCompilerVersionProperty: Property<String> = project.objects.property(String::class.java)
    // Java SourceSet name which is used for template compilation
    private val sourceSetNameProperty: Property<String> = project.objects.property(String::class.java)
    // Configuration name which is used for template compilation
    private val ismlConfigurationNameProperty: Property<String> = project.objects.property(String::class.java)

    // Source compatibility of java files (result of Jsp2Java)
    private val sourceCompatibilityProperty: Property<String> = project.objects.property(String::class.java)
    // Target compatibility of java files (result of Jsp2Java)
    private val targetCompatibilityProperty: Property<String> = project.objects.property(String::class.java)
    // File encoding
    private val encodingProperty: Property<String> = project.objects.property(String::class.java)

    init {
        taglibFolderProperty.set(project.layout.buildDirectory.dir(IsmlExtension.ISMLTAGLIB_OUTPUTPATH))

        jspCompilerVersionProperty.set(JSP_COMPILER_VERSION)
        eclipseCompilerVersionProperty.set(ECLIPSE_COMPILER_VERSION)
        sourceSetNameProperty.set(SourceSet.MAIN_SOURCE_SET_NAME)
        ismlConfigurationNameProperty.set("compile")
        sourceCompatibilityProperty.set("1.6")
        targetCompatibilityProperty.set("1.6")
        encodingProperty.set(DEFAULT_FILEENCODING)
    }

    /**
     * Folder with tag lib configuration
     */
    val taglibFolderProvider: Provider<Directory>
        get() = taglibFolderProperty

    var taglibFolder: File
        get() = taglibFolderProperty.get().asFile
        set(value) = taglibFolderProperty.set(value)

    /**
     * JSP compiler version / Tomcat version
     */
    val jspCompilerVersionProvider: Provider<String>
        get() = jspCompilerVersionProperty

    var jspCompilerVersion by jspCompilerVersionProperty

    /**
     * Eclipse compiler version depends on the Tomcat version
     */
    val eclipseCompilerVersionProvider: Provider<String>
        get() = eclipseCompilerVersionProperty

    var eclipseCompilerVersion by eclipseCompilerVersionProperty

    /**
     * Java SourceSet name which is used for template compilation
     * Default value is 'main'
     */
    val sourceSetNameProvider: Provider<String>
        get() = sourceSetNameProperty

    var sourceSetName by sourceSetNameProperty

    /**
     * Configuration name which is used for template compilation
     * Default value is 'runtimeElements'
     */
    val ismlConfigurationNameProvider: Provider<String>
        get() = ismlConfigurationNameProperty

    var ismlConfigurationName by ismlConfigurationNameProperty

    /**
     * Source compatibility of java files (result of Jsp2Java)
     */
    val sourceCompatibilityProvider: Provider<String>
        get() = sourceCompatibilityProperty

    var sourceCompatibility by sourceCompatibilityProperty

    /**
     * Target compatibility of java files (result of Jsp2Java)
     */
    val targetCompatibilityProvider: Provider<String>
        get() = targetCompatibilityProperty

    var targetCompatibility by targetCompatibilityProperty

    /**
     * File encoding
     */
    val encodingProvider: Provider<String>
        get() = encodingProperty

    var encoding by encodingProperty
}
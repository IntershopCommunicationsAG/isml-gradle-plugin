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
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.util.ConfigureUtil
import java.io.File
import kotlin.reflect.KProperty

/**
 * Add a set function to a String property.
 */
operator fun <T> Property<T>.setValue(receiver: Any?, property: KProperty<*>, value: T) = set(value)
/**
 * Add a get function to a String property.
 */
operator fun <T> Property<T>.getValue(receiver: Any?, property: KProperty<*>): T = get()

/**
 * Configuration container for the ISML plugin.
 */
open class IsmlExtension(project: Project) {

    companion object {

        // names for the plugin
        const val ISML_EXTENSION_NAME = "isml"
        const val ISML_GROUP_NAME = "ISML Template Compilation"

        // default versions
        const val JSP_COMPILER_VERSION = "9.0.19"
        const val ECLIPSE_COMPILER_VERSION = "4.6.1"

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
     * SourceSet container with ISML files source sets.
     */
    val sourceSets: NamedDomainObjectContainer<IsmlSourceSet> =
            project.container(IsmlSourceSet::class.java, IsmlSourceSetFactory(project))

    /**
     * Add source sets to the configuration with an action.
     */
    fun sourceSets(configureAction: Action<in NamedDomainObjectContainer<IsmlSourceSet>>) {
        configureAction.execute(sourceSets)
    }

    /**
     * Add source sets to the configuration with a closure.
     */
    fun sourceSets(closure: Closure<NamedDomainObjectContainer<IsmlSourceSet>>) {
        ConfigureUtil.configure(closure, sourceSets)
    }

    // Taglib folder
    private val taglibFolderProperty: DirectoryProperty = project.objects.directoryProperty()

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

    // Enable TLD scanning by JspC
    private val enableTldScanProperty: Property<Boolean> = project.objects.property(Boolean::class.java)

    init {
        taglibFolderProperty.set(project.layout.buildDirectory.dir(ISMLTAGLIB_OUTPUTPATH))

        jspCompilerVersionProperty.convention(JSP_COMPILER_VERSION)
        eclipseCompilerVersionProperty.convention(ECLIPSE_COMPILER_VERSION)
        sourceSetNameProperty.convention(SourceSet.MAIN_SOURCE_SET_NAME)
        ismlConfigurationNameProperty.convention("runtimeClasspath") //("compile") //
        sourceCompatibilityProperty.convention("1.8")
        targetCompatibilityProperty.convention("1.8")
        encodingProperty.set(DEFAULT_FILEENCODING)

        enableTldScanProperty.convention(false)
    }

    /**
     * Provider for taglibFolder property.
     */
    val taglibFolderProvider: Provider<Directory>
        get() = taglibFolderProperty

    /**
     * Folder with tag lib configurations.
     *
     * @property taglibFolder
     */
    var taglibFolder: File
        get() = taglibFolderProperty.get().asFile
        set(value) = taglibFolderProperty.set(value)

    /**
     * Provider for jspCompilerVersion property.
     */
    val jspCompilerVersionProvider: Provider<String>
        get() = jspCompilerVersionProperty

    /**
     * JSP compiler version / Tomcat version.
     *
     * @property jspCompilerVersion
     */
    var jspCompilerVersion by jspCompilerVersionProperty

    /**
     * Provider for eclipseCompilerVersion property.
     */
    val eclipseCompilerVersionProvider: Provider<String>
        get() = eclipseCompilerVersionProperty

    /**
     * Eclipse compiler version depends on the Tomcat version.
     *
     * @property eclipseCompilerVersion
     */
    var eclipseCompilerVersion by eclipseCompilerVersionProperty

    /**
     * Provider for sourceSetName property.
     */
    val sourceSetNameProvider: Provider<String>
        get() = sourceSetNameProperty

    /**
     * Java SourceSet name which is used for template compilation.
     * Default value is 'main'.
     *
     * @property sourceSetName
     */
    var sourceSetName by sourceSetNameProperty

    /**
     * Provider for ismlConfigurationName property.
     */
    val ismlConfigurationNameProvider: Provider<String>
        get() = ismlConfigurationNameProperty

    /**
     * Configuration name which is used for template compilation.
     * Default value is 'runtimeElements'.
     *
     * @property ismlConfigurationName
     */
    var ismlConfigurationName by ismlConfigurationNameProperty

    /**
     * Provider for sourceCompatibility property.
     */
    val sourceCompatibilityProvider: Provider<String>
        get() = sourceCompatibilityProperty

    /**
     * Source compatibility of java files (result of Jsp2Java).
     *
     * @property sourceCompatibility
     */
    var sourceCompatibility by sourceCompatibilityProperty

    /**
     * Provider for targetCompatibility property.
     */
    val targetCompatibilityProvider: Provider<String>
        get() = targetCompatibilityProperty

    /**
     * Target compatibility of java files (result of Jsp2Java).
     *
     * @property targetCompatibility
     */
    var targetCompatibility by targetCompatibilityProperty

    /**
     * Provider for encoding property.
     */
    val encodingProvider: Provider<String>
        get() = encodingProperty

    /**
     * File encoding for JSP code generation.
     *
     * @property encoding
     */
    var encoding by encodingProperty

    /**
     * Provider for TLD enabling property.
     */
    val enableTldScanProvider: Provider<Boolean>
        get() = enableTldScanProperty

    /**
     * TldScan will be enabled if this property set to true.
     *
     * @property enableTldScan
     */
    var enableTldScan by enableTldScanProperty
}

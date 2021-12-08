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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

/**
 * Main configuration container for the ISML plugin.
 */
open class IsmlExtension @Inject constructor(objectFactory: ObjectFactory, projectLayout: ProjectLayout) {

    companion object {

        /**
         * Extension name of the main ISML extension.
         */
        const val ISML_EXTENSION_NAME = "isml"

        /**
         * Task group name for ISML compilation tasks.
         */
        const val ISML_GROUP_NAME = "ISML Template Compilation"

        /**
         * Default JSP compiler version.
         */
        const val JSP_COMPILER_VERSION = "9.0.41"

        /**
         * Default ISML compiler version.
         */
        const val ISML_COMPILER_VERSION = "11.0.0"

        /**
         * Gradle configuration for jsp compiler.
         */
        const val JASPERCOMPILER_CONFIGURATION_NAME = "jspCompiler"

        /**
         * Gradle configuration for jsp compiler.
         */
        const val ISMLCOMPILER_CONFIGURATION_NAME = "ismlCompiler"

        /**
         * Default source set name of ISML files.
         */
        const val ISML_MAIN_SOURCESET = "main"

        /**
         * Default path of ISML files.
         */
        const val MAIN_TEMPLATE_PATH = "staticfiles/cartridge/templates"

        /**
         * Default target path of ISML compilation.
         */
        const val ISML_OUTPUTPATH = "generated/isml"

        /**
         * Default target path of Jsp compilation.
         */
        const val JSP_OUTPUTPATH = "generated/jsp"

        /**
         * Default target path of ISML taglibs.
         */
        const val ISMLTAGLIB_OUTPUTPATH = "generated/isml-taglibs"

        /**
         * Default file encoding for ISML and JSP compilation.
         */
        const val DEFAULT_FILEENCODING = "UTF-8"

        /**
         * Default web.xml path.
         */
        const val WEB_XML_PATH = "WEB-INF/web.xml"

        /**
         * Default web.xml content.
         */
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
            objectFactory.domainObjectContainer(IsmlSourceSet::class.java)

    /**
     * Provider for taglibFolder property.
     */
    val taglibFolder: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Provider for jspCompilerVersion property.
     */
    val jspCompilerVersion: Property<String> = objectFactory.property(String::class.java)

    /**
     * Provider for ismlCompilerVersion property.
     */
    val ismlCompilerVersion: Property<String> = objectFactory.property(String::class.java)

    /**
     * Java SourceSet name which is used for template compilation.
     * Default value is 'main'.
     *
     * @property sourceSetName
     */
    val sourceSetName: Property<String> = objectFactory.property(String::class.java)

    /**
     * Configuration name which is used for template compilation.
     * Default value is 'runtimeElements'.
     *
     * @property ismlConfigurationName
     */
    val ismlConfigurationName: Property<String> = objectFactory.property(String::class.java)

    /**
     * Source compatibility of java files (result of Jsp2Java).
     *
     * @property sourceCompatibility
     */
    val sourceCompatibility: Property<String> = objectFactory.property(String::class.java)

    /**
     * Target compatibility of java files (result of Jsp2Java).
     *
     * @property targetCompatibility
     */
    val targetCompatibility: Property<String> = objectFactory.property(String::class.java)

    /**
     * File encoding for JSP code generation.
     *
     * @property encoding
     */
    val encoding: Property<String> = objectFactory.property(String::class.java)

    /**
     * TldScan will be enabled if this property set to true.
     *
     * @property enableTldScan
     */
    val enableTldScan: Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        taglibFolder.convention(projectLayout.buildDirectory.dir(ISMLTAGLIB_OUTPUTPATH))

        jspCompilerVersion.convention(JSP_COMPILER_VERSION)
        ismlCompilerVersion.convention(ISML_COMPILER_VERSION)

        sourceSetName.convention(SourceSet.MAIN_SOURCE_SET_NAME)

        ismlConfigurationName.convention("runtimeClasspath") //("compile") //

        sourceCompatibility.convention("11")
        targetCompatibility.convention("11")

        encoding.set(DEFAULT_FILEENCODING)

        enableTldScan.convention(false)
    }
}

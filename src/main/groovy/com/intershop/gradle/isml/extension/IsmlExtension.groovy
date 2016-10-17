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

package com.intershop.gradle.isml.extension

import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

@CompileStatic
class IsmlExtension {

    // names for the plugin
    final static String ISML_EXTENSION_NAME = 'isml'
    final static String ISML_TASK_NAME = 'isml'
    final static String ISML_TASK_DESCRIPTION = 'Compiles ISML template files to class files'
    final static String ISML_GROUP_NAME = 'ISML Template Compilation'

    final static String ISML_MAIN_SOURCESET = 'main'

    // output folder path
    static final String ISML_OUTPUTPATH = 'generated/isml'

    //configuration names
    static final String ECLIPSECOMPILER_CONFIGURATION_NAME = 'ismlJavaCompiler'
    static final String JSPJASPERCOMPILER_CONFIGURATION_NAME = 'ismlJspCompiler'
    static final String DEFAULT_ISMLCOMPILER_CONFIGURATION_NAME = 'runtime'

    //file encoding
    static final String DEFAULT_FILEENCODING = 'UTF-8'

    //default ISML template
    static final String DEFAULT_TEMPLATEPATH = 'staticfiles/cartridge/templates'

    /**
     * sourceSets with ISML files
     */
    final NamedDomainObjectContainer<IsmlSourceSet> sourceSets

    /**
     * JSP compiler version / Tomcat version
     */
    String jspCompilerVersion = '7.0.42'

    /**
     * Eclipse compiler version depends on the Tomcat version
     */
    String eclipseCompilerVersion = '4.2.2'

    /**
     * ISML template encoding
     */
    String templateEncoding = DEFAULT_FILEENCODING

    /**
     * Java SourceSet name which is used for template compilation
     * Default value is 'main'
     */
    String javaSourceSetName = SourceSet.MAIN_SOURCE_SET_NAME

    /**
     * Configuration name which is used for template compilation
     * Default value is 'runtime'
     */
    String ismlConfigurationName = DEFAULT_ISMLCOMPILER_CONFIGURATION_NAME

    // project
    private Project project

    /**
     * Initialize the extension.
     *
     * @param project
     */
    public IsmlExtension(Project project) {
        this.project = project
        sourceSets = project.container(IsmlSourceSet)
    }
    
    void sourceSets (Closure closure) {
        sourceSets.configure(closure)
    }
}

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

import com.intershop.gradle.isml.util.IsmlSourceSet
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.process.JavaForkOptions

@CompileStatic
class IsmlExtension {

    static final String ECLIPSECOMPILER_CONFIGURATION_NAME = 'ismlJavaCompiler'

    static final String JSPJASPERCOMPILER_CONFIGURATION_NAME = 'ismlJspCompiler'

    final NamedDomainObjectContainer<IsmlSourceSet> sourceSets
    
    String jspCompilerVersion = '7.0.42'

    String eclipseCompilerVersion = '4.2.2'
    
    String templateEncoding = 'UTF-8'

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

    /**
     * This configures the special options for the used VM for axis1.
     */
    JavaForkOptions eclipseCompilerForkOptions

    void axis1ForkOptions(Closure c) {
        project.configure(eclipseCompilerForkOptions, c)
    }
}

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

import com.intershop.gradle.isml.tasks.Isml2Jsp
import com.intershop.gradle.isml.tasks.Jsp2Java
import com.intershop.gradle.test.AbstractProjectSpec
import org.gradle.api.Plugin
import org.gradle.api.file.ConfigurableFileCollection

class IsmlPluginTest extends AbstractProjectSpec {

    File fakeIsmlJar
    File fakeJspJar

    def setup() {
        fakeIsmlJar = File.createTempFile('fake-isml', '.jar')
        fakeJspJar = File.createTempFile('fake-jsp', '.jar')
    }

    def cleanup() {
        fakeIsmlJar?.delete()
        fakeJspJar?.delete()
    }

    @Override
    Plugin getPlugin() {
        return new IsmlPlugin()
    }

    def 'should add extension named isml'() {
        when:
        plugin.apply(project)

        then:
        project.extensions.isml
    }

    def 'should add ISML sourceSet'() {
        when:
        plugin.apply(project)

        then:
        project.extensions.isml.sourceSets.main.getName() == 'main'
    }

    def 'should add ISML task'() {
        given:
        plugin.apply(project)

        when:
        project.extensions.isml.sourceSets {
            test {}
        }

        then:
        project.tasks.findByName("isml")
    }

    def 'Isml2Jsp.ismlClasspathfiles should be a ConfigurableFileCollection'() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks.findByName('isml2jspMain')
        task instanceof Isml2Jsp
        (task as Isml2Jsp).ismlClasspathfiles instanceof ConfigurableFileCollection
    }

    def 'Jsp2Java.jspClasspathfiles should be a ConfigurableFileCollection'() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks.findByName('jsp2javaMain')
        task instanceof Jsp2Java
        (task as Jsp2Java).jspClasspathfiles instanceof ConfigurableFileCollection
    }

    def 'Jsp2Java.classpathfiles should be a ConfigurableFileCollection'() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks.findByName('jsp2javaMain')
        task instanceof Jsp2Java
        (task as Jsp2Java).classpathfiles instanceof ConfigurableFileCollection
    }

    def 'Isml2Jsp.ismlClasspathfiles should be wired to the ismlCompiler configuration'() {
        given:
        plugin.apply(project)

        // Add a local file directly to the ismlCompiler configuration
        // If the collection is correctly wired, the file must appear when resolved
        project.dependencies.add('ismlCompiler', project.files(fakeIsmlJar))

        when:
        def task = project.tasks.findByName('isml2jspMain') as Isml2Jsp

        then: 'the file added to ismlCompiler is visible through the task classpath'
        task.ismlClasspathfiles.files.contains(fakeIsmlJar)
    }

    def 'Jsp2Java.jspClasspathfiles should be wired to the jspCompiler configuration'() {
        given:
        plugin.apply(project)

        // Add a local file directly to the jspCompiler configuration.
        project.dependencies.add('jspCompiler', project.files(fakeJspJar))

        when:
        def task = project.tasks.findByName('jsp2javaMain') as Jsp2Java

        then: 'the file added to jspCompiler is visible through the task classpath'
        task.jspClasspathfiles.files.contains(fakeJspJar)
    }
}

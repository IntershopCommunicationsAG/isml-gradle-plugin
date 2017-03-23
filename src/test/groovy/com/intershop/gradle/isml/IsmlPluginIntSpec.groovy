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

import com.intershop.gradle.test.AbstractIntegrationSpec
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class IsmlPluginIntSpec extends AbstractIntegrationSpec {

    def 'Test taglib and usage in one Cartridge - isml'() {
        given:
        copyResources('test_taglib')

        File tplFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/taglibTest.isml.tpl')
        File testFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/taglibTest.isml')

        testFile << tplFile.text.replaceAll('@cartridge@', 'testCartridge')
        tplFile.delete()

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.isml'
            }

            dependencies {
                compile("com.intershop.platform:core:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:servletengine:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:isml:${platformVersion}") {
                    transitive = false
                }
                compile("javax.servlet:javax.servlet-api:${servletVersion}") {
                    transitive = false
                }
                compile("org.slf4j:slf4j-api:${slf4jVersion}") {
                    transitive = false
                }
                compile("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                    transitive = false
                }
            }

            repositories {
                jcenter()

                ivy {
                    url '${System.properties['intershop.host.url']}'
                    layout('pattern') {
                        ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                        artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                    }
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
                maven {
                    url '${System.properties['intershop.host.url']}'
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge'
        """.stripIndent()

        when:
        List<String> args = ['clean', 'isml', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':isml').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    //TODO: Test incremental build of isml: changes on isml file
    def 'Test isml incemental build with a changed file'() {
        given:
        copyResources('test_isml')

        buildFile << """
            plugins {
                id 'com.intershop.gradle.isml'
            }

            configurations {
                compile
                runtime.extendsFrom(compile)
            }

            dependencies {
                compile("com.intershop.platform:core:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:servletengine:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:isml:${platformVersion}") {
                    transitive = false
                }
                compile("javax.servlet:javax.servlet-api:${servletVersion}") {
                    transitive = false
                }
                compile("org.slf4j:slf4j-api:${slf4jVersion}") {
                    transitive = false
                }
                compile("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                    transitive = false
                }
            }

            repositories {
                jcenter()

                ivy {
                    url '${System.properties['intershop.host.url']}'
                    layout('pattern') {
                        ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                        artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                    }
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
                maven {
                    url '${System.properties['intershop.host.url']}'
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge'
        """.stripIndent()

        File ismlFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/test.isml')
        long ismlLastModified = ismlFile.lastModified()

        when:
        List<String> args = ['isml', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        File jspFile = new File(testProjectDir, 'build/generated/isml/main/pagecompile/default/support/test.jsp')
        File javaFile =  new File(testProjectDir, 'build/generated/isml/main/pagecompile/ish/cartridges/testCartridge/default_/support/test_jsp.java')
        File classFile = new File(testProjectDir, 'build/generated/isml/main/pagecompile/ish/cartridges/testCartridge/default_/support/test_jsp.class')


        then:
        result.task(':isml').outcome == SUCCESS
        jspFile.exists()
        javaFile.exists()
        classFile.exists()

        jspFile.lastModified() == javaFile.lastModified()
        jspFile.lastModified() == classFile.lastModified()

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    //TODO: Test incremental build of isml: changes on classpath
    def 'Test isml incemental build with a changed classpath'() {
        given:
        copyResources('test_isml')

        buildFile << """
            plugins {
                id 'com.intershop.gradle.isml'
            }

            configurations {
                compile
                runtime.extendsFrom(compile)
            }

            dependencies {
                compile("com.intershop.platform:core:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:servletengine:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:isml:${platformVersion}") {
                    transitive = false
                }
                compile("javax.servlet:javax.servlet-api:${servletVersion}") {
                    transitive = false
                }
                compile("org.slf4j:slf4j-api:${slf4jVersion}") {
                    transitive = false
                }
                compile("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                    transitive = false
                }
            }

            repositories {
                jcenter()

                ivy {
                    url '${System.properties['intershop.host.url']}'
                    layout('pattern') {
                        ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                        artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                    }
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
                maven {
                    url '${System.properties['intershop.host.url']}'
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge'
        """.stripIndent()

        when:
        List<String> args = ['isml', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':isml').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    def 'Test isml'() {
        given:
        copyResources('test_isml')

        buildFile << """
            plugins {
                id 'com.intershop.gradle.isml'
            }

            configurations {
                compile
                runtime.extendsFrom(compile)
            }

            dependencies {
                compile("com.intershop.platform:core:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:servletengine:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:isml:${platformVersion}") {
                    transitive = false
                }
                compile("javax.servlet:javax.servlet-api:${servletVersion}") {
                    transitive = false
                }
                compile("org.slf4j:slf4j-api:${slf4jVersion}") {
                    transitive = false
                }
                compile("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                    transitive = false
                }
            }

            repositories {
                jcenter()

                ivy {
                    url '${System.properties['intershop.host.url']}'
                    layout('pattern') {
                        ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                        artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                    }
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
                maven {
                    url '${System.properties['intershop.host.url']}'
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge'
        """.stripIndent()

        when:
        List<String> args = ['isml', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':isml').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    def 'Test taglib and usage in one Cartridge and additional javaOptions - isml'() {
        given:
        copyResources('test_taglib')

        File tplFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/taglibTest.isml.tpl')
        File testFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/taglibTest.isml')

        testFile << tplFile.text.replaceAll('@cartridge@', 'testCartridge')
        tplFile.delete()

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.isml'
            }

            dependencies {
                compile("com.intershop.platform:core:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:servletengine:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:isml:${platformVersion}") {
                    transitive = false
                }
                compile("javax.servlet:javax.servlet-api:${servletVersion}") {
                    transitive = false
                }
                compile("org.slf4j:slf4j-api:${slf4jVersion}") {
                    transitive = false
                }
                compile("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                    transitive = false
                }
            }

            tasks.withType(com.intershop.gradle.isml.task.IsmlCompile){
                eclipseCompilerJavaOptions.setMaxHeapSize('64m')
                eclipseCompilerJavaOptions.jvmArgs += ['-Dhttp.proxyHost=10.0.0.100', '-Dhttp.proxyPort=8800']
            }

            repositories {
                jcenter()

                ivy {
                    url '${System.properties['intershop.host.url']}'
                    layout('pattern') {
                        ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                        artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                    }
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
                maven {
                    url '${System.properties['intershop.host.url']}'
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge'
        """.stripIndent()

        when:
        List<String> args = ['clean', 'isml', '-s', '-d']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':isml').outcome == SUCCESS
        result.output.contains('-Dhttp.proxyHost=10.0.0.100 -Dhttp.proxyPort=8800 -Xmx64m')

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    def 'Test taglib and usage with project dependencies - isml'() {

        given:
        File settingsGradle = new File(testProjectDir, 'settings.gradle')

        settingsGradle << """
        rootProject.name='testProject'
        """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.isml'
            }

            subprojects {
                apply plugin: 'java'
                apply plugin: 'ivy-publish'
                apply plugin: 'com.intershop.gradle.isml'

                version = '1.0.0'
                group = 'com.test'

                dependencies {
                    compile("com.intershop.platform:core:${platformVersion}") {
                        transitive = false
                    }
                    compile("com.intershop.platform:servletengine:${platformVersion}") {
                        transitive = false
                    }
                    compile("com.intershop.platform:isml:${platformVersion}") {
                        transitive = false
                    }
                    compile("javax.servlet:javax.servlet-api:${servletVersion}") {
                        transitive = false
                    }
                    compile("org.slf4j:slf4j-api:${slf4jVersion}") {
                        transitive = false
                    }
                    compile("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                        transitive = false
                    }
                }

                repositories {
                    jcenter()

                    ivy {
                        url '${System.properties['intershop.host.url']}'
                        layout('pattern') {
                            ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                            artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                        }
                        credentials {
                            username '${System.properties['intershop.host.username']}'
                            password '${System.properties['intershop.host.userpassword']}'
                        }
                    }
                    maven {
                        url '${System.properties['intershop.host.url']}'
                        credentials {
                            username '${System.properties['intershop.host.username']}'
                            password '${System.properties['intershop.host.userpassword']}'
                        }
                    }
                }
            }

            project(':testCartridge1') {

                task createCartridge(type: Zip) {
                    from 'staticfiles/cartridge'
                    from isml2classMain.outputDirectory
                    into "\${project.name}/release"
                }

                afterEvaluate { project ->
                    createCartridge.dependsOn project.tasks.isml
                }


                publishing {
                    publications {
                        ivy(IvyPublication) {
                            artifact(createCartridge) {
                                type = 'cartridge'
                            }
                            artifact(jar)
                        }
                    }
                    repositories {
                        ivy {
                            url "\${project.buildDir.absolutePath.replaceAll('\\\\\\\\','/')}/repo".toString()
                            layout('pattern') {
                                ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                                artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                            }
                        }
                    }
                }
            }

            project(':testCartridge2') {
                dependencies {
                    compile project(':testCartridge1')
                }
            }
        """.stripIndent()

        createSubProject('testCartridge1', settingsGradle, '')
        createSubProject('testCartridge2', settingsGradle, '')

        copyResources('test_taglib', 'testCartridge1')

        File tplFile1 = new File(testProjectDir, 'testCartridge1/staticfiles/cartridge/templates/default/support/taglibTest.isml.tpl')
        File testFile1 = new File(testProjectDir, 'testCartridge1/staticfiles/cartridge/templates/default/support/taglibTest.isml')

        testFile1 << tplFile1.text.replaceAll('@cartridge@', 'testCartridge1')

        copyResources('use_taglib', 'testCartridge2')

        File tplFile2 = new File(testProjectDir, 'testCartridge2/staticfiles/cartridge/templates/default/support/useTaglibTest.isml.tpl')
        File testFile2 = new File(testProjectDir, 'testCartridge2/staticfiles/cartridge/templates/default/support/useTaglibTest.isml')

        testFile2 << tplFile2.text.replaceAll('@cartridge@', 'testCartridge1')

        tplFile2.delete()

        when:
        List<String> args = ['isml', 'publish', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':testCartridge1:isml').outcome == SUCCESS
        result.task(':testCartridge2:isml').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    def 'Test taglib and usage with dependencies - isml'() {

        given:
        copyResources('use_taglib')

        File tplFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/useTaglibTest.isml.tpl')
        File testFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/useTaglibTest.isml')

        testFile << tplFile.text.replaceAll('@cartridge@', 'testCartridge1')
        tplFile.delete()

        copyResources('repo', 'repo')

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.isml'
            }

            dependencies {
                compile("com.intershop.platform:core:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:servletengine:${platformVersion}") {
                    transitive = false
                }
                compile("com.intershop.platform:isml:${platformVersion}") {
                    transitive = false
                }
                compile("javax.servlet:javax.servlet-api:${servletVersion}") {
                    transitive = false
                }
                compile("org.slf4j:slf4j-api:${slf4jVersion}") {
                    transitive = false
                }
                compile("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                    transitive = false
                }
                compile('com.test:testCartridge1:1.0.0') {
                    transitive = false
                }
            }

            repositories {
                jcenter()
                ivy {
                    url "\${project.projectDir.absolutePath.replaceAll('\\\\\\\\', '/')}/repo".toString()
                    layout('pattern') {
                        ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                        artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                    }
                }
                ivy {
                    url '${System.properties['intershop.host.url']}'
                    layout('pattern') {
                        ivy '[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml'
                        artifact '[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]'
                    }
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
                maven {
                    url '${System.properties['intershop.host.url']}'
                    credentials {
                        username '${System.properties['intershop.host.username']}'
                        password '${System.properties['intershop.host.userpassword']}'
                    }
                }
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge2'
        """.stripIndent()

        when:
        List<String> args = ['isml', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':isml').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    List<String> getVersions(String propertyName) {
        String versionsProps = System.properties[propertyName]?: ''
        String[] versionList = versionsProps.split(',')
        return versionList*.trim()
    }




}

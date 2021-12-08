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

import com.intershop.gradle.test.AbstractIntegrationKotlinSpec
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class IsmlPluginKtsSpec extends AbstractIntegrationKotlinSpec {

    @Ignore
    def 'Test taglib and usage in one Cartridge - isml'() {
        given:
        copyResources('test_taglib')

        File tplFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/taglibTest.isml.tpl')
        File testFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/taglibTest.isml')

        testFile << tplFile.text.replaceAll('@cartridge@', 'testCartridge')
        tplFile.delete()

        buildFile << """
            plugins {
                `java`
                id("com.intershop.gradle.ismltaglib")
                id("com.intershop.gradle.isml")
            }

            isml {
                enableTldScan = true
            }

            dependencies {
                ${getMainDependencies(platformVersion, servletVersion,
                                slf4jVersion, tomcatVersion)}
            }

            repositories {
                jcenter()
                ${getMainRepositories()}
            }
        """.stripIndent()

        settingsFile << """
        rootProject.name="testCartridge"
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

    @Ignore
    def 'Test isml incemental build with a changed file'() {
        given:
        copyResources('test_isml')

        buildFile << """
            plugins {
                id("com.intershop.gradle.isml")
            }

            configurations.create("implementation")

            val runtime by configurations.creating {
                extendsFrom(configurations["implementation"])
            }
            val runtimeClasspath by configurations.creating {
                extendsFrom(configurations["implementation"])
            }

            dependencies {
               ${getMainDependencies(platformVersion, servletVersion,
                slf4jVersion, tomcatVersion)}
            }

            repositories {
                jcenter()
                ${getMainRepositories()}
            }
        """.stripIndent()

        settingsFile << """
        rootProject.name="testCartridge"
        """.stripIndent()

        File ismlFile = new File(testProjectDir, 'staticfiles/cartridge/templates/default/support/test.isml')

        when:
        // change on input
        ismlFile << '\n' << """
        <%
        PipelineDictionary dict = getPipelineDictionary();
        %>
        """.stripIndent()

        List<String> args = ['isml', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        File jspFile = new File(testProjectDir, "build/generated/isml/main/pagecompile/default/support/test.jsp")
        File javaFile =  new File(testProjectDir, 'build/generated/isml/main/pagecompile/org/apache/jsp/testCartridge/default_/support/test_jsp.java')
        File classFile = new File(testProjectDir, 'build/generated/isml/main/pagecompile/org/apache/jsp/testCartridge/default_/support/test_jsp.class')


        then:
        result.task(':isml').outcome == SUCCESS
        jspFile.exists()
        javaFile.exists()
        classFile.exists()

        jspFile.lastModified() == javaFile.lastModified()
        jspFile.lastModified() == classFile.lastModified()

        when:
        def result_step2 = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result_step2.task(':isml2classMain').outcome == UP_TO_DATE

        jspFile.lastModified() == javaFile.lastModified()
        jspFile.lastModified() == classFile.lastModified()

        long lastmodified = jspFile.lastModified()

        when:
        // change on input
        ismlFile << '\n' << """
        <%
        String enablePreference = (String)dict.get("enablepreference");
        %>
        """.stripIndent()

        def result_step3 = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result_step3.task(':isml2classMain').outcome == SUCCESS

        javaFile.lastModified() >= lastmodified
        classFile.lastModified() >= lastmodified
        jspFile.lastModified() >= lastmodified
        jspFile.lastModified() == javaFile.lastModified()
        jspFile.lastModified() == classFile.lastModified()

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    @Ignore
    def 'Test isml incemental build with a changed classpath'() {
        given:
        copyResources('test_isml')
        copyResources('repo', 'repo')

        buildFile << """
            plugins {
                id("com.intershop.gradle.isml")
            }

            val adddep: String by project

            configurations.create("implementation")

            val runtime by configurations.creating {
                extendsFrom(configurations["implementation"])
            }
            val runtimeClasspath by configurations.creating {
                extendsFrom(configurations["implementation"])
            }

            dependencies {
                ${getMainDependencies(platformVersion, servletVersion,
                slf4jVersion, tomcatVersion)}
                if(adddep == "add") {
                    println("Additional dependency ...")
                    "implementation"("com.test:testCartridge1:1.0.0") {
                        isTransitive = false
                    }
                }
            }

            repositories {
                jcenter()
                ${getMainRepositories()}
                ivy {
                    url = uri("\${project.projectDir.absolutePath.replace("\\\\", "/")}/repo".toString())
                    patternLayout {
                        ivy("[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml")
                        artifact("[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]")
                    }
                }
            }
        """.stripIndent()

        settingsFile << """
        rootProject.name="testCartridge"
        """.stripIndent()

        when:
        List<String> args = ['isml', '-Padddep=""', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':isml').outcome == SUCCESS

        when:
        def result_step2 = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result_step2.task(':isml2classMain').outcome == UP_TO_DATE

        when:
        List<String> args3 = ['isml', '-Padddep=add', '-s', '-i']
        def result_step3 = getPreparedGradleRunner()
                .withArguments(args3)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result_step3.output.contains("Additional dependency ...")
        result_step3.task(':isml2classMain').outcome == SUCCESS

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
                id("com.intershop.gradle.isml")
            }

            configurations.create("implementation")

            repositories {
                mavenLocal()
                mavenCentral()
            }
        """.stripIndent()

        settingsFile << """
        rootProject.name="testCartridge"
        """.stripIndent()

        when:
        List<String> args = ['isml', '-s']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':isml').outcome == SUCCESS
        (new File(testProjectDir, 'build/generated/isml/main/pagecompile/org/apache/jsp/testCartridge/common/errorPages/error400_jsp.class')).exists()

        where:
        gradleVersion << supportedGradleVersions
        platformVersion << getVersions('platform.intershop.versions')
        servletVersion << getVersions('servlet.version')
        slf4jVersion << getVersions('slf4j.version')
        tomcatVersion << getVersions('tomcat.version')
    }

    @Ignore
    def 'Test taglib and usage with project dependencies - isml'() {

        given:

        settingsFile << """
        rootProject.name="testProject"
        """.stripIndent()

        buildFile << """
            plugins {
                `java`
                `ivy-publish`
                id("com.intershop.gradle.ismltaglib")
                id("com.intershop.gradle.isml")
            }

            subprojects {
                apply(plugin = "java")
                apply(plugin = "ivy-publish")
                apply(plugin = "com.intershop.gradle.ismltaglib")
                apply(plugin = "com.intershop.gradle.isml")

                version = "1.0.0"
                group = "com.test"

                isml {
                    enableTldScan = true
                }

                dependencies {
                    ${getMainDependencies(platformVersion, servletVersion, slf4jVersion, tomcatVersion)}
                }

                repositories {
                    jcenter()
                    ${getMainRepositories()}
                }
            }

            project(":testCartridge1") {
                apply(plugin = "ivy-publish")

                tasks {
                    val createCartridge = register<Zip>("createCartridge") {
                        dependsOn(project.tasks["isml"])

                        from("staticfiles/cartridge")
                        from(isml2classMain.get().outputs)
                        into("\${project.name}/release")
                    }
                }

                publishing {
                    publications {
                        create("ivy", IvyPublication::class.java) {
                            artifact(tasks.getByName("createCartridge")) {
                                type = "cartridge"
                            }
                            artifact(tasks.getByName("jar"))
                        }
                    }
                    repositories {
                        ivy {
                            url = uri("\${project.buildDir.absolutePath.replace("\\\\", "/")}/repo".toString())
                            patternLayout {
                                ivy("[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml")
                                artifact("[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]")
                            }
                        }
                    }
                }
            }
            
            project(":testCartridge2") {
                dependencies {
                    implementation(project(":testCartridge1"))
                }
            }
        """.stripIndent()

        createSubProject('testCartridge1', '')
        createSubProject('testCartridge2', '')

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
        List<String> args = ['isml', 'publish', '-s', '-d']

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

    @Ignore
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
                `java`
                id("com.intershop.gradle.ismltaglib")
                id("com.intershop.gradle.isml")
            }

            isml {
                enableTldScan = true
            }

            dependencies {
                ${getMainDependencies(platformVersion, servletVersion,
                slf4jVersion, tomcatVersion)}
                "implementation"("com.test:testCartridge1:1.0.0") {
                    isTransitive = false
                }
            }

            repositories {
                jcenter()
                ivy {
                    url = uri("\${project.projectDir.absolutePath.replace("\\\\", "/")}/repo".toString())
                    patternLayout {
                        ivy("[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml")
                        artifact("[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]")
                    }
                }
                ${getMainRepositories()}
            }
        """.stripIndent()

        settingsFile << """
        rootProject.name="testCartridge2"
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

    String getMainDependencies(String platformVersion, String servletVersion,
                               String slf4jVersion, String tomcatVersion) {
        return """
            "implementation"("com.intershop.platform:core:${platformVersion}") {
                isTransitive = false
            }
            "implementation"("com.intershop.platform:servletengine:${platformVersion}") {
                isTransitive = false
            }
            "implementation"("com.intershop.platform:isml:${platformVersion}") {
                isTransitive = false
            }
            "implementation"("javax.servlet:javax.servlet-api:${servletVersion}") {
                isTransitive = false
            }
            "implementation"("org.slf4j:slf4j-api:${slf4jVersion}") {
                isTransitive = false
            }
            "implementation"("org.apache.tomcat:tomcat-el-api:${tomcatVersion}") {
                isTransitive = false
            }""".stripIndent()
    }

    String getMainRepositories() {
        return """
            ivy {
                url = uri("${System.properties['intershop.host.url']}")
                patternLayout {
                    ivy("[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml")
                    artifact("[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]")
                }
                credentials {
                    username = "${System.properties['intershop.host.username']}"
                    password = "${System.properties['intershop.host.userpassword']}"
                }
            }
            maven {
                url = uri("${System.properties['intershop.host.url']}")
                credentials {
                    username = "${System.properties['intershop.host.username']}"
                    password = "${System.properties['intershop.host.userpassword']}"
                }
            }""".stripIndent()
    }


}

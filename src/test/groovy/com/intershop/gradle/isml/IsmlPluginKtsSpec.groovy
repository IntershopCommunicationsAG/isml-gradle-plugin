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

        File tplFile = new File(testProjectDir, 'src/main/ism/testCartridge/default/support/taglibTest.isml.tpl')
        File testFile = new File(testProjectDir, 'src/main/ism/testCartridge/default/support/taglibTest.isml')

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

    def 'Test isml incremental build with a changed file'() {
        given:
        copyResources('test_isml')

        buildFile << """
            plugins {
                id("com.intershop.gradle.isml")
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        settingsFile << """
        rootProject.name="testCartridge"
        """.stripIndent()

        File ismlFile = new File(testProjectDir, 'src/main/isml/testCartridge/default/support/test.isml')

        when:
        // change on input
        ismlFile << '\n' << """
        <%
        PipelineDictionary dict = getPipelineDictionary();
        %>
        """.stripIndent()

        List<String> args = ['isml', '-s']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        File jspFile = new File(testProjectDir, "build/generated/isml/main/default/support/test.jsp")
        File javaFile =  new File(testProjectDir, 'build/generated/jsp/main/org/apache/jsp/testCartridge/default_/support/test_jsp.java')

        then:
        result.task(':isml').outcome == SUCCESS
        result.task(':isml').outcome == SUCCESS
        result.task(':isml2jspMain').outcome == SUCCESS
        result.task(':jsp2javaMain').outcome == SUCCESS

        jspFile.exists()
        javaFile.exists()

        when:
        def result_step2 = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result_step2.task(':jsp2javaMain').outcome == UP_TO_DATE

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
        result_step3.task(':jsp2javaMain').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
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
                java
                id("com.intershop.gradle.isml")
            }

            dependencies {
                implementation(platform("org.apache.tomcat:tomcat-jasper:9.0.56"))
                implementation(platform("org.slf4j:slf4j-api:1.7.32"))
            }
            repositories {
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
        result.task(':isml2jspMain').outcome == SUCCESS
        result.task(':jsp2javaMain').outcome == SUCCESS
        (new File(testProjectDir, 'build/generated/isml/main/common/errorPages/error400.jsp')).exists()
        (new File(testProjectDir, 'build/generated/isml/main/default/support/test.jsp')).exists()
        (new File(testProjectDir, 'build/generated/jsp/main/org/apache/jsp/testCartridge/common/errorPages/error400_jsp.java')).exists()
        (new File(testProjectDir, 'build/generated/jsp/main/org/apache/jsp/testCartridge/default_/support/test_jsp.java')).exists()

        where:
        gradleVersion << supportedGradleVersions
    }
}

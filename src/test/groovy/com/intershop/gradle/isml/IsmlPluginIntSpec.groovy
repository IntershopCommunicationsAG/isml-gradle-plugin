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

import com.intershop.gradle.test.AbstractIntegrationGroovySpec
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Unroll
class IsmlPluginIntSpec extends AbstractIntegrationGroovySpec {

    def 'Test isml incremental build with a changed file'() {
        given:
        copyResources('test_isml')

        buildFile << """
            plugins {
                id 'com.intershop.gradle.isml'
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge'
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

    def 'Test isml'() {
        given:
        copyResources('test_isml')

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.isml'
            }

            dependencies {
                implementation platform('org.apache.tomcat:tomcat-jasper:9.0.56')
                implementation platform('org.slf4j:slf4j-api:1.7.32')
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        File settingsGradle = new File(testProjectDir, 'settings.gradle')
        settingsGradle << """
        rootProject.name='testCartridge'
        """.stripIndent()

        when:
        List<String> args = ['isml', '-s' ]

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

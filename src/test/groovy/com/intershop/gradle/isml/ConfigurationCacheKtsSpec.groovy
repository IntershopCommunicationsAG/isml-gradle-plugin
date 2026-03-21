package com.intershop.gradle.isml

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

import com.intershop.gradle.test.AbstractIntegrationKotlinSpec
import spock.lang.Unroll

/**
 * Integration tests verifying that Isml2Jsp and Jsp2Java tasks are compatible with
 * Gradle's configuration cache and that the cache is properly reused across runs.
 */
@Unroll
class ConfigurationCacheKtsSpec extends AbstractIntegrationKotlinSpec {

    // Shared build script used across tests
    String BASE_BUILD = """
        plugins {
            java
            id("com.intershop.gradle.isml")
        }

        dependencies {
            implementation("org.apache.tomcat:tomcat-jasper:11.0.11")
            implementation("org.slf4j:slf4j-api:1.7.36")
        }

        repositories {
            mavenCentral()
            mavenLocal()
        }
    """.stripIndent()

    def setup() {
        settingsFile << """
            rootProject.name = "testCartridge"
        """.stripIndent()
    }

    def 'isml2jspMain succeeds on first run and reuses configuration cache on second run'() {
        given:
        copyResources('test_isml')
        buildFile << BASE_BUILD

        when: 'first run stores the configuration cache'
        def result1 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'task executed successfully and cache entry was stored'
        result1.task(':isml2jspMain').outcome == SUCCESS
        result1.output.toLowerCase().contains('configuration cache entry stored')
        new File(testProjectDir, 'build/generated/isml/main/default/support/test.jsp').exists()

        when: 'second run should reuse the configuration cache'
        def result2 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'configuration cache entry is reused and task is up-to-date'
        result2.output.toLowerCase().contains('configuration cache entry reused')
        result2.task(':isml2jspMain').outcome == UP_TO_DATE

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'jsp2javaMain succeeds on first run and reuses configuration cache on second run'() {
        given:
        copyResources('test_isml')
        buildFile << BASE_BUILD

        when: 'first run stores the configuration cache'
        def result1 = getPreparedGradleRunner()
                .withArguments('jsp2javaMain', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'both tasks executed successfully and cache entry was stored'
        result1.task(':isml2jspMain').outcome == SUCCESS
        result1.task(':jsp2javaMain').outcome == SUCCESS
        result1.output.toLowerCase().contains('configuration cache entry stored')
        new File(testProjectDir, 'build/generated/jsp/main/org/apache/jsp/testCartridge/default_/support/test_jsp.java').exists()

        when: 'second run should reuse the configuration cache'
        def result2 = getPreparedGradleRunner()
                .withArguments('jsp2javaMain', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'configuration cache entry is reused and tasks are up-to-date'
        result2.output.toLowerCase().contains('configuration cache entry reused')
        result2.task(':isml2jspMain').outcome == UP_TO_DATE
        result2.task(':jsp2javaMain').outcome == UP_TO_DATE

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'isml aggregate task succeeds on first run and reuses configuration cache on second run'() {
        given:
        copyResources('test_isml')
        buildFile << BASE_BUILD

        when: 'first run stores the configuration cache'
        def result1 = getPreparedGradleRunner()
                .withArguments('isml', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'all tasks executed successfully and cache entry was stored'
        result1.task(':isml2jspMain').outcome == SUCCESS
        result1.task(':jsp2javaMain').outcome == SUCCESS
        result1.task(':isml').outcome == SUCCESS
        result1.output.toLowerCase().contains('configuration cache entry stored')

        when: 'second run reuses the configuration cache'
        def result2 = getPreparedGradleRunner()
                .withArguments('isml', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'configuration cache entry is reused, tasks are up-to-date'
        result2.output.toLowerCase().contains('configuration cache entry reused')
        result2.task(':isml2jspMain').outcome == UP_TO_DATE
        result2.task(':jsp2javaMain').outcome == UP_TO_DATE

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'configuration cache is invalidated and tasks re-execute when an ISML input file changes'() {
        given:
        copyResources('test_isml')
        buildFile << BASE_BUILD

        def ismlFile = new File(testProjectDir, 'src/main/isml/testCartridge/default/support/test.isml')

        when: 'first run, stores configuration cache and executes tasks'
        def result1 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result1.task(':isml2jspMain').outcome == SUCCESS
        result1.output.toLowerCase().contains('configuration cache entry stored')

        when: 'second run without changes, reuses cache, task is up-to-date'
        def result2 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result2.output.toLowerCase().contains('configuration cache entry reused')
        result2.task(':isml2jspMain').outcome == UP_TO_DATE

        when: 'an input ISML file is modified'
        ismlFile << '\n<%-- modified comment --%>'

        and: 'third run, configuration cache is reused but task re-executes due to changed input'
        def result3 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--configuration-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'the configuration cache entry is still reused (build scripts did not change)'
        result3.output.toLowerCase().contains('configuration cache entry reused')
        // the task itself must re-run because its input changed
        result3.task(':isml2jspMain').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }
}

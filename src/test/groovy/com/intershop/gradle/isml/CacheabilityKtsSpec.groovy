package com.intershop.gradle.isml

import com.intershop.gradle.test.AbstractIntegrationKotlinSpec

import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Integration tests to verify that the Isml2Jsp and Jsp2Java tasks are properly cacheable.
 */
class CacheabilityKtsSpec extends AbstractIntegrationKotlinSpec {

    // Base build configuration shared across all tests
    String TASK_BASE_CONFIGURATION = """
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

    // Unique, temporary build cache directory for each test method
    File tmpBuildCacheDir

    def setup() {
        tmpBuildCacheDir = Files.createTempDirectory("gradle-build-cache-${CacheabilityKtsSpec.simpleName}-").toFile()

        settingsFile.text = """
            buildCache {
                local {
                    directory = file("${tmpBuildCacheDir.absolutePath.replace('\\', '\\\\')}")
                }
            }
            rootProject.name = "testCartridge"
        """.stripIndent()
    }

    def cleanup() {
        tmpBuildCacheDir?.deleteDir()
    }

    def 'Isml2Jsp task should be cacheable'() {
        given:
        copyResources('test_isml')

        buildFile << """
            ${TASK_BASE_CONFIGURATION}
        """.stripIndent()

        when: 'First build populates the build cache'
        def result1 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Task executes successfully and result is stored in cache'
        result1.task(':isml2jspMain').outcome == SUCCESS
        new File(testProjectDir, 'build/generated/isml/main/default/support/test.jsp').exists()

        when: 'Clean and rebuild using the build cache'
        def result2 = getPreparedGradleRunner()
                .withArguments('clean', 'isml2jspMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Task output is restored from cache'
        result2.task(':isml2jspMain').outcome == FROM_CACHE
        new File(testProjectDir, 'build/generated/isml/main/default/support/test.jsp').exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'Jsp2Java task should be cacheable'() {
        given:
        copyResources('test_isml')

        buildFile << """
            ${TASK_BASE_CONFIGURATION}
        """.stripIndent()

        when: 'First build populates the build cache'
        def result1 = getPreparedGradleRunner()
                .withArguments('jsp2javaMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Both tasks execute successfully and results are stored in cache'
        result1.task(':isml2jspMain').outcome == SUCCESS
        result1.task(':jsp2javaMain').outcome == SUCCESS
        new File(testProjectDir, 'build/generated/jsp/main/org/apache/jsp/testCartridge/default_/support/test_jsp.java').exists()

        when: 'Clean and rebuild using the build cache'
        def result2 = getPreparedGradleRunner()
                .withArguments('clean', 'jsp2javaMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Both tasks are restored from cache'
        result2.task(':isml2jspMain').outcome == FROM_CACHE
        result2.task(':jsp2javaMain').outcome == FROM_CACHE
        new File(testProjectDir, 'build/generated/jsp/main/org/apache/jsp/testCartridge/default_/support/test_jsp.java').exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'Isml2Jsp task should produce a cache miss when an input file changes'() {
        given:
        copyResources('test_isml')

        buildFile << """
            ${TASK_BASE_CONFIGURATION}
        """.stripIndent()

        when: 'First build populates the build cache'
        def result1 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Task executes successfully'
        result1.task(':isml2jspMain').outcome == SUCCESS

        when: 'An input ISML file is modified'
        def ismlFile = new File(testProjectDir, 'src/main/isml/testCartridge/default/support/test.isml')
        ismlFile << '\n<%-- modified comment --%>'

        and: 'Rebuild with build cache'
        def result2 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Task re-executes because the input changed'
        result2.task(':isml2jspMain').outcome == SUCCESS

        when: 'Input file is reverted to its original content'
        copyResources('test_isml')

        and: 'Rebuild with build cache after clean'
        def result3 = getPreparedGradleRunner()
                .withArguments('clean', 'isml2jspMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Task is loaded from cache because the original input is restored'
        result3.task(':isml2jspMain').outcome == FROM_CACHE

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'Isml2Jsp task should use cache across different project directories'() {
        given:
        copyResources('test_isml')

        buildFile << """
            ${TASK_BASE_CONFIGURATION}
        """.stripIndent()

        when: 'First build in the original project directory'
        def result1 = getPreparedGradleRunner()
                .withArguments('isml2jspMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Task executes successfully and populates the cache'
        result1.task(':isml2jspMain').outcome == SUCCESS

        when: 'A second project is created in a different directory with identical content'
        def testProjectDir2 = Files.createTempDirectory("gradle-test-project-${CacheabilityKtsSpec.simpleName}-").toFile()
        testProjectDir2.deleteOnExit()
        copyDirectory(testProjectDir, testProjectDir2)

        and: 'Build in the new directory using the shared build cache'
        def result2 = getPreparedGradleRunner()
                .withProjectDir(testProjectDir2)
                .withArguments('clean', 'isml2jspMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Task is restored from cache despite being in a different directory'
        result2.task(':isml2jspMain').outcome == FROM_CACHE

        cleanup:
        testProjectDir2?.deleteDir()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'Jsp2Java task should use cache across different project directories'() {
        given:
        copyResources('test_isml')

        buildFile << """
            ${TASK_BASE_CONFIGURATION}
        """.stripIndent()

        when: 'First build in the original project directory'
        def result1 = getPreparedGradleRunner()
                .withArguments('jsp2javaMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Both tasks execute successfully and populate the cache'
        result1.task(':isml2jspMain').outcome == SUCCESS
        result1.task(':jsp2javaMain').outcome == SUCCESS

        when: 'A second project is created in a different directory with identical content'
        def testProjectDir2 = Files.createTempDirectory("gradle-test-project-${CacheabilityKtsSpec.simpleName}-").toFile()
        testProjectDir2.deleteOnExit()
        copyDirectory(testProjectDir, testProjectDir2)

        and: 'Build in the new directory using the shared build cache'
        def result2 = getPreparedGradleRunner()
                .withProjectDir(testProjectDir2)
                .withArguments('clean', 'jsp2javaMain', '--build-cache', '-s')
                .withGradleVersion(gradleVersion)
                .build()

        then: 'Both tasks are restored from cache despite being in a different directory'
        result2.task(':isml2jspMain').outcome == FROM_CACHE
        result2.task(':jsp2javaMain').outcome == FROM_CACHE

        cleanup:
        testProjectDir2?.deleteDir()

        where:
        gradleVersion << supportedGradleVersions
    }

    private static void copyDirectory(File source, File target) {
        def sourceRoot = source.toPath()
        def targetRoot = target.toPath()

        Files.walk(sourceRoot).withCloseable { stream ->
            stream.forEach { currentPath ->
                def relativePath = sourceRoot.relativize(currentPath)
                def destinationPath = targetRoot.resolve(relativePath)
                if (Files.isDirectory(currentPath)) {
                    Files.createDirectories(destinationPath)
                } else {
                    Files.copy(currentPath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}


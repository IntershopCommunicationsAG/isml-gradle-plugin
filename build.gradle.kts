import com.jfrog.bintray.gradle.BintrayExtension
import org.asciidoctor.gradle.AsciidoctorExtension
import org.asciidoctor.gradle.AsciidoctorTask
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*
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
plugins {
    // project plugins
    `java-gradle-plugin`
    groovy
    id("nebula.kotlin") version "1.3.21"

    // test coverage
    jacoco

    // ide plugin
    idea

    // publish plugin
    `maven-publish`

    // intershop version plugin
    id("com.intershop.gradle.scmversion") version "4.1.0"

    // plugin for documentation
    id("org.asciidoctor.convert") version "1.5.10"

    // documentation
    id("org.jetbrains.dokka") version "0.9.17"

    // code analysis for kotlin
    id("io.gitlab.arturbosch.detekt") version "1.0.0-RC13"

    // plugin for publishing to Gradle Portal
    id("com.gradle.plugin-publish") version "0.10.1"

    // plugin for publishing to jcenter
    id("com.jfrog.bintray") version "1.8.4"
}

scm {
    version.initialVersion = "1.0.0"
}

group = "com.intershop.gradle.isml"
description = "ISML plugin for Intershop"
version = scm.version.version

val ismlPluginId = "com.intershop.gradle.isml"
val ismltaglibPluginId = "com.intershop.gradle.ismltaglib"

gradlePlugin {
    plugins {
        create("ismlPlugin") {
            id = ismlPluginId
            implementationClass = "com.intershop.gradle.isml.IsmlPlugin"
            displayName = project.name
            description = project.description
        }
        create("ismltaglibPlugin") {
            id = ismltaglibPluginId
            implementationClass = "com.intershop.gradle.isml.IsmlTagLibPlugin"
            displayName = project.name
            description = project.description
        }
    }
}

pluginBundle {
    val pluginURL = "https://github.com/IntershopCommunicationsAG/${project.name}"
    website = pluginURL
    vcsUrl = pluginURL
    tags = listOf("intershop", "gradle", "plugin", "build", "isml")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// set correct project status
if (project.version.toString().endsWith("-SNAPSHOT")) {
    status = "snapshot'"
}

detekt {
    version = "1.0.0-RC13"

    input = files("src/main/kotlin")
    config = files("detekt.yml")
    filters = ".*test.*,.*/resources/.*,.*/tmp/.*"
}

configure<AsciidoctorExtension> {
    noDefaultRepositories = true
}

tasks {
    withType<Test>().configureEach {
        systemProperty("intershop.gradle.versions", "5.2")
        systemProperty("platform.intershop.versions", "11.1.1")
        systemProperty("servlet.version", "3.0.1")
        systemProperty("slf4j.version", "1.7.12")
        systemProperty("tomcat.version", "7.0.42")
        systemProperty("intershop.host.url", "https://repository.intershop.de/releases/")
        systemProperty("intershop.host.username", System.getenv("ISHUSERNAME") ?: System.getProperty("ISHUSERNAME"))
        systemProperty("intershop.host.userpassword", System.getenv("ISHKEY") ?: System.getProperty("ISHKEY"))

        dependsOn("jar")
    }

    val copyAsciiDoc = register<Copy>("copyAsciiDoc") {
        includeEmptyDirs = false

        val outputDir = file("$buildDir/tmp/asciidoctorSrc")
        val inputFiles = fileTree(mapOf("dir" to rootDir,
                "include" to listOf("**/*.asciidoc"),
                "exclude" to listOf("build/**")))

        inputs.files.plus( inputFiles )
        outputs.dir( outputDir )

        doFirst {
            outputDir.mkdir()
        }

        from(inputFiles)
        into(outputDir)
    }

    withType<AsciidoctorTask> {
        dependsOn("copyAsciiDoc")

        sourceDir = file("$buildDir/tmp/asciidoctorSrc")
        sources(delegateClosureOf<PatternSet> {
            include("README.asciidoc")
        })

        backends("html5", "docbook")
        options = mapOf( "doctype" to "article",
                "ruby"    to "erubis")
        attributes = mapOf(
                "latestRevision"        to  project.version,
                "toc"                   to "left",
                "toclevels"             to "2",
                "source-highlighter"    to "coderay",
                "icons"                 to "font",
                "setanchors"            to "true",
                "idprefix"              to "asciidoc",
                "idseparator"           to "-",
                "docinfo1"              to "true")
    }

    withType<JacocoReport> {
        reports {
            xml.isEnabled = true
            html.isEnabled = true

            html.destination = File(project.buildDir, "jacocoHtml")
        }

        val jacocoTestReport by tasks
        jacocoTestReport.dependsOn("test")
    }

    getByName("bintrayUpload")?.dependsOn("asciidoctor")
    getByName("jar")?.dependsOn("asciidoctor")

    val compileKotlin by getting(KotlinCompile::class) {
        kotlinOptions.jvmTarget = "1.8"
    }

    val dokka by existing(DokkaTask::class) {
        reportUndocumented = false
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"

        // Java 8 is only version supported both by Oracle/OpenJDK and Dokka itself
        // https://github.com/Kotlin/dokka/issues/294
        enabled = JavaVersion.current().isJava8
    }

    register<Jar>("sourceJar") {
        description = "Creates a JAR that contains the source code."

        from(sourceSets.getByName("main").allSource)
        archiveClassifier.set("sources")
    }

    register<Jar>("javaDoc") {
        dependsOn(dokka)
        from(dokka)
        archiveClassifier.set("javadoc")
    }
}

publishing {
    publications {
        create("intershopMvn", MavenPublication::class.java) {

            from(components["java"])
            artifact(tasks.getByName("sourceJar"))
            artifact(tasks.getByName("javaDoc"))

            artifact(File(buildDir, "asciidoc/html5/README.html")) {
                classifier = "reference"
            }

            artifact(File(buildDir, "asciidoc/docbook/README.xml")) {
                classifier = "docbook"
            }

            pom.withXml {
                val root = asNode()
                root.appendNode("name", project.name)
                root.appendNode("description", project.description)
                root.appendNode("url", "https://github.com/IntershopCommunicationsAG/${project.name}")

                val scm = root.appendNode("scm")
                scm.appendNode("url", "https://github.com/IntershopCommunicationsAG/${project.name}")
                scm.appendNode("connection", "git@github.com:IntershopCommunicationsAG/${project.name}.git")

                val org = root.appendNode("organization")
                org.appendNode("name", "Intershop Communications")
                org.appendNode("url", "http://intershop.com")

                val license = root.appendNode("licenses").appendNode("license")
                license.appendNode("name", "Apache License, Version 2.0")
                license.appendNode("url", "http://www.apache.org/licenses/LICENSE-2.0")
                license.appendNode("distribution", "repo")
            }
        }
    }
}

bintray {
    user = System.getenv("BINTRAY_USER")
    key = System.getenv("BINTRAY_KEY")

    setPublications("intershopMvn")

    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        repo = "maven"
        name = project.name
        userOrg = "intershopcommunicationsag"

        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/IntershopCommunicationsAG/${project.name}"

        desc = project.description
        websiteUrl = "https://github.com/IntershopCommunicationsAG/${project.name}"
        issueTrackerUrl = "https://github.com/IntershopCommunicationsAG/${project.name}/issues"

        setLabels("intershop", "gradle", "plugin", "build", "isml")
        publicDownloadNumbers = true

        version(delegateClosureOf<BintrayExtension.VersionConfig> {
            name = project.version.toString()
            desc = "${project.description} ${project.version}"
            released  = Date().toString()
            vcsTag = project.version.toString()
        })
    })
}

dependencies {
    compileOnly("org.jetbrains:annotations:15.0")

    compileOnly("org.eclipse.jdt.core.compiler:ecj:4.2.2")
    compileOnly("org.apache.tomcat:tomcat-jasper:7.0.42")
    compileOnly("com.intershop.platform:isml:13.0.8")

    testCompile("commons-io:commons-io:2.2")
    testImplementation("com.intershop.gradle.test:test-gradle-plugin:3.1.0-dev.2")
    testImplementation(gradleTestKit())
}

repositories {
    jcenter()

    ivy {
        url = uri("https://repository.intershop.de/releases/")
        patternLayout {
            ivy("[organisation]/[module]/[revision]/[type]s/ivy-[revision].xml")
            artifact("[organisation]/[module]/[revision]/[ext]s/[artifact]-[type](-[classifier])-[revision].[ext]")
        }
        credentials {
            username = System.getenv("ISHUSERNAME") ?: System.getProperty("ISHUSERNAME")
            password = System.getenv("ISHKEY") ?: System.getProperty("ISHKEY")
        }
    }
}
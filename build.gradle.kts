import org.asciidoctor.gradle.jvm.AsciidoctorTask

/*
 * Copyright 2022 Intershop Communications AG.
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

    kotlin("jvm") version "1.9.25"

    // test coverage
    jacoco

    // ide plugin
    idea

    // publish plugin
    `maven-publish`

    // artifact signing - necessary on Maven Central
    signing

    // plugin for documentation
    id("org.asciidoctor.jvm.convert") version "4.0.3"

    // documentation
    id("org.jetbrains.dokka") version "1.9.20"

    // plugin for publishing to Gradle Portal
    id("com.gradle.plugin-publish") version "1.3.0"
}

group = "com.intershop.gradle.isml"
description = "ISML plugin for Intershop"
// apply gradle property 'projectVersion' to project.version, default to 'LOCAL'
val projectVersion : String? by project
version = projectVersion ?: "LOCAL"

val sonatypeUsername: String? by project
val sonatypePassword: String? by project

repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
}

val pluginUrl = "https://github.com/IntershopCommunicationsAG/${project.name}"
gradlePlugin {
    website = pluginUrl
    vcsUrl = pluginUrl
    plugins {
        create("ismlPlugin") {
            id = "com.intershop.gradle.isml"
            implementationClass = "com.intershop.gradle.isml.IsmlPlugin"
            displayName = project.name
            description = project.description
            tags = listOf("intershop", "build", "isml")
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// set correct project status
if (project.version.toString().endsWith("-SNAPSHOT")) {
    status = "snapshot"
}

testing {
    suites.withType<JvmTestSuite> {
        useSpock()
        dependencies {
            implementation("com.intershop.gradle.test:test-gradle-plugin:5.1.0")
            implementation(gradleTestKit())
        }

        targets {
            all {
                testTask.configure {
                    systemProperty("intershop.gradle.versions", "8.5,8.10.2")
                    testLogging {
                        showStandardStreams = true
                    }
                }
            }
        }
    }
}

tasks {
    val copyAsciiDocTask = register<Copy>("copyAsciiDoc") {
        includeEmptyDirs = false

        val outputDir = project.layout.buildDirectory.dir("tmp/asciidoctorSrc")
        val inputFiles = fileTree(rootDir) {
            include("**/*.asciidoc")
            exclude("build/**")
        }

        inputs.files.plus( inputFiles )
        outputs.dir( outputDir )

        doFirst {
            outputDir.get().asFile.mkdir()
        }

        from(inputFiles)
        into(outputDir)
    }

    withType<AsciidoctorTask> {
        dependsOn(copyAsciiDocTask)
        sourceDirProperty.set(project.provider<Directory>{
            val dir = project.objects.directoryProperty()
            dir.set(copyAsciiDocTask.get().outputs.files.first())
            dir.get()
        })
        sources {
            include("README.asciidoc")
        }

        outputOptions {
            setBackends(listOf("html5", "docbook"))
        }

        setOptions(mapOf(
            "doctype"               to "article",
            "ruby"                  to "erubis"
        ))
        setAttributes(mapOf(
            "latestRevision"        to project.version,
            "toc"                   to "left",
            "toclevels"             to "2",
            "source-highlighter"    to "coderay",
            "icons"                 to "font",
            "setanchors"            to "true",
            "idprefix"              to "asciidoc",
            "idseparator"           to "-",
            "docinfo1"              to "true"
        ))
    }

    withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)

            html.outputLocation.set( project.layout.buildDirectory.dir("jacocoHtml") )
        }

        val jacocoTestReport by tasks
        jacocoTestReport.dependsOn(test)
    }

    jar.configure {
        dependsOn(asciidoctor)
    }

    dokkaJavadoc.configure {
        outputDirectory.set(project.layout.buildDirectory.dir("dokka"))
    }

    withType<Sign> {
        val sign = this
        withType<PublishToMavenLocal> {
            this.dependsOn(sign)
        }
        withType<PublishToMavenRepository> {
            this.dependsOn(sign)
        }
    }

    afterEvaluate {
        named<Jar>("javadocJar") {
            dependsOn(dokkaJavadoc)
            from(dokkaJavadoc)
        }
    }
}

publishing {
    publications {
        create("intershopMvn", MavenPublication::class.java) {

            from(components["java"])

            artifact(project.layout.buildDirectory.file("docs/asciidoc/html5/README.html")) {
                classifier = "reference"
            }

            artifact(project.layout.buildDirectory.file("docs/asciidoc/docbook/README.xml")) {
                classifier = "docbook"
            }

            pom {
                name.set(project.name)
                description.set(project.description)
                url.set(pluginUrl)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                organization {
                    name.set("Intershop Communications AG")
                    url.set("http://intershop.com")
                }
                developers {
                    developer {
                        id.set("m-raab")
                        name.set("M. Raab")
                        email.set("mraab@intershop.de")
                    }
                }
                scm {
                    connection.set("git@github.com:IntershopCommunicationsAG/${project.name}.git")
                    developerConnection.set("git@github.com:IntershopCommunicationsAG/${project.name}.git")
                    url.set(pluginUrl)
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2"
            val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }
}

signing {
    sign(publishing.publications["intershopMvn"])
}

dependencies {
    compileOnly("org.apache.tomcat:tomcat-jasper:10.1.30")
    compileOnly("org.apache.tomcat:tomcat-api:10.1.30")

    compileOnly("com.intershop.icm:isml-parser:12.0.1")

    implementation(gradleKotlinDsl())
    implementation("com.intershop.gradle.resourcelist:resourcelist-gradle-plugin:5.0.2")
}

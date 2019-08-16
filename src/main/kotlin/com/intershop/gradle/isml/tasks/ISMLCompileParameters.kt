package com.intershop.gradle.isml.tasks

import org.apache.log4j.Level
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters
import java.io.File

interface ISMLCompileParameters : WorkParameters {

    val sourceDir: Property<File>

    val outputDir: Property<File>

    val encoding: Property<String>

    val jspPackage: Property<String>

    val sourceCompatibility: Property<String>

    val targetCompatibility: Property<String>

    val eclipseConfFile: Property<File>

    val compilerOut: Property<File>

    val compilerError: Property<File>

    val classpath: Property<String>

    val tempWebInfFolder: Property<File>

    val logLevel: Property<Level>
}
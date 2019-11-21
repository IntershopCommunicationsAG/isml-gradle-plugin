package com.intershop.gradle.isml.tasks

import org.apache.log4j.Category
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ListJarScanFilter(var includeNames : MutableList<String>,
                        var excludeNames : MutableList<String>,
                        var logLevel : Level) : JarScanFilter {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.name)
    }

    init {
        LogManager.getCurrentLoggers().iterator().forEach {
            if(it is Category) {
                it.level = logLevel
            }
        }
    }

    override fun check(jarScanType: JarScanType?, jarName: String?): Boolean {
        var rv = true
        if(jarName != null) {
            logger.debug("Check name '{}' against list '{}'.", jarName, includeNames)

            if(includeNames.size > 0) {
                rv = includeNames.find { it.startsWith(jarName) } != null
            }
            if (excludeNames.size > 0) {
                rv = ! (excludeNames.find { it.startsWith(jarName) } != null)
            }
        }
        return rv
    }
}
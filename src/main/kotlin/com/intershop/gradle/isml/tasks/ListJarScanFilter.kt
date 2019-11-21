package com.intershop.gradle.isml.tasks

import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ListJarScanFilter(var includeNames : MutableList<String>,
                        var excludeNames : MutableList<String>) : JarScanFilter {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("ListJarScanFilter")
    }

    override fun check(jarScanType: JarScanType?, jarName: String?): Boolean {
        var rv = true
        if(jarName != null) {
            logger.debug("Check name --> {} <-- against list {}.", jarName, includeNames)
            if(includeNames.size > 0) {
                rv = includeNames.find { it.startsWith(jarName) } != null
            } else {
                if (excludeNames.size > 0) {
                    rv = excludeNames.find { it.startsWith(jarName) } == null
                }
            }
        }
        return rv
    }
}
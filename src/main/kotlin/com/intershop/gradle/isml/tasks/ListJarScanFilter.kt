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
        var rv = false
        if(jarName != null) {
            logger.debug("Check name --> {} <-- against list {}.", jarName, includeNames)
            rv = includeNames.size > 0 && includeNames.find { it.startsWith(jarName) } != null
            if(rv == true) return rv

            rv = excludeNames.find { it.startsWith(jarName) } == null
        }
        return rv
    }
}
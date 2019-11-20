package com.intershop.gradle.isml.tasks

import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType

class ListJarScanFilter(var includeNames : MutableList<String>,
                        var excludeNames : MutableList<String>) : JarScanFilter {

    override fun check(jarScanType: JarScanType?, jarName: String?): Boolean {
        var rv = false
        if(jarName != null) {
            rv = includeNames.size > 0 && includeNames.find { it.startsWith(jarName) } != null
            if(rv == true) return rv

            rv = excludeNames.find { it.startsWith(jarName) } == null
        }
        return rv
    }
}
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

package com.intershop.gradle.isml.util

import groovy.transform.CompileStatic
import org.gradle.api.Named
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GUtil

@CompileStatic
class IsmlSourceSet implements Named {
    
    String name
    
    String ismlPath = "staticfiles/cartridge/templates"
    
    IsmlSourceSet(String displayName) {
        this.name = displayName
    }
    
    // Tasknames
    String getIsmlTaskName() {
        return getTaskName('isml2class', name);
    }
    
    String getTaskName(String verb, String target) {
        if (verb == null) {
            return "${getTaskBaseName()}${GUtil.toCamelCase(target)}"
        }
        if (target == null) {
            return "${verb}${GUtil.toCamelCase(name)}" 
        }
        return "${verb}${GUtil.toCamelCase(target)}"
    }
    
    private String getTaskBaseName() {
        return name.equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "" : GUtil.toCamelCase(name);
    }
}

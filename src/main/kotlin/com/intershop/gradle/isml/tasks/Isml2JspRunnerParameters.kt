/*
 * Copyright 2018 Intershop Communications AG.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intershop.gradle.isml.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters

/**
 * Parameter interface for Isml2Jsp Runner.
 */
interface Isml2JspRunnerParameters : WorkParameters {

    /**
     * Property outputDir for code generation.
     * @property outputDir
     */
    val outputDir: DirectoryProperty

    /**
     * Property outputDir for code generation.
     * @property inputDir
     */
    val inputDir: DirectoryProperty

    /**
     * Property for content encoding.
     * @property encoding
     */
    val encoding: Property<String>

    /**
     * Property with jsp encoding map.
     */
    val jspEncoding: MapProperty<String, String>
}

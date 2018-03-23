/*
 * Copyright 2018 the original author or authors.
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
 * limitations under the License.
 */
package org.gradle.kotlin.dsl

import org.gradle.api.Task

import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.tasks.TaskContainer


/**
 * Creates a [Task] with the given [name] and type, passing the given arguments to the [javax.inject.Inject]-annotated constructor,
 * and adds it to this project tasks container.
 */
inline
fun <reified T : Task> TaskContainer.create(name: String, vararg arguments: Any) =
    create(name, T::class.java, *arguments)


/**
 * Looks for the task of a given name, casts it to the expected type [T]
 * then applies the given [configure] action.
 *
 * If none found it will throw an [UnknownDomainObjectException].
 * If the task is found but cannot be cast to the expected type it will throw an [IllegalStateException].
 *
 * @param name task name
 * @return task, never null
 * @throws [UnknownDomainObjectException] When the given task is not found.
 * @throws [IllegalStateException] When the given task cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline
fun <reified T : Any> TaskContainer.getByName(name: String, configure: T.() -> Unit = {}): T =
    getByName(name).let {
        (it as? T)?.also(configure)
            ?: throw IllegalStateException(
                "Element '$name' of type '${it::class.java.name}' from container '$this' cannot be cast to '${T::class.qualifiedName}'.")
    }


/**
 * Looks for the task of a given name and, if it exists, casts it to the expected type [T].
 *
 * If none found it will return `null`
 * If the task is found but cannot be cast to the expected type it will throw an [IllegalStateException].
 *
 * @param name task name
 * @return task, or null if not found null
 * @throws [IllegalStateException] When the given task cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline
fun <reified T : Any> TaskContainer.findByName(name: String): T? =
    findByName(name)?.let {
        it as? T
            ?: throw IllegalStateException(
                "Element '$name' of type '${it::class.java.name}' from container '$this' cannot be cast to '${T::class.qualifiedName}'.")
    }

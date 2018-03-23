package org.gradle.kotlin.dsl

import org.gradle.api.DefaultTask
import org.gradle.api.java.archives.Manifest
import org.gradle.api.tasks.Delete
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.TaskContainer

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.CoreMatchers.startsWith

import org.junit.Assert.assertThat
import org.junit.Assert.fail
import org.junit.Test


class TaskContainerExtensionsTest {

    @Test
    fun `can create tasks with injected constructor arguments`() {

        val tasks = mock<TaskContainer>()

        tasks.create<DefaultTask>("my", "foo", "bar")

        verify(tasks).create("my", DefaultTask::class.java, "foo", "bar")
    }

    @Test
    fun `can find task by name and reified type`() {

        val tasks = mock<TaskContainer> {
            on { findByName("jar") } doReturn mock<Jar>()
        }

        tasks.findByName<Jar>("jar")

        inOrder(tasks) {
            verify(tasks).findByName("jar")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure task by name and reified type`() {

        val jar = mock<Jar> {
            on { manifest } doReturn mock<Manifest>()
        }
        val tasks = mock<TaskContainer> {
            on { getByName("jar") } doReturn jar
        }

        tasks.getByName<Jar>("jar") {
            manifest
        }

        inOrder(tasks, jar) {
            verify(tasks).getByName("jar")
            verify(jar).manifest
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `getting existing task by name and wrong reified type throws`() {

        val tasks = mock<TaskContainer>(name = "tasks") {
            on { getByName("jar") } doReturn mock<Jar>()
        }

        try {
            tasks.getByName<Delete>("jar")
            fail("Should have thrown")
        } catch (ex: IllegalStateException) {
            assertCannotBeCast(ex)
        }

        inOrder(tasks) {
            verify(tasks).getByName("jar")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `finding existing task by name and wrong reified type throws`() {

        val tasks = mock<TaskContainer>(name = "tasks") {
            on { findByName("jar") } doReturn mock<Jar>()
        }

        try {
            tasks.findByName<Delete>("jar")
            fail("Should have thrown")
        } catch (ex: IllegalStateException) {
            assertCannotBeCast(ex)
        }

        inOrder(tasks) {
            verify(tasks).findByName("jar")
            verifyNoMoreInteractions()
        }
    }

    private
    fun assertCannotBeCast(ex: IllegalStateException) =
        assertThat(
            ex.message,
            allOf(
                startsWith("Element 'jar' of type 'org.gradle.jvm.tasks.Jar"),
                endsWith(" from container 'tasks' cannot be cast to 'org.gradle.api.tasks.Delete'.")))
}

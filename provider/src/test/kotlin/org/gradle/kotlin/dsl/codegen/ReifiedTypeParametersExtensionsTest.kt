package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Project
import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.plugins.PluginContainer

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test


class ReifiedTypeParametersExtensionsTest : AbstractIntegrationTest() {

    @Test
    fun `plop2`() {

        val jar = withClassJar(
            "gradle-reified-type-parameters.jar",
            Project::class.java,
            PluginCollection::class.java,
            ObjectConfigurationAction::class.java,
            PluginContainer::class.java,
            Convention::class.java)

        val extensions = sequenceOf(jar).reifiedTypeParametersExtensionsDeclarations().toList()

        println(extensions.joinToString(separator = "\n\n", prefix = "\n", postfix = "\n"))

        assertThat(extensions[0], containsMultiLineString("""
        inline
        fun <reified T> org.gradle.api.Project.property(): org.gradle.api.provider.PropertyState<T> =
            property(T::class.java)
        """))

        assertThat(extensions[1], containsMultiLineString("""
        inline
        fun <reified T> org.gradle.api.Project.container(): org.gradle.api.NamedDomainObjectContainer<T> =
            container(T::class.java)
        """))

        assertThat(extensions.size, equalTo(2))

    }
}

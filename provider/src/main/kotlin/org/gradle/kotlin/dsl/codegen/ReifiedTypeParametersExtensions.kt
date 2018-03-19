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
package org.gradle.kotlin.dsl.codegen

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.jetbrains.org.objectweb.asm.Opcodes.ASM6
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import java.io.Closeable

import java.io.File
import java.util.jar.JarFile


internal
fun writeReifiedTypeParametersExtensionsTo(file: File, gradleJars: Iterable<File>) {
    file.bufferedWriter().use {
        it.apply {
            write(fileHeader)
            reifiedTypeParametersExtensionDeclarationsFor(gradleJars).forEach {
                write("\n")
                write(it)
                write("\n")
            }
        }
    }
}


internal
fun reifiedTypeParametersExtensionDeclarationsFor(jars: Iterable<File>): Sequence<String> =
    jars.asSequence().gradleJars().reifiedTypeParametersExtensionsDeclarations()


private
fun Sequence<File>.gradleJars(): Sequence<File> =
    filter { it.name.startsWith("gradle-") }


// TODO refactor as not an extension on Sequence in order not to pollute available api in :provider
internal
fun Sequence<File>.reifiedTypeParametersExtensionsDeclarations(): Sequence<String> =
    reifiedTypeParametersExtensionsFrom(this).map {
        it.run {
            """
            inline
            fun $typeParametersDeclaration $extendedTypeDeclaration.$functionName($parametersDeclaration): $returnType =
                $functionName($typeParameterName::class.java$parametersPassing)
            """.replaceIndent()
        }
    }


private
fun reifiedTypeParametersExtensionsFrom(jars: Sequence<File>): Sequence<ReifiedTypeParametersExtensionFunction> =
    jars.flatMap(::reifiedTypeParametersExtensionsFrom)


internal
fun reifiedTypeParametersExtensionsFrom(jar: File): Sequence<ReifiedTypeParametersExtensionFunction> =
    selectClassesFrom(jar).asSequence()
        .flatMap {
            selectFunctionsFrom(it).asSequence()
        }


private
fun selectClassesFrom(jar: File): List<ByteArray> =
    JarFile(jar).use { jarFile ->
        jarFile.entries().asSequence().filter {
            // TODO real filter, using implicit imports?
            it.name.endsWith(".class")
                && !it.name.contains("$")
                && !it.name.contains("/internal/")
                && it.name.startsWith("org/gradle/")
        }.map { classEntry ->
            jarFile.getInputStream(classEntry).use { it.readBytes() }
        }.toList()
    }


private
fun selectFunctionsFrom(classBytes: ByteArray): List<ReifiedTypeParametersExtensionFunction> =
    ReifiedTypeParametersExtensionsClassVisitor().use {
        ClassReader(classBytes).accept(it, SKIP_DEBUG and SKIP_FRAMES)
        it.reifiedTypeParametersExtensions
    }


internal
data class TypeParameter(val name: String, val bounds: String?)


internal
data class ReifiedTypeParametersExtensionFunction(
    val typeParameterName: String,
    private val extendedTypeTypeParameters: List<TypeParameter>,
    private val extendedType: String,
    val functionName: String,
    val returnType: String,
    val parameters: Map<String, String>
) {

    val typeParametersDeclaration =
        "<${
        if (extendedTypeTypeParameters.isNotEmpty()) {
            extendedTypeTypeParameters.joinToString(", ", postfix = ", ") {
                "${it.name}${
                if (it.bounds != null) {
                    " : ${it.bounds}"
                } else ""
                }"
            }
        } else ""
        }reified $typeParameterName>"

    val extendedTypeDeclaration =
        "$extendedType${
        if (extendedTypeTypeParameters.isNotEmpty()) extendedTypeTypeParameters.joinToString(", ", prefix = "<", postfix = ">") { it.name }
        else ""
        }"

    val parametersDeclaration
        get() = parameters.asSequence().joinToString(", ") { (name, type) -> "$name: $type" }

    val parametersPassing
        get() =
            if (parameters.isEmpty()) ""
            else ", ${parameters.keys.joinToString(", ")}"
}


val typeClassNameBlackList = emptyList<String>()


val methodNameBlackList = listOf("apply")


private
class ReifiedTypeParametersExtensionsClassVisitor : ClassVisitor(ASM6), Closeable {

    private
    val typeParametersSignatureVisitor = TypeParametersSignatureVisitor()

    private
    var skipType = true

    private
    var extendedTypeClassName: String? = null

    private
    val state = mutableListOf<ReifiedTypeParametersExtensionFunction>()

    val reifiedTypeParametersExtensions: List<ReifiedTypeParametersExtensionFunction>
        get() = state.toList()

    override fun close() {
        skipType = true
        extendedTypeClassName = null
        state.clear()
    }

    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
        val typeClassName = Type.getObjectType(name).className
        val superClassName = Type.getObjectType(superName).className
        if (typeClassName in typeClassNameBlackList
            || superClassName != "java.lang.Object"
            || signature == null
        ) {
            return
        }
        val typeTypeParameters = typeParametersSignatureVisitor.use {
            SignatureReader(signature).accept(it)
            typeParametersSignatureVisitor.typeParameters
        }
        if (typeTypeParameters.isNotEmpty()) {
            return
        }
        extendedTypeClassName = typeClassName
        skipType = false
    }

    override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        if (skipType
            || name in methodNameBlackList
            || !access.isAccessPublicMethod
            || signature == null
            || !desc.isDescMethodSingleClassParameter
        ) {
            return null
        }

        val methodTypeParameters = typeParametersSignatureVisitor.use {
            SignatureReader(signature).accept(it)
            typeParametersSignatureVisitor.typeParameters
        }

        methodTypeParameters.singleWithoutBoundsOrNull?.let { methodTypeParameter ->

            val methodReturnType = Type.getReturnType(desc).className.takeIf { it != "java.lang.Object" }?.let { "$it<${methodTypeParameter.name}>" }
                ?: methodTypeParameter.name

            state.add(ReifiedTypeParametersExtensionFunction(
                methodTypeParameter.name,
                emptyList(),
                extendedTypeClassName!!,
                name,
                methodReturnType,
                mapOf()))
        }

        return null
    }

    private
    val Int.isAccessPublicMethod
        get() = (ACC_PUBLIC and this) > 0

    private
    val String.isDescMethodSingleClassParameter
        get() = Type.getArgumentTypes(this).let {
            it.size == 1 && it.firstOrNull()?.className == "java.lang.Class"
        }

    private
    val List<TypeParameter>.singleWithoutBoundsOrNull: TypeParameter?
        get() = singleOrNull()?.takeIf { it.bounds == null }
}


private
class TypeParametersSignatureVisitor : SignatureVisitor(ASM6), Closeable {

    private
    val state = mutableMapOf<String, MutableList<String>>()

    private
    var current: String? = null

    val typeParameters: List<TypeParameter>
        get() = state.map { (name, bounds) -> TypeParameter(name, if (bounds.isEmpty()) null else bounds.joinToString(", ")) }

    override fun close() {
        state.clear()
        current = null
    }

    override fun visitFormalTypeParameter(name: String) {
        state[name] = mutableListOf()
        current = name
    }

    override fun visitClassBound(): SignatureVisitor {
        return object : SignatureVisitor(ASM6) {
        }
    }

    override fun visitInterfaceBound(): SignatureVisitor {
        return object : SignatureVisitor(ASM6) {
            override fun visitClassType(name: String) {
                // TODO what to do about sources that use raw types?
                // we can't know if the class type has generics without looking at it
                state[current]!!.add(Type.getObjectType(name).className)
            }
        }
    }
}

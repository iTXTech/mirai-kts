/*
 *
 * Mirai Kts
 *
 * Copyright (C) 2020 iTX Technologies
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author PeratX
 * @website https://github.com/iTXTech/mirai-kts
 *
 */

package org.itxtech.miraikts

import org.itxtech.miraikts.plugin.PluginManager
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmSdkRoots
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.GenericReplCompiler
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.asSuccess
import kotlin.script.templates.AcceptedAnnotations
import kotlin.script.templates.ScriptTemplateDefinition

// 后续版本号为 2531
const val HEADER = "MKCv531"

const val ENV_MANAGER = "manager"
const val ENV_FILENAME = "filename"

object KtsEngineFactory {
    fun getScriptEngine(manager: PluginManager, file: File, classLoader: ClassLoader): KtsEngine {
        return KtsEngine(
            manager, file,
            classLoader,
            classLoader.getClasspath(3) // core, console, MiraiKts
        )
    }
}

class KtsEngine(
    private val manager: PluginManager,
    private val file: File,
    private val loader: ClassLoader,
    private val templateClasspath: List<File>
) {
    private val replCompiler: ReplCompiler by lazy {
        GenericReplCompiler(
            makeScriptDefinition(),
            makeCompilerConfiguration(),
            PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false)
        )
    }

    private val localEvaluator by lazy {
        GenericReplCompilingEvaluator(
            replCompiler,
            templateClasspath,
            loader
        )
    }

    private val state = localEvaluator.createState(ReentrantReadWriteLock())

    fun eval(classes: ReplCompileResult.CompiledClasses): Any? {
        val result = try {
            localEvaluator.eval(state, classes, null, null)
        } catch (e: Exception) {
            MiraiKts.logger.error(e)
        }

        return when (result) {
            is ReplEvalResult.ValueResult -> result.value
            is ReplEvalResult.UnitResult -> null
            is ReplEvalResult.Error -> throw Exception("Error: $result")
            is ReplEvalResult.Incomplete -> throw Exception("Error: incomplete code. $result")
            is ReplEvalResult.HistoryMismatch -> throw Exception("Repl history mismatch at line: ${result.lineNo}")
            else -> null
        }
    }

    private fun nextCodeLine(code: String) = state.let { ReplCodeLine(it.getNextLineNo(), it.currentGeneration, code) }

    fun compile(script: String): ReplCompileResult.CompiledClasses {
        return when (val result = replCompiler.compile(state, nextCodeLine(script))) {
            is ReplCompileResult.Error -> throw Exception("Error: ${result.message}")
            is ReplCompileResult.Incomplete -> throw Exception("Error: incomplete code $result")
            is ReplCompileResult.CompiledClasses -> result
        }
    }

    private fun makeScriptDefinition(): KotlinScriptDefinition {
        return KotlinScriptDefinitionFromAnnotatedTemplate(
            MktsResolverAnno::class,
            mapOf(
                Pair(ENV_MANAGER, manager),
                Pair(ENV_FILENAME, file)
            )
        )
    }

    private fun makeCompilerConfiguration() = CompilerConfiguration().apply {
        addJvmSdkRoots(PathUtil.getJdkClassesRootsFromCurrentJre())
        addJvmClasspathRoots(templateClasspath)
        add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, ScriptingCompilerConfigurationComponentRegistrar())
        put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script")
        languageVersionSettings = LanguageVersionSettingsImpl(
            LanguageVersion.LATEST_STABLE,
            ApiVersion.LATEST_STABLE,
            mapOf(AnalysisFlags.skipMetadataVersionCheck to true)
        )
    }
}

fun ClassLoader.getClasspath(limit: Int, list: ArrayList<File> = ArrayList()): ArrayList<File> {
    if (limit <= 0) {
        return list
    }
    if (this is URLClassLoader) {
        urLs.forEach {
            list.add(File(it.file))
        }
    }
    if (parent != null) {
        return parent.getClasspath(limit - 1, list)
    }
    return list
}

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class Namespace(val ns: String)

@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class Jar(vararg val jars: String)

@ScriptTemplateDefinition(resolver = MktsResolver::class)
abstract class MktsResolverAnno

class MktsResolver : DependenciesResolver {
    @AcceptedAnnotations(Jar::class, Namespace::class)
    override fun resolve(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult {
        var base = "public"
        val jars = ArrayList<String>()
        scriptContents.annotations.forEach { annotation ->
            when (annotation) {
                is Namespace -> {
                    base = annotation.ns
                }
                is Jar -> {
                    annotation.jars.forEach {
                        jars.add(it)
                    }
                }
            }
        }
        val dir = (environment[ENV_MANAGER] as PluginManager).getLibDir(base)
        val path = ArrayList<File>()
        jars.forEach {
            val jar = File(dir.absolutePath + File.separatorChar + it)
            if (jar.exists()) {
                path.add(jar)
            } else {
                MiraiKts.logger.error("无法找到 \"${(environment[ENV_FILENAME] as File).name}\" 所需的 \"${jar.absolutePath}\"")
            }
        }
        return ScriptDependencies(
            classpath = path
        ).asSuccess()
    }
}

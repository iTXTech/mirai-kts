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

package org.itxtech.miraikts.script

import org.itxtech.miraikts.MiraiKts
import org.itxtech.miraikts.plugin.PluginManager
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmSdkRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerFromEnvironment
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.asSuccess
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.*
import kotlin.script.templates.AcceptedAnnotations
import kotlin.script.templates.ScriptTemplateDefinition

// 后续版本号为 2531
const val HEADER = "MKCv531"

const val ENV_MANAGER = "manager"
const val ENV_FILENAME = "filename"
const val ENV_BASE_PATH = "basepath"

class KtsEngine(
    private val manager: PluginManager,
    private val loader: ClassLoader,
    private val templateClasspath: List<File>,
    private val basePath: String
) {
    fun compile(file: File): CompiledScript<*> {
        val script = file.toScriptSource()
        val rootDisposable = Disposer.newDisposable()
        val config = CompilerConfiguration().apply {
            addJvmSdkRoots(PathUtil.getJdkClassesRootsFromCurrentJre())
            addJvmClasspathRoots(templateClasspath)
            add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, ScriptingCompilerConfigurationComponentRegistrar())
            put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script")
            languageVersionSettings = LanguageVersionSettingsImpl(
                LanguageVersion.LATEST_STABLE,
                ApiVersion.LATEST_STABLE,
                mapOf(AnalysisFlags.skipMetadataVersionCheck to true)
            )
            add(
                ScriptingConfigurationKeys.SCRIPT_DEFINITIONS,
                ScriptDefinition.FromLegacy(
                    ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                        configurationDependencies(JvmDependency(jvmClasspathRoots))
                    }, KotlinScriptDefinitionFromAnnotatedTemplate(
                        MktsResolverAnno::class,
                        mapOf(
                            Pair(ENV_MANAGER, manager),
                            Pair(ENV_FILENAME, file),
                            Pair(ENV_BASE_PATH, basePath)
                        )
                    )
                )
            )
            put(
                CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                PrintingMessageCollector(
                    PrintStream(OutputStream.nullOutputStream()),
                    MessageRenderer.WITHOUT_PATHS,
                    false
                )
            )
            put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, true)
            if (get(JVMConfigurationKeys.JVM_TARGET) == null) {
                put(JVMConfigurationKeys.JVM_TARGET,
                    System.getProperty("java.specification.version")?.let { JvmTarget.fromString(it) }
                        ?: JvmTarget.DEFAULT)
            }
        }
        val env =
            KotlinCoreEnvironment.createForProduction(rootDisposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val compiler = ScriptJvmCompilerFromEnvironment(env)
        val def = ScriptDefinitionProvider.getInstance(env.project)!!
            .findDefinition(script)!!.compilationConfiguration
        return compiler.compile(script, def).valueOrThrow()
    }

    suspend fun eval(compiled: CompiledScript<*>): EvaluationResult {
        val config = ScriptEvaluationConfiguration { jvm { baseClassLoader(loader) } }
        return BasicJvmScriptEvaluator().invoke(compiled, config).valueOrThrow()
    }
}

fun ClassLoader.getClassPath(limit: Int, list: ArrayList<File> = ArrayList()): ArrayList<File> {
    if (limit <= 0) {
        return list
    }
    if (this is URLClassLoader) {
        urLs.forEach {
            list.add(File(it.file))
        }
    }
    if (parent != null) {
        return parent.getClassPath(limit - 1, list)
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

/*
@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class Include(vararg val kt: String)
*/

@ScriptTemplateDefinition(resolver = MktsResolver::class)
abstract class MktsResolverAnno

class MktsResolver : DependenciesResolver {
    @AcceptedAnnotations(Jar::class, Namespace::class/*, Include::class*/)
    override fun resolve(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult {
        var base = "public"
        val jars = ArrayList<String>()
        //val inc = ArrayList<String>()
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
                /*is Include -> {
                    annotation.kt.forEach {
                        inc.add(it)
                    }
                }*/
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
            classpath = path,
            //sources = includes,
            imports = listOf(
                //"org.itxtech.miraikts.script.Include",
                "org.itxtech.miraikts.script.Jar",
                "org.itxtech.miraikts.script.Namespace"
            )
        ).asSuccess()
    }
}

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

import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmSdkRoots
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.script.jsr223.KotlinStandardJsr223ScriptTemplate
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.GenericReplCompiler
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.utils.PathUtil
import java.io.*
import java.math.BigInteger
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngineFactory
import javax.script.ScriptException
import kotlin.reflect.KClass

// 后续版本号为 2531
const val HEADER = "MKCv531"

object KtsEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {
    override fun getScriptEngine(): KtsEngine {
        val jars = arrayListOf<File>()
        (javaClass.classLoader.parent as URLClassLoader).urLs.forEach {
            jars.add(File(it.file))
        }
        (javaClass.classLoader as URLClassLoader).urLs.forEach {
            jars.add(File(it.file))
        }
        return KtsEngine(
            this, jars,
            KotlinStandardJsr223ScriptTemplate::class.qualifiedName!!,
            { ctx, types ->
                ScriptArgsWithTypes(
                    arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)),
                    types ?: emptyArray()
                )
            },
            arrayOf(Bindings::class)
        )
    }
}

class KtsEngine(
    factory: ScriptEngineFactory,
    private val templateClasspath: List<File>,
    templateClassName: String,
    val getScriptArgs: (ScriptContext, Array<out KClass<out Any>>?) -> ScriptArgsWithTypes?,
    private val scriptArgsTypes: Array<out KClass<out Any>>?
) : KotlinJsr223JvmScriptEngineBase(factory), KotlinJsr223JvmInvocableScriptEngine {

    override val replCompiler: ReplCompiler by lazy {
        GenericReplCompiler(
            makeScriptDefinition(templateClasspath, templateClassName),
            makeCompilerConfiguration(),
            PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false)
        )
    }

    private val localEvaluator by lazy {
        GenericReplCompilingEvaluator(
            replCompiler,
            templateClasspath,
            Thread.currentThread().contextClassLoader,
            getScriptArgs(getContext(), scriptArgsTypes)
        )
    }

    override val replEvaluator: ReplFullEvaluator get() = localEvaluator

    override val state: IReplStageState<*> get() = getCurrentState(getContext())

    fun eval(classes: ReplCompileResult.CompiledClasses): Any? {
        val state = getCurrentState(context)
        return asJsr223EvalResult {
            replEvaluator.eval(state, classes, overrideScriptArgs(context), getInvokeWrapper(context))
        }
    }

    private fun asJsr223EvalResult(body: () -> ReplEvalResult): Any? {
        val result = try {
            body()
        } catch (e: Exception) {
            throw ScriptException(e)
        }

        return when (result) {
            is ReplEvalResult.ValueResult -> result.value
            is ReplEvalResult.UnitResult -> null
            is ReplEvalResult.Error ->
                when {
                    result is ReplEvalResult.Error.Runtime && result.cause != null ->
                        throw ScriptException(result.cause)
                    result is ReplEvalResult.Error.CompileTime && result.location != null ->
                        throw ScriptException(
                            result.message,
                            result.location!!.path,
                            result.location!!.line,
                            result.location!!.column
                        )
                    else -> throw ScriptException(result.message)
                }
            is ReplEvalResult.Incomplete -> throw ScriptException("Error: incomplete code. $result")
            is ReplEvalResult.HistoryMismatch -> throw ScriptException("Repl history mismatch at line: ${result.lineNo}")
        }
    }

    override fun createState(lock: ReentrantReadWriteLock): IReplStageState<*> = replEvaluator.createState(lock)

    override fun overrideScriptArgs(context: ScriptContext): ScriptArgsWithTypes? =
        getScriptArgs(context, scriptArgsTypes)

    private fun makeScriptDefinition(templateClasspath: List<File>, templateClassName: String): KotlinScriptDefinition {
        val classloader =
            URLClassLoader(templateClasspath.map { it.toURI().toURL() }.toTypedArray(), this.javaClass.classLoader)
        val cls = classloader.loadClass(templateClassName)
        return KotlinScriptDefinitionFromAnnotatedTemplate(cls.kotlin, emptyMap())
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

data class MiraiKtsCache(
    val meta: MiraiKtsCacheMetadata,
    val classes: ReplCompileResult.CompiledClasses
)

data class MiraiKtsCacheMetadata(
    val source: Boolean,
    val header: String? = null,
    val origin: String? = null,
    val checksum: String,
    val file: File
) {
    fun verifyHeader(h: String = HEADER) = header == h
}

fun File.checksum(): String = BigInteger(
    1, MessageDigest.getInstance("MD5").digest(readBytes())
).toString(16).padStart(32, '0')

fun File.findCache(dir: File, checksum: String = checksum()): File {
    return File(dir.absolutePath + File.separatorChar + checksum + ".mkc")
}

fun File.readMkc(): MiraiKtsCache {
    val bi = FileInputStream(this)
    val si = ObjectInputStream(bi)
    return MiraiKtsCache(
        MiraiKtsCacheMetadata(
            false,
            si.readString(),
            si.readString(),
            si.readString(),
            this
        ),
        (si.readObject() as ReplCompileResult.CompiledClasses).apply {
            si.close()
            bi.close()
        }
    )
}

fun ReplCompileResult.CompiledClasses.save(
    target: File, origin: File,
    checksum: String, header: String = HEADER
): MiraiKtsCacheMetadata {
    val bo = FileOutputStream(target)
    val so = ObjectOutputStream(bo)
    so.writeString(header)
    so.writeString(origin.absolutePath)
    so.writeString(checksum)
    so.writeObject(this)
    so.close()
    bo.close()
    return MiraiKtsCacheMetadata(
        true,
        header,
        origin.absolutePath,
        checksum,
        target
    )
}

fun ObjectInputStream.readString(): String {
    return String(readNBytes(readShort().toInt()))
}

fun ObjectOutputStream.writeString(str: String) {
    writeShort(str.length)
    writeBytes(str)
}

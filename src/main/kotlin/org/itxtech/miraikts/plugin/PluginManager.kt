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

package org.itxtech.miraikts.plugin

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import org.itxtech.miraikts.MiraiKts
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngine
import org.jetbrains.kotlin.script.jsr223.KotlinStandardJsr223ScriptTemplate
import java.io.File
import java.net.URLClassLoader
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine

@OptIn(ObsoleteCoroutinesApi::class)
open class PluginManager {
    protected val plDir: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "plugins").also { it.mkdirs() } }
    protected val plData: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "data").also { it.mkdirs() } }
    protected val context = newSingleThreadContext("MiraiKts")

    init {
        setIdeaIoUseFallback()
        MiraiKts.launch(context) {
            Thread.currentThread().contextClassLoader = this@PluginManager.javaClass.classLoader
            this@PluginManager.javaClass.classLoader.loadClass("net.mamoe.mirai.console.plugins.JsonConfig")
        }
    }

    open fun getPluginDataDir(name: String): File {
        return File(plData.absolutePath + File.separatorChar + name).apply { mkdirs() }
    }

    protected val pluginId = atomic(0)
    protected val plugins = hashMapOf<Int, KtsPlugin>()

    open fun loadPlugins() {
        if (!MiraiKts.dataFolder.isDirectory) {
            MiraiKts.logger.error("数据文件夹不是一个文件夹！" + MiraiKts.dataFolder.absolutePath)
        } else {
            plDir.listFiles()?.forEach { file ->
                loadPlugin(file)
            }
        }
    }

    open fun loadPlugin(file: File): Boolean {
        if (file.exists() && file.isFile && file.absolutePath.endsWith(".kts")) {
            MiraiKts.logger.info("正在加载 Kts 插件：" + file.absolutePath)
            plugins.values.forEach {
                if (it.file == file) {
                    return false
                }
            }

            MiraiKts.launch(context) {
                val plugin = KtsEngine.scriptEngine.eval(file.readText()) as KtsPlugin
                plugin.manager = this@PluginManager
                plugin.id = pluginId.value
                plugin.file = file
                plugins[pluginId.getAndIncrement()] = plugin

                plugin.onLoad()
            }
            return true
        }
        return false
    }

    open fun enablePlugins() = MiraiKts.launch(context) {
        plugins.values.forEach {
            it.onEnable()
        }
    }

    open fun disablePlugins() = MiraiKts.launch(context) {
        plugins.values.forEach {
            it.onDisable()
        }
    }
}

object KtsEngine : KotlinJsr223JvmScriptEngineFactoryBase() {
    override fun getScriptEngine(): ScriptEngine {
        val jars = arrayListOf<File>()
        (javaClass.classLoader.parent as URLClassLoader).urLs.forEach {
            jars.add(File(it.file))
        }
        (javaClass.classLoader as URLClassLoader).urLs.forEach {
            jars.add(File(it.file))
        }
        return KotlinJsr223JvmLocalScriptEngine(
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

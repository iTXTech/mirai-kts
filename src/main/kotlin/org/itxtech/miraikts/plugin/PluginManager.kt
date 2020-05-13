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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import net.mamoe.mirai.utils.currentTimeMillis
import org.itxtech.miraikts.*
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineBase
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import java.io.File

@OptIn(ObsoleteCoroutinesApi::class)
open class PluginManager {
    protected val plDir: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "plugins").also { it.mkdirs() } }
    protected val plData: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "data").also { it.mkdirs() } }
    protected val cache: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "cache").also { it.mkdirs() } }
    protected val context = newFixedThreadPoolContext(Runtime.getRuntime().availableProcessors(), "MiraiKts")

    init {
        setIdeaIoUseFallback()
    }

    private fun launch(b: suspend CoroutineScope.() -> Unit) {
        MiraiKts.launch(context) {
            if (Thread.currentThread().contextClassLoader != this@PluginManager.javaClass.classLoader) {
                Thread.currentThread().contextClassLoader = this@PluginManager.javaClass.classLoader
            }
            b.invoke(this)
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
                loadPlugin(file, true)
            }
        }
    }

    open fun loadPlugin(ktsFile: File, enable: Boolean = false): Boolean {
        if (ktsFile.exists() && ktsFile.isFile && ktsFile.absolutePath.endsWith(".kts")) {
            MiraiKts.logger.info("正在加载 Kts 插件：" + ktsFile.absolutePath)
            plugins.values.forEach {
                if (it.file == ktsFile) {
                    return false
                }
            }

            launch {
                val engine = KtsEngineFactory.scriptEngine
                val cacheFile = ktsFile.findCache(cache)

                val compile = !cacheFile.exists()
                val start = currentTimeMillis

                val compiled: ReplCompileResult.CompiledClasses = if (compile) {
                    (engine.compile(ktsFile.readText()) as KotlinJsr223JvmScriptEngineBase.CompiledKotlinScript)
                        .compiledData.apply {
                        save(cacheFile)
                    }
                } else {
                    cacheFile.readAsCache()
                }

                val plugin = engine.eval(compiled) as KtsPlugin

                MiraiKts.logger.debug(
                    (if (compile) "编译 " else "缓存 ") +
                            "插件 \"${ktsFile.name}\" 加载耗时 " + (currentTimeMillis - start) + "ms"
                )

                plugin.manager = this@PluginManager
                plugin.id = pluginId.value
                plugin.file = ktsFile
                plugins[pluginId.getAndIncrement()] = plugin

                plugin.onLoad()
                if (enable) {
                    plugin.onEnable()
                }
            }
            return true
        }
        return false
    }

    open fun enablePlugins() = launch {
        plugins.values.forEach {
            it.onEnable()
        }
    }

    open fun disablePlugins() = launch {
        plugins.values.forEach {
            it.onDisable()
        }
    }
}

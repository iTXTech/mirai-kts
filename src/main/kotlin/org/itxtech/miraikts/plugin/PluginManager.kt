package org.itxtech.miraikts.plugin

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import org.itxtech.miraikts.MiraiKts
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import java.io.File

@OptIn(ObsoleteCoroutinesApi::class)
open class PluginManager {
    protected val plDir: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "plugins").also { it.mkdirs() } }
    protected val plData: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "data").also { it.mkdirs() } }
    protected val context = newSingleThreadContext("MiraiKts")

    init {
        setIdeaIoUseFallback()
        MiraiKts.launch(context) {
            Thread.currentThread().contextClassLoader = this@PluginManager.javaClass.classLoader
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
                val plugin = KotlinJsr223JvmLocalScriptEngineFactory().scriptEngine.eval(file.readText()) as KtsPlugin
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

    open fun enablePlugins() {
        plugins.values.forEach {
            it.onEnable()
        }
    }

    open fun disablePlugins() {
        plugins.values.forEach {
            it.onDisable()
        }
    }
}

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
import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.registerCommand
import net.mamoe.mirai.utils.currentTimeMillis
import org.itxtech.miraikts.MiraiKts
import org.itxtech.miraikts.script.*
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.reflect.full.isSubclassOf
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultValue

@OptIn(ObsoleteCoroutinesApi::class)
open class PluginManager {
    protected val plDir: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "plugins").also { it.mkdirs() } }
    protected val plData: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "data").also { it.mkdirs() } }
    protected val plLib: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "lib").also { it.mkdirs() } }
    protected val cache: File by lazy { File(MiraiKts.dataFolder.absolutePath + File.separatorChar + "cache").also { it.mkdirs() } }

    protected val context = newSingleThreadContext("MiraiKts")
    protected val pluginId = atomic(0)
    protected val plugins = hashMapOf<Int, KtsPlugin>()

    init {
        setIdeaIoUseFallback()
    }

    private fun launch(b: suspend CoroutineScope.() -> Unit): Job = MiraiKts.launch(context, block = b)

    open fun getPluginDataDir(name: String) =
        File(plData.absolutePath + File.separatorChar + name).apply { mkdirs() }

    open fun getLibDir(name: String) =
        File(plLib.absolutePath + File.separatorChar + name).apply { mkdirs() }

    open fun getClassLoader(parent: ClassLoader) = URLClassLoader(ArrayList<URL>().toTypedArray(), parent)

    open fun loadPlugins() {
        if (!MiraiKts.dataFolder.isDirectory) {
            MiraiKts.logger.error("数据文件夹不是一个文件夹！" + MiraiKts.dataFolder.absolutePath)
        } else {
            plDir.listFiles()?.forEach { file ->
                loadPlugin(file)
            }
        }
    }

    open fun loadPlugin(ktsFile: File): Boolean {
        if (ktsFile.exists() && ktsFile.isFile &&
            (ktsFile.name.endsWith(".kts") || ktsFile.name.endsWith(".mkc"))
        ) {
            MiraiKts.logger.info("正在加载 MiraiKts 插件：" + ktsFile.name)
            plugins.values.forEach {
                if (it.file == ktsFile) {
                    return false
                }
            }

            val id = pluginId.getAndIncrement()
            launch {
                val checksum = ktsFile.checksum()
                val cacheFile = if (ktsFile.name.endsWith(".mkc")) {
                    ktsFile
                } else {
                    plugins.values.forEach {
                        if (it.cacheMeta.checksum == checksum) {
                            MiraiKts.logger.error("插件 \"${ktsFile.name}\" 与已加载的插件 \"${it.file.name}\" 冲突：相同的插件")
                            return@launch
                        }
                    }
                    ktsFile.findCache(cache, checksum)
                }

                val compile = !cacheFile.exists()
                val start = currentTimeMillis

                val classpath: ArrayList<File>
                val engine = KtsEngine(
                    this@PluginManager,
                    getClassLoader(this@PluginManager.javaClass.classLoader).apply { classpath = getClassPath(3) },
                    classpath,
                    ktsFile.parentFile.absolutePath
                )

                val metadata: MiraiKtsCacheMetadata
                val compiled: CompiledScript<*> = if (compile) {
                    try {
                        engine.compile(ktsFile).apply { metadata = save(cacheFile, ktsFile, checksum) }
                    } catch (e: Exception) {
                        MiraiKts.logger.error("非法的 MiraiKts 插件文件 \"${ktsFile.name}\"", e)
                        return@launch
                    }
                } else {
                    cacheFile.readMkc().apply {
                        if (!ktsFile.name.endsWith(".mkc") && meta.checksum != checksum) {
                            MiraiKts.logger.error("非法的 MiraiKts 缓存文件 \"${cacheFile.name}\"，请删除该文件")
                            return@launch
                        }
                        metadata = meta
                    }.classes
                }

                if (!metadata.verifyHeader()) {
                    MiraiKts.logger.error("非法的 MiraiKts 缓存文件头 \"${metadata.header}\" 位于 \"${cacheFile.name}\"，请删除该文件")
                    return@launch
                }

                var pl: KtsPlugin? = null
                try {
                    when (val value = engine.eval(compiled).returnValue) {
                        is ResultValue.Value -> pl = value.value as KtsPlugin
                        else -> {
                            value.scriptInstance!!::class.nestedClasses
                                .filter { it.isSubclassOf(KtsPlugin::class) }
                                .forEach {
                                    if (it.objectInstance != null) {
                                        pl = it.objectInstance as KtsPlugin
                                    }
                                }
                        }
                    }
                    if (pl == null) {
                        throw Exception("KtsPlugin Instance not found.")
                    }
                } catch (e: Exception) {
                    MiraiKts.logger.error("错误的 MiraiKts 插件文件 \"${ktsFile.name}", e)
                    return@launch
                }

                MiraiKts.logger.debug(
                    (if (compile) "编译 " else "缓存 ") +
                            "插件 \"${ktsFile.name}\" 加载耗时 " + (currentTimeMillis - start) + "ms"
                )

                val plugin = pl!!
                plugin.cacheMeta = metadata
                plugins.values.forEach {
                    if (it.info.name == plugin.info.name || it.cacheMeta.checksum == plugin.cacheMeta.checksum) {
                        MiraiKts.logger.error("插件 \"${ktsFile.name}\" 与已加载的插件 \"${it.file.name}\" 冲突：相同的插件")
                        return@launch
                    }
                }

                plugin.manager = this@PluginManager
                plugin.id = id
                plugin.file = ktsFile
                plugins[id] = plugin

                plugin.load()
            }
            return true
        }
        return false
    }

    open fun enablePlugins() = launch {
        plugins.values.forEach {
            it.enable()
        }
    }

    open fun disablePlugins() = launch {
        plugins.values.forEach {
            it.disable()
        }
    }

    private fun getCommonPluginInfo(p: KtsPlugin, sender: CommandSender) {
        sender.appendMessage(
            "Id：" + p.id + " 文件：" + p.file.name + " 名称：" + p.info.name + " 状态：" +
                    (if (p.enabled) "启用" else "停用") + " 版本：" + p.info.version +
                    " 作者：" + p.info.author

        )
        if (p.info.website != "") {
            sender.appendMessage("主页：" + p.info.website)
        }
    }

    open fun registerCommand() {
        MiraiKts.registerCommand {
            name = "kpm"
            description = "Mirai Kts 插件管理器"
            usage = "kpm [list|info|enable|disable|load] (插件名/文件名)"
            onCommand { cmd ->
                if ((cmd.isEmpty() || (cmd[0] != "list" && cmd.size < 2))) {
                    return@onCommand false
                }
                when (cmd[0]) {
                    "list" -> {
                        var size = 0L
                        cache.listFiles()?.forEach { f ->
                            size += f.length()
                        }

                        appendMessage("")
                        appendMessage("MiraiKts 已生成 ${cache.listFiles()?.size} 个缓存文件，共 ${(size / 1024).toInt()} KB。")
                        appendMessage("共加载了 " + plugins.size + " 个 MiraiKts 插件。")
                        appendMessage("")
                        plugins.values.forEach { p ->
                            getCommonPluginInfo(p, this)
                            appendMessage("")
                        }
                    }
                    "info" -> {
                        if (plugins.containsKey(cmd[1].toInt())) {
                            val p = plugins[cmd[1].toInt()]!!
                            appendMessage("插件信息：")
                            getCommonPluginInfo(p, this)
                            appendMessage("")
                            appendMessage("使用缓存启动：" + (if (!p.cacheMeta.source) "是" else "否"))
                            appendMessage("缓存文件头：" + p.cacheMeta.header)
                            appendMessage("缓存源文件MD5：" + p.cacheMeta.checksum)
                            appendMessage("缓存源文件名：" + p.cacheMeta.origin)
                            appendMessage("缓存文件：" + p.cacheMeta.file.name)
                            appendMessage("")
                        } else {
                            appendMessage("Id " + cmd[1] + " 不存在。")
                        }
                    }
                    "load" -> {
                        if (!loadPlugin(File(plDir.absolutePath + File.separatorChar + cmd[1]))) {
                            appendMessage("文件 \"${cmd[1]}\" 非法。")
                        }
                    }
                    "enable" -> {
                        if (plugins.containsKey(cmd[1].toInt())) {
                            plugins[cmd[1].toInt()]!!.enable()
                        } else {
                            appendMessage("Id " + cmd[1] + " 不存在。")
                        }
                    }
                    "disable" -> {
                        if (plugins.containsKey(cmd[1].toInt())) {
                            plugins[cmd[1].toInt()]!!.disable()
                        } else {
                            appendMessage("Id " + cmd[1] + " 不存在。")
                        }
                    }
                    else -> return@onCommand false
                }
                return@onCommand true
            }
        }
    }
}

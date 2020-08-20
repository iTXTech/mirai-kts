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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.SimpleLogger
import org.itxtech.miraikts.MiraiKts
import org.itxtech.miraikts.script.MiraiKtsCacheMetadata
import java.io.File
import kotlin.coroutines.CoroutineContext

inline fun miraiPlugin(
    block: KtsPluginBuilder.() -> Unit
) = KtsPluginBuilder().apply(block).build()

inline fun pluginInfo(block: KtsPluginInfo.() -> Unit) = KtsPluginInfo().apply(block)

class KtsPluginBuilder {
    lateinit var info: KtsPluginInfo

    var load: (KtsPlugin.() -> Unit)? = null
    var enable: (KtsPlugin.() -> Unit)? = null
    var disable: (KtsPlugin.() -> Unit)? = null
    var unload: (KtsPlugin.() -> Unit)? = null

    fun info(block: KtsPluginInfo.() -> Unit) {
        info = KtsPluginInfo().apply(block)
    }

    fun load(block: KtsPlugin.() -> Unit) {
        load = block
    }

    fun enable(block: KtsPlugin.() -> Unit) {
        enable = block
    }

    fun disable(block: KtsPlugin.() -> Unit) {
        disable = block
    }

    fun unload(block: KtsPlugin.() -> Unit) {
        unload = block
    }

    fun build() = KtsPlugin(info, load, enable, disable, unload)
}

open class KtsPlugin(
    val info: KtsPluginInfo,
    protected var load: (KtsPlugin.() -> Unit)? = null,
    protected var enable: (KtsPlugin.() -> Unit)? = null,
    protected var disable: (KtsPlugin.() -> Unit)? = null,
    protected var unload: (KtsPlugin.() -> Unit)? = null
) : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = MiraiKts.coroutineContext + job

    lateinit var manager: PluginManager
    lateinit var file: File
    lateinit var cacheMeta: MiraiKtsCacheMetadata
    var id: Int = 0
    var enabled = false
    val dataDir: File by lazy { manager.getPluginDataDir(info.name) }

    val logger: MiraiLogger by lazy {
        SimpleLogger("KtsPlugin ${info.name}") { priority, message, e ->
            MiraiKts.logger.call(priority, "[${info.name}] $message", e)
        }
    }

    /**
     * 必须保证只被调用一次
     */
    fun load() = onLoad()

    fun enable() {
        if (!enabled) {
            enabled = true
            onEnable()
        }
    }

    fun disable() {
        if (enabled) {
            enabled = false
            onDisable()
            job.cancelChildren()
        }
    }

    /**
     * 调用后要清除所有引用，尽管不会发生类卸载
     */
    fun unload() {
        disable()
        onUnload()
        job.cancel()
    }

    open fun onLoad() = load?.invoke(this)

    open fun onEnable() = enable?.invoke(this)

    open fun onDisable() = disable?.invoke(this)

    open fun onUnload() = unload?.invoke(this)
}

class KtsPluginInfo {
    lateinit var name: String
    var version: String = "未知"
    var author: String = "未知"
    var website: String = ""
}

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
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.Command
import net.mamoe.mirai.console.command.CommandBuilder
import net.mamoe.mirai.console.command.registerCommand
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.SimpleLogger
import org.itxtech.miraikts.MiraiKts
import java.io.File
import kotlin.coroutines.CoroutineContext

inline fun miraiPlugin(
    block: KtsPluginBuilder.() -> Unit
) = KtsPluginBuilder().apply(block).build()

class KtsPluginBuilder {
    lateinit var info: KtsPluginInfo

    var load: (KtsPlugin.() -> Unit)? = null
    var enable: (KtsPlugin.() -> Unit)? = null
    var disable: (KtsPlugin.() -> Unit)? = null

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

    fun build(): KtsPlugin {
        return KtsPlugin(MiraiKts.coroutineContext, info, load, enable, disable)
    }
}

class KtsPlugin(
    coroutineContext: CoroutineContext,
    private val info: KtsPluginInfo,
    var load: (KtsPlugin.() -> Unit)? = null,
    var enable: (KtsPlugin.() -> Unit)? = null,
    var disable: (KtsPlugin.() -> Unit)? = null
) : CoroutineScope {
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = coroutineContext + supervisorJob

    lateinit var manager: PluginManager
    lateinit var file: File
    var id: Int = 0
    var enabled = false
    val dataDir: File by lazy { manager.getPluginDataDir(info.name) }

    val logger: MiraiLogger by lazy {
        SimpleLogger("KtsPlugin ${info.name}") { priority, message, e ->
            val identityString = "[MiraiKts] [${info.name}]"
            MiraiConsole.frontEnd.pushLog(priority, identityString, 0, message!!)
            if (e != null) {
                MiraiConsole.frontEnd.pushLog(priority, identityString, 0, e.toString())
            }
        }
    }

    fun registerCommand(builder: CommandBuilder.() -> Unit): Command {
        return MiraiKts.registerCommand(builder)
    }

    fun onLoad() {
        load?.invoke(this)
    }

    fun onEnable() {
        if (!enabled) {
            enabled = true
            enable?.invoke(this)
        }
    }

    fun onDisable() {
        if (enabled) {
            enabled = false
            disable?.invoke(this)
        }
    }
}

class KtsPluginInfo {
    lateinit var name: String
    var version: String? = null
}

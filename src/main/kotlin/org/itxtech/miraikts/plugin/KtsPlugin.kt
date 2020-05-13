package org.itxtech.miraikts.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.Command
import net.mamoe.mirai.console.command.CommandBuilder
import net.mamoe.mirai.console.command.registerCommand
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.SimpleLogger
import org.itxtech.miraikts.MiraiKts
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.ContinuationInterceptor
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

class PluginDispatcher : ContinuationInterceptor by Executors.newFixedThreadPool(1).asCoroutineDispatcher()

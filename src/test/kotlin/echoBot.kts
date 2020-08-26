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

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandOwner
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import org.itxtech.miraikts.plugin.KtsPlugin
import org.itxtech.miraikts.plugin.miraiPlugin

// 扩展函数写在 miraiPlugin 前面
fun KtsPlugin.doSomething() {
    logger.info("数据文件夹 $dataDir")
}

object Owner : CommandOwner

object KtsCommand : SimpleCommand(
    Owner, "kts",
    description = "Kts太强了"
) {
    @Handler
    suspend fun CommandSender.handle() {
        sendMessage("Kts NB!!!")
    }
}

miraiPlugin {
    info {
        name = "KtsPluginExample"
        version = "1.0.0"
        author = "PeratX"
        website = "https://github.com/iTXTech/mirai-kts/blob/master/src/test/kotlin/echoBot.kts"
    }

    load {
        logger.info("Hello world from MiraiKts!")
        launch {
            val client = HttpClient(CIO) {
                BrowserUserAgent()
            }
            val r = client.get<String>("https://im.qq.com")
            logger.info("QQ主页长：${r.length}")
        }
        KtsCommand.register()
    }

    enable {
        logger.info("KtsPlugin 已启用！")
        doSomething()
        subscribeAlways<BotOnlineEvent> {
            logger.info("Bot已上线！$this")
        }
        subscribeAlways<GroupMessageEvent> {
            group.sendMessage(PlainText("MiraiKts收到消息：") + message)
        }
    }

    disable {
        logger.info("KtsPlugin 已停用！")
    }

    unload {
        KtsCommand.unregister()
        logger.info("KtsPlugin 已卸载！")
    }
}

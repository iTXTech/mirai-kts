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

import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.event.subscribeAlways
import org.itxtech.miraikts.plugin.miraiPlugin

miraiPlugin {
    info {
        name = "KtsPluginExample"
        version = "1.0.0"
    }

    load {
        logger.info("Hello world from MiraiKts!")
        registerCommand {
            name = "kts"
            description = "Kts太强了"
            usage = "Kotlin Script"
            onCommand {
                logger.info("Kts NB!!!")
                return@onCommand true
            }
        }
    }

    enable {
        logger.info("KtsPlugin 已启用！")
        subscribeAlways<BotOnlineEvent> {
            logger.info("Bot已上线！$this")
        }
    }
}

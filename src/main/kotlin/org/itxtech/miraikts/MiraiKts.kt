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

import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import org.itxtech.miraikts.plugin.PluginManager
import java.io.File

object MiraiKts : KotlinPlugin() {
    val dataFolder: File by lazy { dataFolderPath.toFile() }

    private val manager = PluginManager()

    override fun onLoad() {
        manager.loadPlugins()
        manager.registerCommand()
    }

    override fun onEnable() {
        manager.enablePlugins()
    }

    override fun onDisable() {
        manager.disablePlugins()
    }
}

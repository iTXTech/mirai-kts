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

import org.itxtech.miraikts.plugin.KtsPlugin
import org.itxtech.miraikts.plugin.pluginInfo

// 一个 kts 插件中，只能存在一个 KtsPlugin object，且必须为 object
object NoBuilderPlugin : KtsPlugin(
    pluginInfo {
        name = "KtsPluginNoBuilderExample"
        version = "1.0.0"
        author = "PeratX"
        website = "https://github.com/iTXTech/mirai-kts/blob/master/src/test/kotlin/nobuilder.kts"
    }
) {
    override fun onLoad() {
        TestClass.doSomething(this)
    }

    override fun onEnable() {
        logger.info("KtsNoBuilder 已启用！")
    }

    override fun onDisable() {
        logger.info("KtsNoBuilder 已停用！")
    }

    override fun onUnload() {
        logger.info("KtsNoBuilder 已卸载！")
    }
}

object TestClass {
    fun doSomething(plugin: KtsPlugin) {
        plugin.logger.info("数据文件夹 ${plugin.dataDir}")
    }
}

# Mirai Kts

**强大的 `Mirai Kotlin Script` 插件加载器**

使用`Kotlin Script`编写 [Mirai](https://github.com/mamoe/mirai) 插件，支持与所有`Mirai API`直接交互，现仅支持`OpenJDK 8+`环境。

## 使用须知

**所有基于`Mirai Kts`的插件必须遵循`AGPL-v3`协议开放源代码，详见 [协议文本](LICENSE) 。**

**API可能随时变动，请保持更新！**

## 特性

* 极简语法，完整Kotlin语法支持
* 高效率执行（缺点是冷启动较慢）
* 字节码缓存机制，减少编译次数
* 施主别着急，更多功能即将加入。先看看 [例子](src/test/kotlin) 吧。

## Mirai Kts 插件管理器 `kpm`

1. 在 `mirai console` 中键入 `kpm` 获得帮助
1. `kpm` (`Kotlin Script Plugin Manager`) 可`列出/启用/停用/加载插件` 和 `查看插件信息`

`kpm [list|info|enable|disable|load] (插件名/文件名)`


## 开源协议

    Copyright (C) 2020 iTX Technologies

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

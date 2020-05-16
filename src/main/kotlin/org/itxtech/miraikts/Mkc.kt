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

import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest

data class MiraiKtsCache(
    val meta: MiraiKtsCacheMetadata,
    val classes: ReplCompileResult.CompiledClasses
)

data class MiraiKtsCacheMetadata(
    val source: Boolean,
    val header: String? = null,
    val origin: String? = null,
    val checksum: String,
    val file: File
) {
    fun verifyHeader(h: String = HEADER) = header == h
}

fun File.checksum(): String = BigInteger(
    1, MessageDigest.getInstance("MD5").digest(readBytes())
).toString(16).padStart(32, '0')

fun File.findCache(dir: File, checksum: String = checksum()): File {
    return File(dir.absolutePath + File.separatorChar + checksum + ".mkc")
}

fun File.readMkc(): MiraiKtsCache {
    val bi = FileInputStream(this)
    val si = ObjectInputStream(bi)
    return MiraiKtsCache(
        MiraiKtsCacheMetadata(
            false,
            si.readString(),
            si.readString(),
            si.readString(),
            this
        ),
        (si.readObject() as ReplCompileResult.CompiledClasses).apply {
            si.close()
            bi.close()
        }
    )
}

fun ReplCompileResult.CompiledClasses.save(
    target: File,
    origin: File,
    checksum: String,
    header: String = HEADER
): MiraiKtsCacheMetadata {
    val bo = FileOutputStream(target)
    val so = ObjectOutputStream(bo)
    so.writeString(header)
    so.writeString(origin.absolutePath)
    so.writeString(checksum)
    so.writeObject(this)
    so.close()
    bo.close()
    return MiraiKtsCacheMetadata(
        true,
        header,
        origin.absolutePath,
        checksum,
        target
    )
}

fun ObjectInputStream.readString(): String {
    return String(readNBytes(readShort().toInt()))
}

fun ObjectOutputStream.writeString(str: String) {
    writeShort(str.length)
    writeBytes(str)
}

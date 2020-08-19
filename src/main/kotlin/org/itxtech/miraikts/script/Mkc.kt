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

package org.itxtech.miraikts.script

import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.script.experimental.api.CompiledScript

data class MiraiKtsCache(
    val meta: MiraiKtsCacheMetadata,
    val classes: CompiledScript
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

fun ByteArray.checksum(): String = BigInteger(
    1, MessageDigest.getInstance("MD5").digest(this)
).toString(16).padStart(32, '0')

fun File.checksum(): String = readBytes().checksum()

fun File.findCache(dir: File, checksum: String = checksum()) =
    File(dir.absolutePath + File.separatorChar + checksum + ".mkc")

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
        (si.readObject() as CompiledScript).apply {
            si.close()
            bi.close()
        }
    )
}

fun CompiledScript.save(
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
    val len = readShort().toInt()
    val array = ByteArray(len)
    var read = 0
    while (read < len) {
        val i = this.read(array, read, len - read)
        if (i == -1) error("Unexpected EOF")
        read += i
    }
    return String(array)
}

fun ObjectOutputStream.writeString(str: String) {
    writeShort(str.length)
    writeBytes(str)
}

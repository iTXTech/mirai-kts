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

/*mkts
{"namespace":"okhttpexample","deps":["okhttp.jar","okio.jar"]}
mkts*/

import okhttp3.OkHttpClient
import okhttp3.Request
import org.itxtech.miraikts.plugin.miraiPlugin
import java.util.concurrent.TimeUnit

miraiPlugin {
    info {
        name = "KtsOkhttpExample"
        version = "1.0.0"
        author = "PeratX"
        website = "https://github.com/iTXTech/mirai-kts/blob/master/src/test/kotlin/okhttp.kts"
    }

    load {
        logger.info("Hello world from MiraiKts!")
        val client = OkHttpClient.Builder()
            .connectTimeout(1000, TimeUnit.MILLISECONDS)
            .readTimeout(1000, TimeUnit.MILLISECONDS)
            .addInterceptor {
                return@addInterceptor it.proceed(
                    it.request().newBuilder()
                        .addHeader(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36"
                        )
                        .build()
                )
            }
            .build()
        logger.info(client.newCall(Request.Builder().url("https://im.qq.com").build()).execute())
    }

    enable {
        logger.info("KtsOkhttp 已启用！")
    }

    disable {
        logger.info("KtsOkhttp 已停用！")
    }
}

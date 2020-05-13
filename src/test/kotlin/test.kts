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

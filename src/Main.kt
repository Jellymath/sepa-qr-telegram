import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.send.media.sendPhoto
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitText
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.requests.abstracts.asMultipartFile
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.utils.row
import kotlinx.coroutines.flow.first
import qrcode.QRCode

data class Account(val alias: String, val iban: String, val name: String)

val cache = mutableMapOf<UserId, MutableSet<Account>>()

suspend fun main() {

    val bot = telegramBot(System.getenv("TELEGRAM_BOT_TOKEN"))

    bot.buildBehaviourWithLongPolling {
        println(getMe())

        onCommand("add") {
            println("add command for ${it.from} started")

            sendTextMessage(it.chat, "Please enter alias for the account")
            val alias = waitText().first().text
            sendTextMessage(it.chat, "Please enter IBAN for the account")
            val iban = waitText().first().text
            sendTextMessage(it.chat, "Please enter name for the account")
            val name = waitText().first().text

            cache.getOrPut(it.from!!.id) { mutableSetOf() } += Account(alias, iban, name)
            sendTextMessage(it.chat, "Account $alias added")

            println("Add command for ${it.from} finished")
        }

        onCommand("remove") {
            println("remove command for ${it.from} started")
            val accounts = cache[it.from!!.id] ?: emptySet()

            if (accounts.isEmpty()) {
                reply(it, "No accounts to remove")
                return@onCommand
            }

            sendMessage(it.chat, "Select alias to remove", replyMarkup = inlineKeyboard {
                row {
                    accounts.forEach { account ->
                        dataButton(account.alias, account.alias)
                    }
                }
            })
            val alias = waitDataCallbackQuery().first().data
            cache.getValue(it.from!!.id).removeIf { it.alias == alias }
            reply(it, "Alias $alias removed")
            println("remove command for ${it.from} finished")
        }

        onCommand("generate") {
            println("generate command for ${it.from} started")
            val accounts = cache[it.from!!.id] ?: emptySet()

            sendMessage(it.chat, "Select alias", replyMarkup = inlineKeyboard {
                row {
                    accounts.forEach { account ->
                        dataButton(account.alias, account.alias)
                    }
                    dataButton("No alias", "<Proceed without alias>")
                }
            })
            val alias = waitDataCallbackQuery().first().data
            val iban: String
            val name: String
            if (alias == "<Proceed without alias>") {
                sendTextMessage(it.chat, "Please enter IBAN for the account")
                iban = waitText().first().text
//                oneOf(async { waitText().first().text }, async { waitDataCallbackQuery().first().data })
                sendTextMessage(it.chat, "Please enter name for the account")
                name = waitText().first().text
            } else {
                val account = accounts.first { it.alias == alias }
                iban = account.iban
                name = account.name
            }

            sendTextMessage(it.chat, "Please enter amount")
            val amount = waitText().first().text.toDouble()
            sendTextMessage(it.chat, "Please enter description")
            val description = waitText().first().text

            val qrCode = sepaQrCode(name, iban, amount, description)
            val text = """
                Send EUR$amount
                IBAN: $iban
                Full name: $name
                Description: $description
            """.trimIndent()
            sendPhoto(it.chat, qrCode.asMultipartFile("qr.png"), text)

            reply(it, "QR code generated, feel free to forward it")
            println("generate command for ${it.from} finished")
        }

        onCommand("start") {
            reply(it, "Hi:)")
        }
    }.join()
}

fun sepaQrText(name: String, iban: String, amount: Double, description: String) = """
BCD
002
2
SCT

$name
$iban
EUR$amount


$description
""".trimIndent()

fun sepaQrCode(name: String, iban: String, amount: Double, description: String) =
    QRCode.ofSquares().build(sepaQrText(name, iban, amount, description)).renderToBytes()
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.bot.setMyDescription
import dev.inmo.tgbotapi.extensions.api.bot.setMyShortDescription
import dev.inmo.tgbotapi.extensions.api.send.media.sendPhoto
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitText
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.extensions.onCommandPrivacy
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.requests.abstracts.asMultipartFile
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.types.chat.PreviewChat
import dev.inmo.tgbotapi.utils.RiskFeature
import dev.inmo.tgbotapi.utils.row
import kotlinx.coroutines.flow.first
import qrcode.QRCode

data class Account(val alias: String, val iban: String, val name: String)

val cache = mutableMapOf<UserId, MutableSet<Account>>()

val startText = """
Hi! I'm a SEPA QR code generator bot.
To generate a QR code, use the /generate command.
You can simplify QR code generation by adding accounts with /add command.
You can remove accounts with /remove or /remove_all commands.
To learn what data this bot stores, use the /privacy command.
                
Not sure what SEPA QR code is? Check out https://en.wikipedia.org/wiki/EPC_QR_code
""".trimIndent()

val privacyText = """
This bot only stores account information you are explicitly provided with /add command.
The purpose of this information is only to simplify SEPA QR code generation.
You can remove accounts with /remove or /remove_all commands.
The bot does not store any information provided for /generate command.
""".trimIndent()

const val description = "Bot that can generate SEPA QR code (formally EPC QR Code) for you"

@OptIn(RiskFeature::class)
suspend fun main() {
    val telegramToken = requireNotNull(System.getenv("TELEGRAM_BOT_TOKEN")) {
        "TELEGRAM_BOT_TOKEN environment variable is not specified"
    }
    val bot = telegramBot(telegramToken)

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

        onCommand("remove_all") {
            println("remove_all command for ${it.from} started")
            cache.remove(it.from!!.id)
            reply(it, "All accounts removed")
            println("remove_all command for ${it.from} finished")
        }

        onCommand("generate") {
            println("generate command for ${it.from} started")
            val accounts = cache[it.from!!.id] ?: emptySet()
            val alias = if (accounts.isNotEmpty()) selectAlias(it.chat, accounts) else null
            val iban: String
            val name: String
            if (alias == null) {
                sendTextMessage(it.chat, "Please enter IBAN for the account")
                iban = waitText().first().text
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
            reply(it, startText)
        }

        onCommandPrivacy(privacyText)

        setMyCommands(
            listOf(
                BotCommand("generate", "Generate SEPA QR code"),
                BotCommand("add", "Add account info to use in the future"),
                BotCommand("remove", "Remove account from the list of saved accounts"),
                BotCommand("remove_all", "Remove all accounts from the list of saved accounts"),
                BotCommand("start", "Get introduction for this bot"),
                BotCommand("privacy", "Learn what data this bot stores and why")
            )
        )

        setMyDescription(description)
        setMyShortDescription(description)
    }.join()
}

suspend fun BehaviourContext.selectAlias(chat: PreviewChat, accounts: Set<Account>): String? {
    sendMessage(chat, "Select alias", replyMarkup = inlineKeyboard {
        row {
            accounts.forEach { account ->
                dataButton(account.alias, account.alias)
            }
            dataButton("No alias", "<Proceed without alias>")
        }
    })
    val alias = waitDataCallbackQuery().first().data
    return alias.takeIf { it != "<Proceed without alias>" }
}

fun sepaQrText(name: String, iban: String, amount: Double, description: String) = """
BCD
002
1
SCT

$name
$iban
EUR$amount


$description
""".trimIndent()

fun sepaQrCode(name: String, iban: String, amount: Double, description: String) =
    QRCode.ofSquares().build(sepaQrText(name, iban, amount, description)).renderToBytes()

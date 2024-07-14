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
import dev.inmo.tgbotapi.types.chat.PreviewChat
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.utils.RiskFeature
import dev.inmo.tgbotapi.utils.row
import kotlinx.coroutines.flow.first
import java.sql.DriverManager

data class Account(val alias: String, val iban: String, val name: String)

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

val botCommands = listOf(
    BotCommand("generate", "Generate SEPA QR code"),
    BotCommand("add", "Add account info to use in the future"),
    BotCommand("remove", "Remove account from the list of saved accounts"),
    BotCommand("remove_all", "Remove all accounts from the list of saved accounts"),
    BotCommand("start", "Get introduction for this bot"),
    BotCommand("privacy", "Learn what data this bot stores and why")
)


@OptIn(RiskFeature::class)
suspend fun main() {
    val telegramToken = requireNotNull(System.getenv("TELEGRAM_BOT_TOKEN")) {
        "TELEGRAM_BOT_TOKEN environment variable is not specified"
    }
    val databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:sepa_qr_bot.db"
    val connection = DriverManager.getConnection(databaseUrl)
    connection.createAccountsTable()
    val bot = telegramBot(telegramToken)

    bot.buildBehaviourWithLongPolling {
        println(getMe())

        onCommand("add") {
            println("add command for ${it.from} started")

            sendTextMessage(it.chat, "Please enter alias for the account")
            val alias = waitText().first().text
            val iban = readIban(it.chat)
            sendTextMessage(it.chat, "Please enter name for the account")
            val name = waitText().first().text

            connection.addAccount(it.fromRawId, Account(alias, iban, name))
            sendTextMessage(it.chat, "Account $alias added")

            println("Add command for ${it.from} finished")
        }

        onCommand("remove") {
            println("remove command for ${it.from} started")
            val aliases = connection.getAliases(it.fromRawId)

            if (aliases.isEmpty()) {
                reply(it, "No accounts to remove")
                return@onCommand
            }

            sendMessage(it.chat, "Select alias to remove", replyMarkup = inlineKeyboard {
                row {
                    aliases.forEach { alias ->
                        dataButton(alias, alias)
                    }
                }
            })
            val alias = waitDataCallbackQuery().first().data
            connection.deleteAccount(it.fromRawId, alias)
            reply(it, "Alias $alias removed")
            println("remove command for ${it.from} finished")
        }

        onCommand("remove_all") {
            println("remove_all command for ${it.from} started")
            connection.deleteAllAccounts(it.fromRawId)
            reply(it, "All accounts removed")
            println("remove_all command for ${it.from} finished")
        }

        onCommand("generate") {
            println("generate command for ${it.from} started")
            val accounts = connection.getAccounts(it.fromRawId)
            val alias = if (accounts.isNotEmpty()) selectAlias(it.chat, accounts.map { it.alias }) else null
            val iban: String
            val name: String
            if (alias == null) {
                iban = readIban(it.chat)
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
        setMyCommands(botCommands)
        setMyDescription(description)
        setMyShortDescription(description)
    }.join()
}

suspend fun BehaviourContext.selectAlias(chat: PreviewChat, aliases: List<String>): String? {
    sendMessage(chat, "Select alias", replyMarkup = inlineKeyboard {
        row {
            aliases.forEach { dataButton(it, it) }
            dataButton("No alias", "<Proceed without alias>")
        }
    })
    val alias = waitDataCallbackQuery().first().data
    return alias.takeIf { it != "<Proceed without alias>" }
}

suspend fun BehaviourContext.readIban(chat: PreviewChat): String {
    sendTextMessage(chat, "Please enter IBAN for the account")
    var remainingAttempts = 3
    while (remainingAttempts-- > 0) {
        val iban = waitText().first().text
        if (validateIban(iban)) return iban
        if (remainingAttempts > 0) sendTextMessage(chat, "Invalid IBAN, please try again")
    }
    sendTextMessage(chat, "Too many failed attempts, stopping the process")
    throw IllegalStateException("Too many invalid IBAN attempts")
}

@OptIn(RiskFeature::class)
val CommonMessage<*>.fromRawId get() = from!!.id.chatId.long
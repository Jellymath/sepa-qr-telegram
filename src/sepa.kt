import qrcode.QRCode

/**
 * Text for SEPA QR code, see: [EPC Document](https://www.europeanpaymentscouncil.eu/sites/default/files/kb/file/2022-09/EPC069-12%20v3.0%20Quick%20Response%20Code%20-%20Guidelines%20to%20Enable%20the%20Data%20Capture%20for%20the%20Initiation%20of%20an%20SCT_0.pdf) for more details
 */
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
import qrcode.QRCode

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
val validIbanChars = ('0'..'9') + ('A'..'Z') + ' '

/**
 * Validate IBAN by characters, length and checksum.
 * Checksum calculation: [Wiki link](https://en.wikipedia.org/wiki/International_Bank_Account_Number#Validating_the_IBAN)
 */
fun validateIban(iban: String): Boolean {
    if (iban.trim().any { it !in validIbanChars }) return false
    val filtered = iban.filterNot { it.isWhitespace() }
    if (filtered.length !in 15..34) return false
    val swapped = filtered.drop(4) + filtered.take(4)
    val digits = swapped.map { if (it.isDigit()) it else it - 'A' + 10 }.joinToString("")
    val remainder = digits.toBigInteger() % 97.toBigInteger()
    return remainder == 1.toBigInteger()
}
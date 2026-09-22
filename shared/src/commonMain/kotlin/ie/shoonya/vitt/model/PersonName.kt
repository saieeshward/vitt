package ie.shoonya.vitt.model

/**
 * A readable name for someone a split is shared with.
 *
 * Participants are stored as whatever the user typed, which in practice is an
 * email address, because that is what the settlement summary is sent to. An
 * address is a poor name: `anya@example.com` is not how anybody thinks about
 * the person they split a taxi with, and a column of them reads as a database
 * rather than as people.
 *
 * So the card leads with a name and keeps the address underneath, where it is
 * still checkable before a summary goes out. Nothing is stored differently;
 * this is presentation only, and the address remains the identity.
 */
object PersonName {

    /**
     * The name to show, or the input unchanged when it is already one.
     *
     * Deliberately conservative. Anything it cannot confidently improve it
     * leaves alone: a wrong guess at somebody's name is worse than an address,
     * because the address at least is not pretending.
     */
    fun of(participant: String): String {
        val trimmed = participant.trim()
        if (trimmed.isEmpty()) return participant
        // Not an address: the user typed a name, so it is already the answer.
        val local = trimmed.substringBefore('@', missingDelimiterValue = "")
        if (local.isEmpty()) return trimmed

        // Plus-addressing is routing, not identity: anya+taxi@ is still Anya.
        val withoutTag = local.substringBefore('+')
        val words = withoutTag
            .split('.', '_', '-')
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return trimmed

        // A local part that is mostly digits is an identifier rather than a
        // name, and title-casing it produces something that looks like a name
        // and is not one.
        if (words.all { word -> word.all { it.isDigit() } }) return trimmed

        return words.joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }

    /** The address, shown under the name, or null when the input was not one. */
    fun address(participant: String): String? =
        participant.trim().takeIf { '@' in it && it.substringBefore('@').isNotEmpty() }
}

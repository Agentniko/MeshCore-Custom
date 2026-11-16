package io.github.thatsfguy.meshcore.custom

/**
 * A shareable contact profile card (MeshCore Custom).
 *
 * The card is ordinary text that travels as an ordinary MeshCore message, so the
 * protocol is untouched: a standard client simply shows `MCC1;n=Niko;d=niko`.
 * Nothing is verified — a card is a claim made by whoever sent it, and the UI must
 * say so and ask before saving it. Images are never sent over the radio.
 *
 * Wire form: `MCC1;` then `key=value` pairs separated by `;`. `%`, `;`, `=` and line
 * breaks inside values are percent-escaped. Unknown keys are ignored (forward
 * compatible); an unknown version prefix is rejected.
 */
data class ProfileCard(
    val name: String,
    val discordUser: String? = null,
    val discordId: String? = null,
    val discordLink: String? = null,
    val description: String? = null,
)

object ProfileCardCodec {
    const val PREFIX = "MCC1;"
    /** A MeshCore text payload holds at most 160 bytes. */
    const val MAX_BYTES = 160
    const val MAX_NAME = 32
    const val MAX_DISCORD_USER = 40
    const val MAX_LINK = 80
    const val MAX_DESCRIPTION = 60

    private val allowedLinkPrefixes = listOf(
        "https://discord.com/", "https://discord.gg/", "https://discordapp.com/",
    )

    fun byteSize(text: String): Int = text.encodeToByteArray().size

    /** True when [link] is an https Discord link made of URL-safe characters only. */
    fun isSafeDiscordLink(link: String): Boolean =
        link.length <= MAX_LINK &&
            allowedLinkPrefixes.any { link.startsWith(it) && link.length > it.length } &&
            link.all { it.isLetterOrDigit() && it.code < 128 || it in "/:._-" }

    /** A Discord snowflake: 17 to 20 digits. */
    fun isDiscordId(id: String): Boolean = id.length in 17..20 && id.all { it in '0'..'9' }

    private fun escape(s: String): String = buildString {
        for (ch in s) when (ch) {
            '%' -> append("%25")
            ';' -> append("%3B")
            '=' -> append("%3D")
            '\n' -> append("%0A")
            '\r' -> append("%0D")
            else -> append(ch)
        }
    }

    private fun unescape(s: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '%') {
                if (i + 3 > s.length) return null
                val code = s.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                out.append(code.toChar())
                i += 3
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    private fun clean(s: String?, max: Int): String? {
        val t = s?.trim()?.filter { it.code >= 0x20 && it.code != 0x7F }?.take(max)
        return t?.takeIf { it.isNotEmpty() }
    }

    private fun render(card: ProfileCard): String = buildString {
        append(PREFIX)
        append("n=").append(escape(card.name))
        card.discordUser?.let { append(";d=").append(escape(it)) }
        card.discordId?.let { append(";i=").append(it) }
        card.discordLink?.let { append(";l=").append(escape(it)) }
        card.description?.let { append(";t=").append(escape(it)) }
    }

    /**
     * Encodes [card], or returns null when it cannot fit in one message. Fields that are
     * too long are rejected by [sanitize]; if the result is still over [MAX_BYTES] the
     * description, then the link, are dropped (never the name). Only fields the caller
     * left non-null are written — privacy toggles are applied by clearing fields first.
     */
    fun encode(card: ProfileCard): String? {
        val c = sanitize(card) ?: return null
        val steps = listOf(c, c.copy(description = null), c.copy(description = null, discordLink = null))
        return steps.map(::render).firstOrNull { byteSize(it) <= MAX_BYTES }
    }

    /** Trims, strips control characters and drops invalid optional fields. Null if no name. */
    fun sanitize(card: ProfileCard): ProfileCard? {
        val name = clean(card.name, MAX_NAME) ?: return null
        val link = clean(card.discordLink, MAX_LINK)?.takeIf(::isSafeDiscordLink)
        val id = card.discordId?.trim()?.takeIf(::isDiscordId)
        return ProfileCard(
            name = name,
            discordUser = clean(card.discordUser, MAX_DISCORD_USER),
            discordId = id,
            discordLink = link,
            description = clean(card.description, MAX_DESCRIPTION),
        )
    }

    /** Parses [text] as a card, or null if it is not one (or is malformed). Never throws. */
    fun decode(text: String): ProfileCard? {
        val t = text.trim()
        if (!t.startsWith(PREFIX) || byteSize(t) > MAX_BYTES) return null
        val values = HashMap<String, String>()
        for (part in t.removePrefix(PREFIX).split(';')) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            if (eq <= 0) return null
            val key = part.substring(0, eq)
            val value = unescape(part.substring(eq + 1)) ?: return null
            if (key in values) return null
            values[key] = value
        }
        val name = values["n"] ?: return null
        return sanitize(
            ProfileCard(
                name = name,
                discordUser = values["d"],
                discordId = values["i"],
                discordLink = values["l"],
                description = values["t"],
            ),
        )
    }
}

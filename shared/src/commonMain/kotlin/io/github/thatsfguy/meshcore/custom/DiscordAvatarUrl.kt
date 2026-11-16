package io.github.thatsfguy.meshcore.custom

/**
 * Validation of a Discord avatar image link pasted by the user.
 *
 * Discord offers no way to look up someone's avatar from their ID without a bot token, so
 * the app never does that. The user may instead paste the link of the avatar image
 * (Discord → right-click the picture → copy image address). That is the ONLY outbound
 * request the profile feature can make: HTTPS, host `cdn.discordapp.com`, path
 * `/avatars/<id>/<hash>.<ext>`, started by an explicit tap, fetched once and kept locally.
 */
object DiscordAvatarUrl {
    private const val PREFIX = "https://cdn.discordapp.com/avatars/"
    private val extensions = setOf("png", "jpg", "jpeg", "webp", "gif")

    /**
     * Returns the normalised URL to fetch (always a 256 px PNG, which for an animated
     * avatar is its first frame), or null when [input] is not an acceptable avatar link.
     */
    fun normalise(input: String): String? {
        val s = input.trim()
        if (!s.startsWith(PREFIX) || s.length > 200) return null
        val withoutQuery = s.substringBefore('?').substringBefore('#')
        val rest = withoutQuery.removePrefix(PREFIX)
        val parts = rest.split('/')
        if (parts.size != 2) return null
        val id = parts[0]
        if (!ProfileCardCodec.isDiscordId(id)) return null
        val file = parts[1]
        val dot = file.lastIndexOf('.')
        if (dot <= 0) return null
        val hash = file.substring(0, dot)
        val ext = file.substring(dot + 1).lowercase()
        if (ext !in extensions) return null
        if (hash.isEmpty() || hash.length > 64) return null
        if (!hash.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }) return null
        return "$PREFIX$id/$hash.png?size=256"
    }
}

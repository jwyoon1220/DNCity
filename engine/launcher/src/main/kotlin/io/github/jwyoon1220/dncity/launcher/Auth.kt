// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What is kept between runs: only the refresh token and the display name (never the short-lived tokens). */
class StoredAccount(var refreshToken: String = "", var username: String = "", var uuid: String = "") {
    companion object {
        private fun file(root: Path) = root.resolve("account.json")

        fun load(root: Path): StoredAccount? = try {
            Files.newBufferedReader(file(root)).use { gson.fromJson(it, StoredAccount::class.java) }
                ?.takeIf { it.refreshToken.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        fun clear(root: Path) {
            Files.deleteIfExists(file(root))
        }
    }

    fun save(root: Path) {
        Files.createDirectories(root)
        val f = file(root)
        Files.newBufferedWriter(f).use { gson.toJson(this, it) }
        // Best effort: only the owner should read a refresh token (no-op on Windows' ACL model).
        runCatching {
            f.toFile().setReadable(false, false)
            f.toFile().setReadable(true, true)
            f.toFile().setWritable(false, false)
            f.toFile().setWritable(true, true)
        }
    }
}

/**
 * Microsoft -> Xbox Live -> XSTS -> Minecraft Services login, the same chain CmlLib/XboxAuthNet and every
 * third-party launcher use. Interactive sign-in is the OAuth *device code* flow: the user opens a URL and types
 * a short code, so no embedded browser and no client secret are needed. Requires an Azure app registration
 * (public client, "Allow public client flows") that Mojang has approved for the Minecraft API.
 */
class MicrosoftAuth(private val clientId: String) {

    class DeviceCode(val userCode: String, val verificationUri: String, val deviceCode: String, val intervalSec: Int, val expiresInSec: Int)
    class Tokens(val access: String, val refresh: String)

    private companion object {
        const val SCOPE = "XboxLive.signin offline_access"
    }

    /**
     * Two OAuth back ends: the legacy Live endpoint (`login.live.com`) for old-style 16-hex client ids such as the
     * Minecraft Java title id CmlLib uses by default (`XboxGameTitles.MinecraftJava`), and Entra ID v2 for an
     * app registered in Azure (GUID client id).
     */
    private val live = !clientId.contains('-')
    private val deviceCodeUrl = if (live) "https://login.live.com/oauth20_connect.srf" else "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private val tokenUrl = if (live) "https://login.live.com/oauth20_token.srf" else "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

    init {
        if (clientId.isBlank()) throw AuthException("Azure 클라이언트 ID가 설정되지 않았습니다. 설정에서 입력하세요.")
    }

    fun requestDeviceCode(): DeviceCode {
        val res = Http.postForm(deviceCodeUrl, mapOf("client_id" to clientId, "scope" to SCOPE, "response_type" to "device_code"))
        val j = parse(res)
        if (res.status != 200) throw AuthException("디바이스 코드 요청 실패: ${j.str("error_description") ?: res.body}")
        return DeviceCode(
            j.str("user_code")!!, j.str("verification_uri") ?: "https://microsoft.com/link", j.str("device_code")!!,
            j.get("interval")?.asInt ?: 5, j.get("expires_in")?.asInt ?: 900,
        )
    }

    /** Blocks until the user finishes signing in, the code expires, or [cancelled] returns true. */
    fun pollForToken(code: DeviceCode, cancelled: () -> Boolean): Tokens {
        var interval = code.intervalSec.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (cancelled()) throw AuthException("로그인이 취소되었습니다.")
            Thread.sleep(interval * 1000L)
            val res = Http.postForm(
                tokenUrl,
                mapOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to clientId,
                    "device_code" to code.deviceCode,
                ),
            )
            val j = parse(res)
            if (res.status == 200) return Tokens(j.str("access_token")!!, j.str("refresh_token") ?: "")
            when (j.str("error")) {
                "authorization_pending" -> {}
                "slow_down" -> interval += 5
                "authorization_declined", "access_denied" -> throw AuthException("로그인이 거부되었습니다.")
                "expired_token" -> throw AuthException("코드가 만료되었습니다. 다시 시도하세요.")
                else -> throw AuthException("로그인 실패: ${j.str("error_description") ?: res.body}")
            }
        }
        throw AuthException("코드가 만료되었습니다. 다시 시도하세요.")
    }

    fun refresh(refreshToken: String): Tokens {
        val res = Http.postForm(
            tokenUrl,
            mapOf("grant_type" to "refresh_token", "client_id" to clientId, "refresh_token" to refreshToken, "scope" to SCOPE),
        )
        val j = parse(res)
        if (res.status != 200) throw AuthException("로그인이 만료되었습니다. 다시 로그인하세요. (${j.str("error") ?: res.status})")
        return Tokens(j.str("access_token")!!, j.str("refresh_token") ?: refreshToken)
    }

    /** Microsoft access token -> a Minecraft [Session] (throws [AuthException] with a readable reason). */
    fun minecraftSession(microsoftAccessToken: String): Session {
        // 1. Xbox Live user token
        val xbl = json(
            Http.post(
                "https://user.auth.xboxlive.com/user/authenticate",
                """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$microsoftAccessToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}""",
                "application/json",
            ),
            "Xbox Live 인증",
        )
        val xblToken = xbl.str("Token") ?: throw AuthException("Xbox Live 토큰이 없습니다.")

        // 2. XSTS token for the Minecraft services
        val xstsRes = Http.post(
            "https://xsts.auth.xboxlive.com/xsts/authorize",
            """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}""",
            "application/json",
        )
        if (xstsRes.status == 401) {
            val xerr = runCatching { parse(xstsRes).get("XErr")?.asLong }.getOrNull()
            throw AuthException(
                when (xerr) {
                    2148916233L -> "이 Microsoft 계정에 Xbox 계정이 없습니다. xbox.com에서 먼저 만드세요."
                    2148916235L -> "Xbox Live를 사용할 수 없는 지역의 계정입니다."
                    2148916236L, 2148916237L -> "성인 인증이 필요한 계정입니다."
                    2148916238L -> "미성년자 계정은 가족 그룹에 추가되어야 합니다."
                    else -> "XSTS 인증 실패 (XErr=$xerr)"
                },
            )
        }
        val xsts = json(xstsRes, "XSTS 인증")
        val xstsToken = xsts.str("Token") ?: throw AuthException("XSTS 토큰이 없습니다.")
        val uhs = xsts.obj("DisplayClaims")?.arr("xui")?.get(0)?.asJsonObject?.str("uhs")
            ?: throw AuthException("XSTS 응답에 uhs가 없습니다.")

        // 3. Minecraft access token
        val loginRes = Http.post(
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}""",
            "application/json",
        )
        if (loginRes.status == 403) {
            // Microsoft sign-in and Xbox/XSTS worked; only Mojang's allow-list of Azure apps said no.
            throw AuthException(
                "Mojang가 이 Azure 앱(클라이언트 ID)을 아직 승인하지 않았습니다 (HTTP 403). " +
                    "https://aka.ms/mce-reviewappid 양식으로 Minecraft API 사용 승인을 신청하세요. " +
                    "승인 전에는 오프라인 닉네임으로만 플레이할 수 있습니다.",
            )
        }
        val login = json(loginRes, "Minecraft 로그인")
        val mcToken = login.str("access_token") ?: throw AuthException("Minecraft 토큰이 없습니다.")

        // 4. Profile (also proves the account owns the game)
        val profileRes = Http.getAuth("https://api.minecraftservices.com/minecraft/profile", mcToken)
        if (profileRes.status == 404) throw AuthException("이 계정은 Minecraft Java Edition을 소유하고 있지 않습니다.")
        val profile = json(profileRes, "프로필 조회")
        val id = profile.str("id") ?: throw AuthException("프로필 id가 없습니다.")
        val name = profile.str("name") ?: throw AuthException("프로필 이름이 없습니다.")
        return Session(name, uuidFromTrimmed(id), mcToken, "msa")
    }

    /** Full interactive sign-in. [onCode] shows the code to the user; [cancelled] aborts the wait. */
    fun interactiveLogin(root: Path, onCode: (DeviceCode) -> Unit, cancelled: () -> Boolean): Session {
        val code = requestDeviceCode()
        onCode(code)
        val tokens = pollForToken(code, cancelled)
        val session = minecraftSession(tokens.access)
        StoredAccount(tokens.refresh, session.username, session.uuid.toString()).save(root)
        return session
    }

    /** Silent sign-in from the stored refresh token; rotates the stored token. */
    fun silentLogin(root: Path, account: StoredAccount): Session {
        val tokens = refresh(account.refreshToken)
        val session = minecraftSession(tokens.access)
        StoredAccount(tokens.refresh, session.username, session.uuid.toString()).save(root)
        return session
    }

    private fun parse(res: Http.Response): JsonObject =
        try {
            JsonParser.parseString(res.body).asJsonObject
        } catch (e: Exception) {
            throw IOException("서버 응답을 읽을 수 없습니다 (HTTP ${res.status})", e)
        }

    private fun json(res: Http.Response, what: String): JsonObject {
        if (res.status != 200) throw AuthException("$what 실패 (HTTP ${res.status})")
        return parse(res)
    }

    private fun uuidFromTrimmed(s: String): UUID =
        UUID.fromString(s.replaceFirst(Regex("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)"), "$1-$2-$3-$4-$5"))
}

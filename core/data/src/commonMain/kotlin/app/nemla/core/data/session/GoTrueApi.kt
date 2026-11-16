package app.nemla.core.data.session

import app.nemla.core.common.AppResult
import app.nemla.core.network.BackendConfig
import app.nemla.core.network.safeRequest
import app.nemla.core.network.withoutUserSession
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

@Serializable
internal class GoTrueUserDto(
    val id: String,
    val email: String? = null,
    @SerialName("user_metadata") val userMetadata: JsonObject? = null,
)

@Serializable
internal class GoTrueSessionDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: GoTrueUserDto? = null,
    val id: String? = null,
) {
    override fun toString(): String = "GoTrueSessionDto(***)"

    fun toStoredSession(
        clock: Clock,
        fallbackEmail: String? = null,
    ): StoredSession? {
        val access = accessToken?.takeIf { it.isNotBlank() } ?: return null
        val refresh = refreshToken?.takeIf { it.isNotBlank() } ?: return null
        val account = user ?: return null
        val expiry = expiresAt ?: expiresIn?.let { clock.now().epochSeconds + it }
        return StoredSession(access, refresh, expiry, account.id, account.email ?: fallbackEmail)
    }
}

@Serializable
internal class PasswordGrantDto(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "PasswordGrantDto(***)"
}

@Serializable
internal class RefreshGrantDto(
    @SerialName("refresh_token") val refreshToken: String,
) {
    override fun toString(): String = "RefreshGrantDto(***)"
}

@Serializable
internal class SignUpMetadataDto(
    val pseudo: String? = null,
    val age: Int? = null,
    val sexe: String? = null,
    @SerialName("situation_familiale") val situationFamiliale: String? = null,
)

@Serializable
internal class SignUpDto(
    val email: String,
    val password: String,
    val data: SignUpMetadataDto,
) {
    override fun toString(): String = "SignUpDto(***)"
}

@Serializable
internal class RecoverDto(
    val email: String,
)

internal class GoTrueApi(
    private val client: HttpClient,
    private val config: BackendConfig,
) {
    suspend fun signInWithPassword(
        email: String,
        password: String,
    ): AppResult<GoTrueSessionDto> = postSessionless("token", PasswordGrantDto(email, password), grantType = "password")

    suspend fun refresh(refreshToken: String): AppResult<GoTrueSessionDto> =
        postSessionless("token", RefreshGrantDto(refreshToken), grantType = "refresh_token")

    suspend fun signUp(body: SignUpDto): AppResult<GoTrueSessionDto> = postSessionless("signup", body)

    suspend fun recover(email: String): AppResult<Unit> = postSessionless("recover", RecoverDto(email))

    suspend fun signOut(): AppResult<Unit> = safeRequest { client.post(endpoint("logout")) }

    suspend fun currentUser(): AppResult<GoTrueUserDto> = safeRequest { client.get(endpoint("user")) }

    private suspend inline fun <reified B : Any, reified T> postSessionless(
        path: String,
        body: B,
        grantType: String? = null,
    ): AppResult<T> =
        safeRequest {
            client.post(endpoint(path)) {
                withoutUserSession()
                if (grantType != null) parameter("grant_type", grantType)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private fun endpoint(path: String): String = config.supabaseUrl.trim().trimEnd('/') + "/auth/v1/" + path
}

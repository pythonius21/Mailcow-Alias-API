import retrofit2.http.GET
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.Headers
import kotlinx.coroutines.Deferred
import retrofit2.http.Header

// Datenklasse für Alias-Objekte
data class Alias(
    val address: String,
    val goto: String,
    val active: Boolean
)

interface MailcowApi {
    @GET("api/v1/get/alias/all")
    suspend fun getAllAliases(
        @Header("X-API-Key") apiKey: String
    ): List<Alias>
}

object MailcowService {
    fun create(baseUrl: String): MailcowApi {
        val client = OkHttpClient.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(MailcowApi::class.java)
    }
}

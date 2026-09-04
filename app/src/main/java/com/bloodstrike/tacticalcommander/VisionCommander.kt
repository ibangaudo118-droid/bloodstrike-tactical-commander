package com.bloodstrike.tacticalcommander

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

object VisionCommander {

    suspend fun signIn(): Result<Unit> {
        return try {
            val auth = SupabaseClient.client.auth

            if (auth.currentSessionOrNull() == null) {
                auth.signInAnonymously()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeFrame(
        jpegBase64: String
    ): Result<String> {

        return try {

            val response =
                SupabaseClient.client.functions.invoke(
                    function = "tactical-vision",
                    body = buildJsonObject {
                        put(
                            "image",
                            "data:image/jpeg;base64,$jpegBase64"
                        )
                    }
                )

            val json = response.body
                .toString()

            Result.success(json)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

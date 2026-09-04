package com.bloodstrike.tacticalcommander

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions

object SupabaseClient {

    private const val SUPABASE_URL =
        "https://tflibejcmtietdjmlfpb.supabase.co"

    private const val SUPABASE_KEY =
        "sb_publishable_AZbNlJ5k1_0DlAJ_lOB63w_xE3OtJdS"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth)
        install(Functions)
    }
}

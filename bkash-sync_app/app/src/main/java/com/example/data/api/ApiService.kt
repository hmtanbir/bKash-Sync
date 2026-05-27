package com.example.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {

    @POST
    suspend fun verifyApiKey(
        @Url url: String,
        @Header("Authorization") authHeader: String
    ): Response<VerifyResponse>

    @POST
    suspend fun syncTransactions(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body payload: TransactionPayload
    ): Response<SyncResponse>

    @POST
    suspend fun deltaSync(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body payload: DeltaSyncPayload
    ): Response<DeltaSyncResponse>
}

data class DeltaSyncPayload(
    val userPhoneNumber: String,
    val localTrxIds: List<String>
)

data class DeltaSyncResponse(
    val success: Boolean,
    val missingOnServer: List<String>?,
    val missingOnClient: List<TransactionItem>?
)

data class VerifyResponse(
    val success: Boolean
)

data class TransactionItem(
    val trxId: String,
    val amount: String,
    val senderNumber: String,
    val balance: String,
    val datetime: String,
    val userPhoneNumber: String? = null,
    val simSlot: String? = null
)

data class TransactionPayload(
    val transactions: List<TransactionItem>
)

data class SyncResponse(
    val success: Boolean?,
    val message: String?
)

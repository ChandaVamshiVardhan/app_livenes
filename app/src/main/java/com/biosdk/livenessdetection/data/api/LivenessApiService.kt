package com.biosdk.livenessdetection.data.api

import com.biosdk.livenessdetection.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface LivenessApiService {
    @POST("start-session")
    suspend fun startSession(): Response<SessionResponse>
    
    @POST("stop-session/{sessionId}")
    suspend fun stopSession(
        @Path("sessionId") sessionId: String,
        @Query("keep") keep: Boolean = false
    ): Response<StopSessionResponse>
    
    @GET("session/{sessionId}")
    suspend fun getSessionStatus(
        @Path("sessionId") sessionId: String
    ): Response<SessionStatus>
    
    @GET("session/{sessionId}/results")
    suspend fun getSessionResults(
        @Path("sessionId") sessionId: String
    ): Response<SessionSummary>
}
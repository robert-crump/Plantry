package com.example.plantry.data.claude

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeFailureTest {

    @Test
    fun fromStatus_mapsDocumentedApiErrors() {
        assertEquals(ClaudeFailure.INVALID_KEY, ClaudeFailure.fromStatus(401))
        assertEquals(ClaudeFailure.NO_CREDIT, ClaudeFailure.fromStatus(402))
        assertEquals(ClaudeFailure.PERMISSION_DENIED, ClaudeFailure.fromStatus(403))
        assertEquals(ClaudeFailure.MODEL_NOT_FOUND, ClaudeFailure.fromStatus(404))
        assertEquals(ClaudeFailure.RATE_LIMITED, ClaudeFailure.fromStatus(429))
        assertEquals(ClaudeFailure.OVERLOADED, ClaudeFailure.fromStatus(529))
        assertEquals(ClaudeFailure.OVERLOADED, ClaudeFailure.fromStatus(503))
        assertEquals(ClaudeFailure.UNKNOWN, ClaudeFailure.fromStatus(400))
    }

    @Test
    fun fromError_prefersErrorTypeOverStatus() {
        assertEquals(ClaudeFailure.NO_CREDIT, ClaudeFailure.fromError(400, "billing_error"))
        assertEquals(ClaudeFailure.INVALID_KEY, ClaudeFailure.fromError(400, "authentication_error"))
        assertEquals(ClaudeFailure.OVERLOADED, ClaudeFailure.fromError(529, "overloaded_error"))
        assertEquals(ClaudeFailure.UNKNOWN, ClaudeFailure.fromError(400, "invalid_request_error"))
        assertEquals(ClaudeFailure.INVALID_KEY, ClaudeFailure.fromError(401, null))
    }
}

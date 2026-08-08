package io.legado.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSessionAssessmentTest {
    @Test
    fun classifiesAuthenticationFailuresAsLoginRequired() {
        assertEquals(
            SourceSessionStatus.LOGIN_REQUIRED,
            SourceSessionAssessment.classify(401, "https://source.example/book", "")
        )
        assertEquals(
            SourceSessionStatus.LOGIN_REQUIRED,
            SourceSessionAssessment.classify(403, "https://source.example/book", "")
        )
    }

    @Test
    fun classifiesLoginRedirectsAndPasswordForms() {
        assertEquals(
            SourceSessionStatus.LOGIN_REQUIRED,
            SourceSessionAssessment.classify(302, "https://source.example/signin?next=/book", "")
        )
        assertEquals(
            SourceSessionStatus.LOGIN_REQUIRED,
            SourceSessionAssessment.classify(
                200,
                "https://source.example/book",
                "<form><input type=\"password\" name=\"password\"></form>"
            )
        )
    }

    @Test
    fun preservesAuthenticatedPagesContainingUnrelatedLoginText() {
        assertEquals(
            SourceSessionStatus.AUTHENTICATED,
            SourceSessionAssessment.classify(200, "https://source.example/book", "Login chapter title")
        )
        assertEquals(
            SourceSessionStatus.HTTP_ERROR,
            SourceSessionAssessment.classify(503, "https://source.example/book", "server error")
        )
        assertEquals(
            SourceSessionStatus.TRANSPORT_ERROR,
            SourceSessionAssessment.classify(null, null, "")
        )
    }

    @Test
    fun sourceDebugExposesSessionStatus() {
        val result = SourceDebugModel(object : io.legado.core.source.CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>) =
                io.legado.core.source.CoreHttpResponse(
                    url = "https://source.example/login",
                    body = "<input type=\"password\">",
                    statusCode = 200
                )
        }).execute(SourceDebugRequest("https://source.example/book"))

        assertEquals(SourceSessionStatus.LOGIN_REQUIRED, result.sessionStatus)
        assertEquals("https://source.example/login", result.finalUrl)
    }

    @Test
    fun sourceDebugExposesTheDetectedLoginUrlForBrowserLogin() {
        val result = SourceDebugModel(object : io.legado.core.source.CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>) =
                io.legado.core.source.CoreHttpResponse(
                    url = "https://source.example/signin?next=%2Fbook",
                    body = "",
                    statusCode = 302
                )
        }).execute(SourceDebugRequest("https://source.example/book"))

        assertEquals(SourceSessionStatus.LOGIN_REQUIRED, result.sessionStatus)
        assertEquals("https://source.example/signin?next=%2Fbook", result.loginUrl)
    }

    @Test
    fun authenticatedDebugResponseDoesNotOfferLoginUrl() {
        val result = SourceDebugModel(object : io.legado.core.source.CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>) =
                io.legado.core.source.CoreHttpResponse(url = url, body = "book", statusCode = 200)
        }).execute(SourceDebugRequest("https://source.example/book"))

        assertEquals(SourceSessionStatus.AUTHENTICATED, result.sessionStatus)
        assertEquals(null, result.loginUrl)
    }
}

package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.document.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence.DocumentRequestEntity
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence.DocumentRequestRepository
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.DocumentRequestSummary
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationResult
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.DocumentRequestParametersFixtures.givenRawContextualReportRequestParameters
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.wiremock.DocumentManagementApiExtension.Companion.documentManagementApi
import java.time.Instant
import java.util.UUID

class DocumentRequestControllerTest : IntegrationTestBase() {

  @Autowired
  private lateinit var repository: DocumentRequestRepository

  private val role = "ROLE_EM_CRIME_MATCHING__DOCUMENT_REQUESTS__RW"

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Nested
  @DisplayName("POST /document-requests")
  inner class CreateDocumentRequest {
    @Test
    fun `it should return a 401 if the request is not authenticated`() {
      webTestClient.post()
        .uri("/document-requests")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          givenContextualReportRequestBody(),
        )
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `it should return a 403 if the client does not have a valid role`() {
      webTestClient.post()
        .uri("/document-requests")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          givenContextualReportRequestBody(),
        )
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `it should create a contextual report document request`() {
      // Given a valid request
      val body = givenContextualReportRequestBody()

      // When the request is sent to the controller, then it should be accepted
      val request = webTestClient
        .post()
        .uri("/document-requests")
        .headers(setAuthorisation(roles = listOf(role)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isAccepted
        .expectBody<DocumentRequestSummary>()
        .returnResult()
        .responseBody!!

      // And the request should be persisted
      val saved = repository.findById(UUID.fromString(request.id)).orElseThrow()

      assertThat(saved.type).isEqualTo(DocumentType.CONTEXTUAL_REPORT)
      assertThat(saved.status).isEqualTo(DocumentRequestStatus.PENDING)
      assertThat(saved.parameters).containsKeys(
        "authorisingManager",
        "crime",
        "deviceWearer",
        "positionSegments",
        "reportPeriod",
        "reportRequest",
      )
      assertThat(saved.createdBy).isEqualTo("AUTH_ADM")
    }

    @Test
    fun `it should reject an unsupported document type`() {
      // Given an invalid request (unsupported document type)
      val body = """
      {
        "type": "UNKNOWN",
        "parameters": {}
      }
      """.trimIndent()

      // When the request is sent to the controller, then it should be rejected
      webTestClient.post()
        .uri("/document-requests")
        .headers(setAuthorisation(roles = listOf(role)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest

      // And the request should not be persisted
      assertThat(repository.count()).isZero()
    }

    @Test
    fun `it should reject a contextual report with an invalid report period`() {
      // Given an invalid request (start before end)
      val body = givenContextualReportRequestBody(
        givenRawContextualReportRequestParameters() + mapOf(
          "reportPeriod" to mapOf(
            "start" to "2026-10-02T08:00:00Z",
            "end" to "2026-10-01T08:00:00Z",
          ),
        ),
      )

      // When the request is sent to the controller, then it should be rejected
      webTestClient
        .post()
        .uri("/document-requests")
        .headers(setAuthorisation(roles = listOf(role)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest

      // And the request should not be persisted
      assertThat(repository.count()).isZero()
    }

    @Test
    fun `it should reject a request with missing parameters`() {
      // Given an invalid request (missing position segments)
      val body = givenContextualReportRequestBody(
        givenRawContextualReportRequestParameters() + mapOf("positionSegments" to emptyList<Any>()),
      )

      // When the request is sent to the controller, then it should be rejected
      webTestClient.post()
        .uri("/document-requests")
        .headers(setAuthorisation(roles = listOf(role)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest

      // And the request should not be persisted
      assertThat(repository.count()).isZero()
    }
  }

  @Nested
  @DisplayName("GET /document-requests/{documentRequestId}")
  inner class GetDocumentRequest {
    @Test
    fun `it should return a 401 if the request is not authenticated`() {
      webTestClient.get()
        .uri("/document-requests/${UUID.randomUUID()}")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `it should return a 403 if the client does not have a valid role`() {
      webTestClient.get()
        .uri("/document-requests/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `it should retrieve an existing document request`() {
      // Given an existing document request
      val id = UUID.randomUUID()

      repository.save(givenDocumentRequestEntity(id))

      // When the request is retrieved
      val response = webTestClient.get()
        .uri("/document-requests/$id")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<DocumentRequestSummary>()
        .returnResult()
        .responseBody!!

      // Then the document request is returned
      assertThat(response.id).isEqualTo(id.toString())
      assertThat(response.type).isEqualTo(DocumentType.CONTEXTUAL_REPORT)
      assertThat(response.status).isEqualTo(DocumentRequestStatus.PENDING)
      assertThat(response.createdBy).isEqualTo("test")
      assertThat(response.createdAt).isEqualTo(Instant.parse("2026-10-08T12:00:00Z"))
    }

    @Test
    fun `it should return a 404 if the document request does not exist`() {
      webTestClient.get()
        .uri("/document-requests/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `it should return a 400 if the document request ID is not a UUID`() {
      webTestClient.get()
        .uri("/document-requests/abc")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isBadRequest
    }
  }

  @Nested
  @DisplayName("GET /document-requests")
  inner class ListDocumentRequests {
    @Test
    fun `it should return a 401 if the request is not authenticated`() {
      webTestClient.get()
        .uri("/document-requests")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `it should return a 403 if the client does not have a valid role`() {
      webTestClient.get()
        .uri("/document-requests")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `it should return a paginated list of document requests`() {
      // Given three document requests
      val requests = (1..3).map { day ->
        givenDocumentRequestEntity(
          createdAt = Instant.parse("2026-10-0${day}T12:00:00Z"),
        ).also(repository::save)
      }

      // When the first page is requested
      val response = webTestClient.get()
        .uri("/document-requests?page=0&size=2")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PaginationResult<DocumentRequestSummary>>()
        .returnResult()
        .responseBody!!

      // Then the most recent requests should be returned
      assertThat(response.data.map { it.id }).containsExactly(
        requests[2].id.toString(),
        requests[1].id.toString(),
      )
      assertThat(response.pageCount).isEqualTo(2)
      assertThat(response.pageNumber).isEqualTo(0)
      assertThat(response.pageSize).isEqualTo(2)
    }

    @Test
    fun `it should return the second page of document requests`() {
      // Given three document requests
      val requests = (1..3).map { day ->
        givenDocumentRequestEntity(
          createdAt = Instant.parse("2026-10-0${day}T12:00:00Z"),
        ).also(repository::save)
      }

      // When the second page is requested
      val response = webTestClient.get()
        .uri("/document-requests?page=1&size=2")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PaginationResult<DocumentRequestSummary>>()
        .returnResult()
        .responseBody!!

      // Then the oldest request should be returned
      assertThat(response.data.map { it.id }).containsExactly(
        requests[0].id.toString(),
      )
      assertThat(response.pageCount).isEqualTo(2)
      assertThat(response.pageNumber).isEqualTo(1)
      assertThat(response.pageSize).isEqualTo(2)
    }

    @Test
    fun `it should return an empty list when no document requests exist`() {
      val response = webTestClient.get()
        .uri("/document-requests")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PaginationResult<DocumentRequestSummary>>()
        .returnResult()
        .responseBody!!

      assertThat(response.data).isEmpty()
      assertThat(response.pageCount).isZero()
      assertThat(response.pageNumber).isZero()
      assertThat(response.pageSize).isEqualTo(20)
    }

    @Test
    fun `it should return a 400 for invalid pagination parameters`() {
      webTestClient.get()
        .uri("/document-requests?page=-1&size=0")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isBadRequest
    }
  }

  @Nested
  @DisplayName("GET /document-requests/{documentRequestId}/artefact")
  inner class GetDocumentRequestArtefactRoute {
    @Test
    fun `it should return a 401 if the request is not authenticated`() {
      webTestClient.get()
        .uri("/document-requests/${UUID.randomUUID()}/artefact")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `it should return a 403 if the client does not have a valid role`() {
      webTestClient.get()
        .uri("/document-requests/${UUID.randomUUID()}/artefact")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `it should return a 404 when the document request does not have an artefact`() {
      val documentRequestId = UUID.randomUUID()
      repository.save(givenDocumentRequestEntity(id = documentRequestId))

      webTestClient.get()
        .uri("/document-requests/$documentRequestId/artefact")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `it should return the artefact bytes when the artefact exists`() {
      val documentRequestId = UUID.randomUUID()
      val documentUuid = UUID.randomUUID()
      repository.save(
        givenDocumentRequestEntity(
          id = documentRequestId,
          artefactReference = documentUuid.toString(),
          artefactFilename = "document-request.pdf",
          artefactContentType = "application/pdf",
        ),
      )

      webTestClient.get()
        .uri("/document-requests/$documentRequestId/artefact")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isOk
        .expectHeader().contentType(MediaType.APPLICATION_PDF)
        .expectBody<ByteArray>()
        .isEqualTo("document artefact content".toByteArray())
    }

    @Test
    fun `it should return large artefact bytes when the artefact exceeds the default WebClient buffer size`() {
      val documentRequestId = UUID.randomUUID()
      val documentUuid = UUID.randomUUID()
      val expectedContent = "A".repeat(300 * 1024)
      repository.save(
        givenDocumentRequestEntity(
          id = documentRequestId,
          artefactReference = documentUuid.toString(),
          artefactFilename = "document-request.pdf",
          artefactContentType = "application/pdf",
        ),
      )

      documentManagementApi.stubGetDocumentFile(
        documentUuid = documentUuid,
        responseBody = expectedContent,
      )

      webTestClient.get()
        .uri("/document-requests/$documentRequestId/artefact")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isOk
        .expectHeader().contentType(MediaType.APPLICATION_PDF)
        .expectBody<ByteArray>()
        .isEqualTo(expectedContent.toByteArray())
    }

    @Test
    fun `it should return a 500 when the artefact content type is invalid`() {
      val documentRequestId = UUID.randomUUID()
      repository.save(
        givenDocumentRequestEntity(
          id = documentRequestId,
          artefactReference = "document-artefact-reference",
          artefactFilename = "document-request.pdf",
          artefactContentType = "invalid-content-type",
        ),
      )

      webTestClient.get()
        .uri("/document-requests/$documentRequestId/artefact")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .is5xxServerError
    }

    @Test
    fun `it should return a 404 when the remote document artefact is missing`() {
      val documentRequestId = UUID.randomUUID()
      val documentUuid = UUID.randomUUID()
      repository.save(
        givenDocumentRequestEntity(
          id = documentRequestId,
          artefactReference = documentUuid.toString(),
          artefactFilename = "document-request.pdf",
          artefactContentType = "application/pdf",
        ),
      )

      documentManagementApi.stubGetDocumentFile(
        documentUuid = documentUuid,
        status = 404,
      )

      webTestClient.get()
        .uri("/document-requests/$documentRequestId/artefact")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `it should return a 500 when the Document Management API returns a 500`() {
      val documentRequestId = UUID.randomUUID()
      val documentUuid = UUID.randomUUID()
      repository.save(
        givenDocumentRequestEntity(
          id = documentRequestId,
          artefactReference = documentUuid.toString(),
          artefactFilename = "document-request.pdf",
          artefactContentType = "application/pdf",
        ),
      )

      documentManagementApi.stubGetDocumentFile(
        documentUuid = documentUuid,
        status = 500,
      )

      webTestClient.get()
        .uri("/document-requests/$documentRequestId/artefact")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .is5xxServerError
    }

    @Test
    fun `it should return a 429 when the Document Management API returns a 429 after retries are exhausted`() {
      val documentRequestId = UUID.randomUUID()
      val documentUuid = UUID.randomUUID()
      repository.save(
        givenDocumentRequestEntity(
          id = documentRequestId,
          artefactReference = documentUuid.toString(),
          artefactFilename = "document-request.pdf",
          artefactContentType = "application/pdf",
        ),
      )

      documentManagementApi.stubGetDocumentFile(
        documentUuid = documentUuid,
        status = 429,
      )

      webTestClient.get()
        .uri("/document-requests/$documentRequestId/artefact")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
    }
  }

  private fun givenDocumentRequestEntity(
    id: UUID = UUID.randomUUID(),
    createdAt: Instant = Instant.parse("2026-10-08T12:00:00Z"),
    artefactReference: String? = null,
    artefactFilename: String? = null,
    artefactContentType: String? = null,
    claimedAt: Instant? = Instant.parse("2026-10-08T13:00:00Z"),
    attempts: Int = 0,
    lastError: String? = null,
  ): DocumentRequestEntity = DocumentRequestEntity(
    id = id,
    type = DocumentType.CONTEXTUAL_REPORT,
    status = DocumentRequestStatus.PENDING,
    parameters = givenRawContextualReportRequestParameters(),
    createdBy = "test",
    createdAt = createdAt,
    artefactReference = artefactReference,
    artefactFilename = artefactFilename,
    artefactContentType = artefactContentType,
    claimedAt = claimedAt,
    attempts = attempts,
    lastError = lastError,
    concurrencyVersion = 0L,
  )

  private fun givenContextualReportRequestBody(
    parameters: Map<String, Any> = givenRawContextualReportRequestParameters(),
  ): Map<String, Any> = mapOf(
    "type" to "CONTEXTUAL_REPORT",
    "parameters" to parameters,
  )
}

package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.document.application.usecase

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.ContextualReportDocxGenerator
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.DocumentGeneratorRegistry
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.DocumentSensitivityLabeller
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.processnextdocumentrequest.ProcessDocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentStore
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.DocumentRequestParametersFixtures.givenContextualReportRequestParameters
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.wiremock.DocumentManagementApiExtension.Companion.documentManagementApi
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProcessDocumentRequestIntegrationTest : IntegrationTestBase() {
  @Autowired
  private lateinit var documentStore: DocumentStore

  private lateinit var useCase: ProcessDocumentRequest

  private val clock = Clock.fixed(
    Instant.parse("2026-10-08T12:00:00Z"),
    ZoneOffset.UTC,
  )

  @BeforeEach
  fun setUp() {
    useCase = ProcessDocumentRequest(
      generatorRegistry = DocumentGeneratorRegistry(listOf(ContextualReportDocxGenerator(DocumentSensitivityLabeller(clock = clock)))),
      documentStore = documentStore,
    )
  }

  @Test
  fun `it should generate a contextual report and save it in the document management API`() {
    val request = givenDocumentRequest()

    useCase.process(request)

    assertThat(request.status).isEqualTo(DocumentRequestStatus.COMPLETED)
    val artefact = requireNotNull(request.artefact)
    assertThat(artefact.reference).isNotBlank()
    assertThat(artefact.filename).isEqualTo("placeholder")
    assertThat(artefact.contentType).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  }

  @Test
  fun `it should not complete the request if the document management API rejects the upload`() {
    documentManagementApi.stubUploadDocumentFailure(400)
    val request = givenDocumentRequest()

    assertThrows<WebClientResponseException.BadRequest> {
      useCase.process(request)
    }

    assertThat(request.status).isEqualTo(DocumentRequestStatus.PENDING)
    assertThat(request.artefact).isNull()
    documentManagementApi.verifyUploadRequestCount(1)
  }

  @Test
  fun `it should attempt to retry a 500 error from the document management API`() {
    documentManagementApi.stubUploadDocumentFailure(500)
    val request = givenDocumentRequest()

    val throwable = catchThrowable { useCase.process(request) }

    assertThat(throwable)
      .isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    assertThat((throwable as WebClientResponseException.InternalServerError).statusCode.value()).isEqualTo(500)

    assertThat(request.status).isEqualTo(DocumentRequestStatus.PENDING)
    assertThat(request.artefact).isNull()
    documentManagementApi.verifyUploadRequestCount(3)
  }

  private fun givenDocumentRequest(
    status: DocumentRequestStatus = DocumentRequestStatus.PENDING,
  ): DocumentRequest = DocumentRequest.rehydrate(
    id = UUID.randomUUID(),
    type = DocumentType.CONTEXTUAL_REPORT,
    parameters = givenContextualReportRequestParameters(),
    createdBy = "user123",
    createdAt = clock.instant(),
    status = status,
    claimedAt = clock.instant(),
    attempts = 1,
    lastError = null,
    concurrencyVersion = 1L,
  )
}

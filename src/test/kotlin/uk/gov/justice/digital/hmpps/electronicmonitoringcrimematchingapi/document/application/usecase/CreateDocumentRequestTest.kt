package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.CreateDocumentRequestCommand
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.exception.InvalidRequestException
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.mapper.DocumentRequestMapper
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.parser.DocumentRequestParameterParser
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.ContextualReportDocxGenerator
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.DocumentGeneratorRegistry
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.DocumentSensitivityLabeller
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.processnextdocumentrequest.ProcessDocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.DocumentRequestParametersFixtures.givenContextualReportRequestParameters
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.DocumentRequestParametersFixtures.givenRawContextualReportRequestParameters
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.InMemoryDocumentRequestStore
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.InMemoryDocumentStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CreateDocumentRequestTest {
  private val store = InMemoryDocumentRequestStore()
  private val documentStore = InMemoryDocumentStore()

  private val clock = Clock.fixed(
    Instant.parse("2026-10-08T12:00:00Z"),
    ZoneOffset.UTC,
  )

  private val processDocumentRequest = ProcessDocumentRequest(
    DocumentGeneratorRegistry(
      listOf(
        ContextualReportDocxGenerator(
          DocumentSensitivityLabeller(
            clock,
          ),
        ),
      ),
    ),
    documentStore = documentStore,
  )

  private val processNextDocumentRequest = ProcessNextDocumentRequest(
    documentRequestStore = store,
    processDocumentRequest = processDocumentRequest,
    clock = clock,
  )

  private val objectMapper = JsonMapper.builder()
    .findAndAddModules()
    .build()

  private val useCase = CreateDocumentRequest(
    store = store,
    mapper = DocumentRequestMapper(),
    parser = DocumentRequestParameterParser(objectMapper),
    clock = clock,
    processNextDocumentRequest = processNextDocumentRequest,
  )

  @Test
  fun `it should create and persist a contextual report request`() {
    // Given a valid command
    val command = givenCreateDocumentRequestCommand()

    // When a document request is created
    val result = useCase.create(command, "user123")

    // Then the document request should be persisted
    val saved = store.findById(java.util.UUID.fromString(result.id))

    assertThat(saved).isNotNull
    assertThat(saved!!.id.toString()).isEqualTo(result.id)
    assertThat(saved.type).isEqualTo(DocumentType.CONTEXTUAL_REPORT)
    assertThat(saved.status).isEqualTo(DocumentRequestStatus.COMPLETED)
    assertThat(saved.createdBy).isEqualTo("user123")
    assertThat(saved.createdAt).isEqualTo(clock.instant())

    assertThat(saved.parameters).isEqualTo(
      givenContextualReportRequestParameters(),
    )
  }

  @Test
  fun `it should not persist a request with invalid parameters`() {
    // Given a command with invalid parameters
    val invalidCommand = givenCreateDocumentRequestCommand(
      parameters = givenRawContextualReportRequestParameters() + ("positionSegments" to emptyList<Any>()),
    )

    // When a document request is created, then it should throw an exception and not persist the request
    assertThatThrownBy {
      useCase.create(invalidCommand, "user123")
    }
      .isInstanceOf(InvalidRequestException::class.java)
      .hasMessage("'positionSegments' must not be empty")

    assertThat(store.findAll(pagination = PaginationRequest(page = 0, size = 20)).data).isEmpty()
  }

  @Test
  fun `it should create independent document requests`() {
    // Given a valid command
    val command = givenCreateDocumentRequestCommand()

    // When two document requests are created with the same command
    val first = useCase.create(command, "user123")
    val second = useCase.create(command, "user123")

    // Then the two requests should have been created
    assertThat(first.id).isNotEqualTo(second.id)
    assertThat(store.findAll(pagination = PaginationRequest(page = 0, size = 20)).data).hasSize(2)
  }

  private fun givenCreateDocumentRequestCommand(
    parameters: Map<String, Any> = givenRawContextualReportRequestParameters(),
  ): CreateDocumentRequestCommand = CreateDocumentRequestCommand(
    type = DocumentType.CONTEXTUAL_REPORT,
    parameters = parameters,
  )
}

package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.ContextualReportDocxGenerator
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.DocumentGeneratorRegistry
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.DocumentSensitivityLabeller
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.processnextdocumentrequest.ProcessDocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentArtefact
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.DocumentRequestParametersFixtures
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.InMemoryDocumentRequestStore
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.InMemoryDocumentStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

// TODO(ACR-1101): Make sure relevant tests are added for async generation
class ProcessNextDocumentRequestTest {
  private val documentStore = InMemoryDocumentStore()
  private val documentRequestStore = InMemoryDocumentRequestStore()

  private val clock = Clock.fixed(
    Instant.parse("2026-10-08T12:00:00Z"),
    ZoneOffset.UTC,
  )

  private val processDocumentRequest = ProcessDocumentRequest(
    generatorRegistry = DocumentGeneratorRegistry(
      generators = listOf(
        ContextualReportDocxGenerator(
          DocumentSensitivityLabeller(clock = clock),
        ),
      ),
    ),
    documentStore = documentStore,
  )

  private val useCase = ProcessNextDocumentRequest(
    documentRequestStore = documentRequestStore,
    clock = clock,
    processDocumentRequest = processDocumentRequest,
  )

  @Test
  fun `it should generate and persist a contextual report`() {
    // Given a document request
    val request = givenDocumentRequest(claimedAt = null)
    documentRequestStore.save(request)

    // When the document request is processed
    useCase.process()

    // Then the generated document should be persisted
    val persisted = documentRequestStore.findById(request.id)
    Assertions.assertThat(persisted).isNotNull
    Assertions.assertThat(persisted!!.artefact).isNotNull
    val saved = documentStore.findByReference(persisted.artefact!!.reference)
    Assertions.assertThat(saved).isNotNull
    Assertions.assertThat(persisted.status).isEqualTo(DocumentRequestStatus.COMPLETED)
  }

  @Test
  fun `it should not generate or persist a contextual report if the document request is not in pending state`() {
    // Given a completed document request
    val request = givenDocumentRequest(
      status = DocumentRequestStatus.COMPLETED,
      claimedAt = null,
    )
    documentRequestStore.save(request)

    // When the document request is processed
    useCase.process()

    // Then the generated document should NOT be persisted
    val persisted = documentRequestStore.findById(request.id)
    Assertions.assertThat(persisted).isNotNull
    Assertions.assertThat(persisted!!.artefact).isNull()
    // TODO(ACR-1101): In the next PR, we will add failure handling and this result will need to change:
    Assertions.assertThat(persisted.status).isEqualTo(DocumentRequestStatus.COMPLETED)
    Assertions.assertThat(documentStore.findAll()).isEmpty()
  }

  private fun givenDocumentRequest(
    status: DocumentRequestStatus = DocumentRequestStatus.PENDING,
    claimedAt: Instant? = null,
    artefact: DocumentArtefact? = null,
  ): DocumentRequest = DocumentRequest.rehydrate(
    id = UUID.randomUUID(),
    type = DocumentType.CONTEXTUAL_REPORT,
    parameters = DocumentRequestParametersFixtures.givenContextualReportRequestParameters(),
    createdBy = "user123",
    createdAt = clock.instant(),
    status = status,
    claimedAt = claimedAt,
    attempts = 0,
    lastError = null,
    concurrencyVersion = 0L,
    artefact = artefact,
  )
}

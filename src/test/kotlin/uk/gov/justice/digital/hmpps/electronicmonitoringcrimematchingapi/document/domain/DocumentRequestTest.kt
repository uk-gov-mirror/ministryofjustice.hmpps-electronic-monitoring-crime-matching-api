package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.DocumentRequestParametersFixtures.givenContextualReportRequestParameters
import java.time.Instant
import java.util.UUID

class DocumentRequestTest {
  private val createdAt = Instant.parse("2026-10-08T12:00:00Z")
  private val claimedAt = Instant.parse("2026-10-08T13:00:00Z")

  private val parameters = givenContextualReportRequestParameters()

  @Test
  fun `it should create a pending document request`() {
    val request = DocumentRequest.create(
      parameters = parameters,
      createdBy = "user123",
      createdAt = createdAt,
    )

    assertThat(request.id).isNotNull()
    assertThat(request.type).isEqualTo(DocumentType.CONTEXTUAL_REPORT)
    assertThat(request.parameters).isEqualTo(parameters)
    assertThat(request.status).isEqualTo(DocumentRequestStatus.PENDING)
    assertThat(request.createdBy).isEqualTo("user123")
    assertThat(request.createdAt).isEqualTo(createdAt)
  }

  @Test
  fun `it should restore a persisted document request`() {
    val id = UUID.randomUUID()

    val request = DocumentRequest.rehydrate(
      id = id,
      type = DocumentType.CONTEXTUAL_REPORT,
      parameters = parameters,
      status = DocumentRequestStatus.PENDING,
      createdBy = "user123",
      createdAt = createdAt,
      claimedAt = claimedAt,
      attempts = 0,
      lastError = null,
      concurrencyVersion = 1L,
    )

    assertThat(request.id).isEqualTo(id)
    assertThat(request.type).isEqualTo(DocumentType.CONTEXTUAL_REPORT)
    assertThat(request.parameters).isEqualTo(parameters)
    assertThat(request.status).isEqualTo(DocumentRequestStatus.PENDING)
    assertThat(request.createdBy).isEqualTo("user123")
    assertThat(request.createdAt).isEqualTo(createdAt)
    assertThat(request.claimedAt).isEqualTo(claimedAt)
    assertThat(request.attempts).isEqualTo(0)
    assertThat(request.lastError).isNull()
    assertThat(request.concurrencyVersion).isEqualTo(1L)
  }
}

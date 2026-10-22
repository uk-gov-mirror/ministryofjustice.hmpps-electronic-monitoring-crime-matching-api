package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.IntegrationTestBase
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@ActiveProfiles("integration")
class DocumentRequestRepositoryTest : IntegrationTestBase() {
  @Autowired
  lateinit var documentRequestRepository: DocumentRequestRepository

  @Autowired
  lateinit var transactionTemplate: TransactionTemplate

  @Autowired
  lateinit var jdbcTemplate: JdbcTemplate

  @BeforeEach
  fun setup() {
    documentRequestRepository.deleteAll()
  }

  @Test
  fun `it should claim pending rows that are unclaimed or claimed before cutoff`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val eligibleUnclaimed = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    val eligibleStaleClaim = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = cutoff.minusMillis(1),
      createdAt = Instant.parse("2026-01-01T00:01:00Z"),
    )
    val ineligibleRecentClaim = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = cutoff.plusMillis(1),
      createdAt = Instant.parse("2026-01-01T00:02:00Z"),
    )
    val ineligibleCompleted = givenDocumentRequestRow(
      status = DocumentRequestStatus.COMPLETED,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:03:00Z"),
    )

    val claimedRows = documentRequestRepository.claimEligibleDocumentRequests(
      pendingState = DocumentRequestStatus.PENDING.name,
      batchSize = 10,
      cutoff = cutoff,
      now = now,
    )

    assertThat(claimedRows.map { it.id }).containsExactlyInAnyOrder(
      eligibleUnclaimed.id,
      eligibleStaleClaim.id,
    )
    assertThat(claimedRows).allSatisfy { claimed ->
      assertThat(claimed.status).isEqualTo(DocumentRequestStatus.PENDING)
    }

    assertThat(claimedAtFor(eligibleUnclaimed.id)).isEqualTo(now)
    assertThat(claimedAtFor(eligibleStaleClaim.id)).isEqualTo(now)
    assertThat(claimedAtFor(ineligibleRecentClaim.id)).isEqualTo(cutoff.plusMillis(1))
    assertThat(claimedAtFor(ineligibleCompleted.id)).isNull()
  }

  @Test
  fun `it should increment the optimistic lock version when a row is claimed`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val eligible = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    val claimedRows = documentRequestRepository.claimEligibleDocumentRequests(
      pendingState = DocumentRequestStatus.PENDING.name,
      batchSize = 10,
      cutoff = cutoff,
      now = now,
    )

    assertThat(claimedRows).hasSize(1)
    assertThat(claimedRows.first().concurrencyVersion).isEqualTo(1L)
    assertThat(claimedAtFor(eligible.id)).isEqualTo(now)
  }

  @Test
  fun `it should return no rows when there are no eligible rows`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val pendingRecentClaim = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = cutoff.plusMillis(5),
    )
    val completed = givenDocumentRequestRow(
      status = DocumentRequestStatus.COMPLETED,
      claimedAt = null,
    )

    val claimedRows = documentRequestRepository.claimEligibleDocumentRequests(
      pendingState = DocumentRequestStatus.PENDING.name,
      batchSize = 10,
      cutoff = cutoff,
      now = now,
    )

    assertThat(claimedRows).isEmpty()

    assertThat(claimedAtFor(pendingRecentClaim.id)).isEqualTo(cutoff.plusMillis(5))
    assertThat(claimedAtFor(completed.id)).isNull()
  }

  @Test
  fun `it should claim at most one eligible row`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val eligibleOne = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    val eligibleTwo = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:01:00Z"),
    )

    val claimedRows = documentRequestRepository.claimEligibleDocumentRequests(
      pendingState = DocumentRequestStatus.PENDING.name,
      batchSize = 1,
      cutoff = cutoff,
      now = now,
    )

    assertThat(claimedRows.map { it.id }).containsExactly(eligibleOne.id)

    assertThat(claimedAtFor(eligibleOne.id)).isEqualTo(now)
    assertThat(claimedAtFor(eligibleTwo.id)).isNull()
  }

  @Test
  fun `it should skip a row already locked by another claim transaction`() {
    val row = givenDocumentRequestRow(
      status = DocumentRequestStatus.PENDING,
      claimedAt = null,
    )
    val cutoff = Instant.parse("2026-01-01T00:09:00Z")
    val firstClaimNow = Instant.parse("2026-01-01T00:10:00Z")
    val secondClaimNow = Instant.parse("2026-01-01T00:10:30Z")

    val firstClaimedRows = AtomicReference<Collection<DocumentRequestEntity>>(emptyList())
    val secondClaimedRows = AtomicReference<Collection<DocumentRequestEntity>>(emptyList())
    val firstClaimComplete = CountDownLatch(1)
    val secondClaimAttempted = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(2)

    try {
      val firstFuture = executor.submit {
        transactionTemplate.executeWithoutResult {
          firstClaimedRows.set(
            documentRequestRepository.claimEligibleDocumentRequests(
              pendingState = DocumentRequestStatus.PENDING.name,
              batchSize = 1,
              cutoff = cutoff,
              now = firstClaimNow,
            ),
          )
          firstClaimComplete.countDown()
          assertThat(secondClaimAttempted.await(5, TimeUnit.SECONDS)).isTrue()
        }
      }

      val secondFuture = executor.submit {
        assertThat(firstClaimComplete.await(5, TimeUnit.SECONDS)).isTrue()
        transactionTemplate.executeWithoutResult {
          secondClaimedRows.set(
            documentRequestRepository.claimEligibleDocumentRequests(
              pendingState = DocumentRequestStatus.PENDING.name,
              batchSize = 1,
              cutoff = cutoff,
              now = secondClaimNow,
            ),
          )
        }
        secondClaimAttempted.countDown()
      }

      firstFuture.get(10, TimeUnit.SECONDS)
      secondFuture.get(10, TimeUnit.SECONDS)
    } finally {
      executor.shutdownNow()
    }

    assertThat(firstClaimedRows.get().map { it.id }).containsExactly(row.id)
    assertThat(secondClaimedRows.get()).isEmpty()
    assertThat(claimedAtFor(row.id)).isEqualTo(firstClaimNow)
  }

  private fun givenDocumentRequestRow(
    status: DocumentRequestStatus,
    claimedAt: Instant?,
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    attempts: Int = 0,
    lastError: String? = null,
  ): DocumentRequestEntity {
    val row = documentRequestRepository.saveAndFlush(
      DocumentRequestEntity(
        id = UUID.randomUUID(),
        type = DocumentType.CONTEXTUAL_REPORT,
        status = status,
        parameters = mapOf(
          "deviceId" to 123456,
          "from" to "2026-10-01T08:00:00Z",
          "to" to "2026-10-02T08:00:00Z",
        ),
        createdBy = "test",
        createdAt = createdAt,
        claimedAt = claimedAt,
        attempts = attempts,
        lastError = lastError,
        concurrencyVersion = 0L,
      ),
    )

    if (claimedAt != null) {
      jdbcTemplate.update(
        "update document_request set claimed_at = ? where id = ?",
        Timestamp.from(claimedAt),
        row.id,
      )
    }

    return row
  }

  private fun claimedAtFor(id: UUID): Instant? = jdbcTemplate.queryForObject(
    "select claimed_at from document_request where id = ?",
    { rs, _ ->
      rs.getTimestamp("claimed_at")?.toInstant()
    },
    id,
  )
}

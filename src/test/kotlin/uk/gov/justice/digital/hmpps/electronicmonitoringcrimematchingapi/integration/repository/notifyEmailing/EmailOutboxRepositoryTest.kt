package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.repository.notifyEmailing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.entity.EmailOutbox
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.EmailOutboxState
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.repository.notifyEmailing.EmailOutboxRepository
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@ActiveProfiles("integration")
class EmailOutboxRepositoryTest : IntegrationTestBase() {

  @Autowired
  lateinit var emailOutboxRepository: EmailOutboxRepository

  @Autowired
  lateinit var transactionTemplate: TransactionTemplate

  @BeforeEach
  fun setup() {
    emailOutboxRepository.deleteAll()
  }

  @Test
  fun `it should claim pending rows that are unclaimed or claimed before cutoff`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val eligibleUnclaimed = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    val eligibleStaleClaim = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = cutoff.minusMillis(1),
      createdAt = Instant.parse("2026-01-01T00:01:00Z"),
    )
    val ineligibleRecentClaim = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = cutoff.plusMillis(1),
      createdAt = Instant.parse("2026-01-01T00:02:00Z"),
    )
    val ineligiblePublished = givenOutboxRow(
      state = EmailOutboxState.PUBLISHED,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:03:00Z"),
    )

    val claimedRows = emailOutboxRepository.claimEligibleRows(
      pendingState = EmailOutboxState.PENDING.name,
      failedState = EmailOutboxState.FAILED.name,
      cutoff = cutoff,
      now = now,
      maxAttempts = 1,
    )

    assertThat(claimedRows.map { it.id }).containsExactlyInAnyOrder(
      eligibleUnclaimed.id,
      eligibleStaleClaim.id,
    )
    assertThat(claimedRows).allSatisfy { claimed ->
      assertThat(claimed.claimedAt).isEqualTo(now)
      assertThat(claimed.state).isEqualTo(EmailOutboxState.PENDING)
    }

    val persistedRows = emailOutboxRepository.findAllById(
      listOf(
        eligibleUnclaimed.id,
        eligibleStaleClaim.id,
        ineligibleRecentClaim.id,
        ineligiblePublished.id,
      ),
    ).associateBy { it.id }

    assertThat(persistedRows[eligibleUnclaimed.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[eligibleStaleClaim.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[ineligibleRecentClaim.id]!!.claimedAt).isEqualTo(cutoff.plusMillis(1))
    assertThat(persistedRows[ineligiblePublished.id]!!.claimedAt).isNull()
  }

  @Test
  fun `it should claim failed rows that are unclaimed or claimed before cutoff`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val eligibleUnclaimed = givenOutboxRow(
      state = EmailOutboxState.FAILED,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    val eligibleStaleClaim = givenOutboxRow(
      state = EmailOutboxState.FAILED,
      claimedAt = cutoff.minusMillis(1),
      createdAt = Instant.parse("2026-01-01T00:01:00Z"),
    )
    val ineligibleRecentClaim = givenOutboxRow(
      state = EmailOutboxState.FAILED,
      claimedAt = cutoff.plusMillis(1),
      createdAt = Instant.parse("2026-01-01T00:02:00Z"),
    )

    val claimedRows = emailOutboxRepository.claimEligibleRows(
      pendingState = EmailOutboxState.PENDING.name,
      failedState = EmailOutboxState.FAILED.name,
      cutoff = cutoff,
      now = now,
      maxAttempts = 1,
    )

    assertThat(claimedRows.map { it.id }).containsExactlyInAnyOrder(
      eligibleUnclaimed.id,
      eligibleStaleClaim.id,
    )
    assertThat(claimedRows).allSatisfy { claimed ->
      assertThat(claimed.claimedAt).isEqualTo(now)
      assertThat(claimed.state).isEqualTo(EmailOutboxState.FAILED)
    }

    val persistedRows = emailOutboxRepository.findAllById(
      listOf(
        eligibleUnclaimed.id,
        eligibleStaleClaim.id,
        ineligibleRecentClaim.id,
      ),
    ).associateBy { it.id }

    assertThat(persistedRows[eligibleUnclaimed.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[eligibleStaleClaim.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[ineligibleRecentClaim.id]!!.claimedAt).isEqualTo(cutoff.plusMillis(1))
  }

  @Test
  fun `it should not claim failed rows that exceed the max attempts or are dead`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val ineligibleMaxAttemptsReached = givenOutboxRow(
      state = EmailOutboxState.FAILED,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:02:00Z"),
      attempts = 1,
    )
    val ineligibleDead = givenOutboxRow(
      state = EmailOutboxState.DEAD,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    val claimedRows = emailOutboxRepository.claimEligibleRows(
      pendingState = EmailOutboxState.PENDING.name,
      failedState = EmailOutboxState.FAILED.name,
      cutoff = cutoff,
      now = now,
      maxAttempts = 1,
    )

    assertThat(claimedRows.size).isEqualTo(0)

    val persistedRows = emailOutboxRepository.findAllById(
      listOf(
        ineligibleMaxAttemptsReached.id,
        ineligibleDead.id,
      ),
    ).associateBy { it.id }

    assertThat(persistedRows[ineligibleMaxAttemptsReached.id]!!.claimedAt).isNull()
    assertThat(persistedRows[ineligibleDead.id]!!.claimedAt).isNull()
  }

  @Test
  fun `it should return no rows when there are no eligible rows`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val pendingRecentClaim = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = cutoff.plusMillis(5),
    )
    val published = givenOutboxRow(
      state = EmailOutboxState.PUBLISHED,
      claimedAt = null,
    )

    val claimedRows = emailOutboxRepository.claimEligibleRows(
      pendingState = EmailOutboxState.PENDING.name,
      failedState = EmailOutboxState.FAILED.name,
      cutoff = cutoff,
      now = now,
      maxAttempts = 1,
    )

    assertThat(claimedRows).isEmpty()

    val persistedRows = emailOutboxRepository.findAllById(
      listOf(pendingRecentClaim.id, published.id),
    ).associateBy { it.id }

    assertThat(persistedRows[pendingRecentClaim.id]!!.claimedAt).isEqualTo(cutoff.plusMillis(5))
    assertThat(persistedRows[published.id]!!.claimedAt).isNull()
  }

  @Test
  fun `it should claim at most four eligible rows`() {
    val now = Instant.parse("2026-01-01T00:10:00Z")
    val cutoff = now.minusSeconds(60)

    val eligibleOne = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    val eligibleTwo = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:01:00Z"),
    )
    val eligibleThree = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:02:00Z"),
    )
    val eligibleFour = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:02:00Z"),
    )
    val eligibleFive = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = null,
      createdAt = Instant.parse("2026-01-01T00:02:00Z"),
    )

    val claimedRows = emailOutboxRepository.claimEligibleRows(
      pendingState = EmailOutboxState.PENDING.name,
      failedState = EmailOutboxState.FAILED.name,
      cutoff = cutoff,
      now = now,
      maxAttempts = 1,
    )

    assertThat(claimedRows.map { it.id }).containsExactlyInAnyOrder(eligibleOne.id, eligibleTwo.id, eligibleThree.id, eligibleFour.id)

    val persistedRows = emailOutboxRepository.findAllById(
      listOf(eligibleOne.id, eligibleTwo.id, eligibleThree.id, eligibleFour.id, eligibleFive.id),
    ).associateBy { it.id }

    assertThat(persistedRows[eligibleOne.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[eligibleTwo.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[eligibleThree.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[eligibleFour.id]!!.claimedAt).isEqualTo(now)
    assertThat(persistedRows[eligibleFive.id]!!.claimedAt).isNull()
  }

  @Test
  fun `it should skip a row already locked by another claim transaction`() {
    val row = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = null,
    )
    val cutoff = Instant.parse("2026-01-01T00:09:00Z")
    val firstClaimNow = Instant.parse("2026-01-01T00:10:00Z")
    val secondClaimNow = Instant.parse("2026-01-01T00:10:30Z")

    val firstClaimedRows = AtomicReference<List<EmailOutbox>>(emptyList())
    val secondClaimedRows = AtomicReference<List<EmailOutbox>>(emptyList())
    val firstClaimComplete = CountDownLatch(1)
    val secondClaimAttempted = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(2)

    try {
      val firstFuture = executor.submit {
        transactionTemplate.executeWithoutResult {
          firstClaimedRows.set(
            emailOutboxRepository.claimEligibleRows(
              pendingState = EmailOutboxState.PENDING.name,
              failedState = EmailOutboxState.FAILED.name,
              cutoff = cutoff,
              now = firstClaimNow,
              maxAttempts = 1,
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
            emailOutboxRepository.claimEligibleRows(
              pendingState = EmailOutboxState.PENDING.name,
              failedState = EmailOutboxState.FAILED.name,
              cutoff = cutoff,
              now = secondClaimNow,
              maxAttempts = 1,
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

    val persistedRow = emailOutboxRepository.findById(row.id).orElseThrow()
    assertThat(persistedRow.claimedAt).isEqualTo(firstClaimNow)
  }

  @Test
  fun `it should complete a claimed row when claim timestamp and version both match`() {
    val claimedAt = Instant.parse("2026-01-01T00:10:00Z")
    val row = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = claimedAt,
    )

    val updated = emailOutboxRepository.completeClaimedRow(
      id = row.id,
      claimedAt = claimedAt,
      state = EmailOutboxState.PUBLISHED.name,
      attempts = 1,
      lastError = null,
      version = row.version,
    )

    assertThat(updated).isEqualTo(1)

    val persisted = emailOutboxRepository.findById(row.id).orElseThrow()
    assertThat(persisted.state).isEqualTo(EmailOutboxState.PUBLISHED)
    assertThat(persisted.attempts).isEqualTo(1)
    assertThat(persisted.lastError).isNull()
    assertThat(persisted.version).isEqualTo(1)
  }

  @Test
  fun `it should ignore completion when claim timestamp or version no longer match`() {
    val claimedAt = Instant.parse("2026-01-01T00:10:00Z")
    val row = givenOutboxRow(
      state = EmailOutboxState.PENDING,
      claimedAt = claimedAt,
    )

    val updatedWithWrongClaim = emailOutboxRepository.completeClaimedRow(
      id = row.id,
      claimedAt = claimedAt.plusSeconds(1),
      state = EmailOutboxState.FAILED.name,
      attempts = 1,
      lastError = "stale claim",
      version = row.version,
    )
    val updatedWithWrongVersion = emailOutboxRepository.completeClaimedRow(
      id = row.id,
      claimedAt = claimedAt,
      state = EmailOutboxState.FAILED.name,
      attempts = 1,
      lastError = "stale version",
      version = row.version + 1,
    )

    assertThat(updatedWithWrongClaim).isZero()
    assertThat(updatedWithWrongVersion).isZero()

    val persisted = emailOutboxRepository.findById(row.id).orElseThrow()
    assertThat(persisted.state).isEqualTo(EmailOutboxState.PENDING)
    assertThat(persisted.attempts).isZero()
    assertThat(persisted.lastError).isNull()
    assertThat(persisted.version).isEqualTo(0)
  }

  private fun givenOutboxRow(
    state: EmailOutboxState,
    claimedAt: Instant?,
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    attempts: Int = 0,
  ): EmailOutbox = emailOutboxRepository.save(
    EmailOutbox(
      payload = "{\"type\":\"CRIME_MATCHING_REQUEST\",\"crime_batch_id\":\"batch-id\"}",
      state = state,
      claimedAt = claimedAt,
      createdAt = createdAt,
      attempts = attempts,
    ),
  )
}

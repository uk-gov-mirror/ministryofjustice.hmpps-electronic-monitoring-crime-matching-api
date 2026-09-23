package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.repository.notifyEmailing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.entity.EmailOutbox
import java.time.Instant
import java.util.UUID

@Repository
interface EmailOutboxRepository : JpaRepository<EmailOutbox, UUID> {
  @Query(
    value = """
    with candidates as (
      select id
      from email_outbox
      where (state = :pendingState or (state = :failedState and attempts < :maxAttempts))
        and (claimed_at is null or claimed_at < :cutoff)
      order by created_at
      limit 4
      for update skip locked
    )
    update email_outbox p
    set claimed_at = :now
    from candidates c
    where p.id = c.id
    returning p.*
  """,
    nativeQuery = true,
  )
  fun claimEligibleRows(
    @Param("pendingState") pendingState: String,
    @Param("failedState") failedState: String,
    @Param("maxAttempts") maxAttempts: Int,
    @Param("cutoff") cutoff: Instant,
    @Param("now") now: Instant,
  ): List<EmailOutbox>

  @Modifying
  @Transactional
  @Query(
    value = """
      update email_outbox
      set state = :state,
          attempts = :attempts,
          last_error = :lastError,
          version = version + 1
      where id = :id
        and claimed_at = :claimedAt
        and version = :version
    """,
    nativeQuery = true,
  )
  fun completeClaimedRow(
    @Param("id") id: UUID,
    @Param("claimedAt") claimedAt: Instant,
    @Param("state") state: String,
    @Param("attempts") attempts: Int,
    @Param("lastError") lastError: String?,
    @Param("version") version: Long,
  ): Int
}

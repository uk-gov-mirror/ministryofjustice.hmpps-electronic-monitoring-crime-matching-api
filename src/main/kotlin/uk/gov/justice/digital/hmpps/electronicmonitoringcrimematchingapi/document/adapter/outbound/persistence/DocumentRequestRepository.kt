package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface DocumentRequestRepository : JpaRepository<DocumentRequestEntity, UUID> {
  @Query(
    value = """
    with candidates as (
      select id
      from document_request
      where (status = :pendingState)
        and (claimed_at is null or claimed_at < :cutoff)
      order by created_at
      limit :batchSize
      for update skip locked
    )
    update document_request dr
    set claimed_at = :now,
        concurrency_version = dr.concurrency_version + 1
    from candidates c
    where dr.id = c.id
    returning dr.*
  """,
    nativeQuery = true,
  )
  fun claimEligibleDocumentRequests(
    @Param("pendingState") pendingState: String,
    @Param("batchSize") batchSize: Int,
    @Param("cutoff") cutoff: Instant,
    @Param("now") now: Instant,
  ): Collection<DocumentRequestEntity>
}

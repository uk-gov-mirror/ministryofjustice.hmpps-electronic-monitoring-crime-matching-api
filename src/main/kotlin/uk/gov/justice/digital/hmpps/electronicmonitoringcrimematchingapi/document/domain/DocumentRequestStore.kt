package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain

import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationResult
import java.time.Instant
import java.util.UUID

interface DocumentRequestStore {
  fun save(
    documentRequest: DocumentRequest,
  ): DocumentRequest

  fun findAll(pagination: PaginationRequest): PaginationResult<DocumentRequest>

  fun findById(id: UUID): DocumentRequest?

  fun claimEligibleDocumentRequests(
    batchSize: Int,
    cutoff: Instant,
    now: Instant,
  ): Collection<DocumentRequest>
}

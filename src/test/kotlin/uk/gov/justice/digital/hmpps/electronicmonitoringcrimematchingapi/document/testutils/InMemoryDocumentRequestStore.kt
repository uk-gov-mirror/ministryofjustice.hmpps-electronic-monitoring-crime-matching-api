package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils

import org.springframework.dao.OptimisticLockingFailureException
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationResult
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentArtefact
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStore
import java.time.Instant
import java.util.UUID

class InMemoryDocumentRequestStore : DocumentRequestStore {
  private val requests = mutableMapOf<UUID, DocumentRequest>()

  override fun save(documentRequest: DocumentRequest): DocumentRequest {
    val existing = requests[documentRequest.id]

    val persisted = if (existing == null) {
      documentRequest.copy()
    } else {
      if (documentRequest.concurrencyVersion != existing.concurrencyVersion) {
        throw OptimisticLockingFailureException(
          "Document request ${documentRequest.id} has version ${documentRequest.concurrencyVersion} but store has ${existing.concurrencyVersion}",
        )
      }

      documentRequest.copy(concurrencyVersion = existing.concurrencyVersion + 1)
    }

    requests[persisted.id] = persisted
    return persisted.copy()
  }

  override fun findById(id: UUID): DocumentRequest? = requests[id]?.copy()

  override fun findAll(
    pagination: PaginationRequest,
  ): PaginationResult<DocumentRequest> {
    val sorted = requests.values.sortedWith(
      compareByDescending { it.createdAt },
    )

    val pageCount = (sorted.size + pagination.size - 1) / pagination.size

    return PaginationResult(
      data = sorted.drop(pagination.page * pagination.size)
        .take(pagination.size),
      pageCount = pageCount,
      pageNumber = pagination.page,
      pageSize = pagination.size,
    )
  }

  // This MUST mirror the logic in the Repository query otherwise the tests could be misleading
  override fun claimEligibleDocumentRequests(
    batchSize: Int,
    cutoff: Instant,
    now: Instant,
  ): Collection<DocumentRequest> = requests.values
    .sortedBy { it.createdAt }
    .filter {
      it.status == DocumentRequestStatus.PENDING && (it.claimedAt == null || it.claimedAt < cutoff)
    }
    .take(batchSize)
    .map { eligible ->
      val claimed = eligible.copy(
        claimedAt = now,
        concurrencyVersion = eligible.concurrencyVersion + 1,
      )
      requests[claimed.id] = claimed
      claimed
    }

  private fun DocumentRequest.copy(
    status: DocumentRequestStatus = this.status,
    claimedAt: Instant? = this.claimedAt,
    attempts: Int = this.attempts,
    lastError: String? = this.lastError,
    concurrencyVersion: Long = this.concurrencyVersion,
    artefact: DocumentArtefact? = this.artefact,
  ): DocumentRequest = DocumentRequest.rehydrate(
    id = id,
    type = type,
    parameters = parameters,
    status = status,
    createdBy = createdBy,
    createdAt = createdAt,
    claimedAt = claimedAt,
    attempts = attempts,
    lastError = lastError,
    concurrencyVersion = concurrencyVersion,
    artefact = artefact,
  )
}

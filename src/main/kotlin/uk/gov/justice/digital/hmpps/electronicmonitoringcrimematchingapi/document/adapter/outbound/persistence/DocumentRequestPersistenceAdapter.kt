package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.PaginationResult
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStore
import java.time.Instant
import java.util.UUID

@Component
class DocumentRequestPersistenceAdapter(
  private val repository: DocumentRequestRepository,
  private val mapper: DocumentRequestPersistenceMapper,
) : DocumentRequestStore {

  override fun findAll(pagination: PaginationRequest): PaginationResult<DocumentRequest> {
    val results = repository.findAll(
      PageRequest.of(pagination.page, pagination.size, Sort.by(Sort.Direction.DESC, "createdAt")),
    )

    return PaginationResult(
      data = results.content.map(mapper::toDomain),
      pageCount = results.totalPages,
      pageNumber = results.number,
      pageSize = results.size,
    )
  }

  override fun findById(id: UUID): DocumentRequest? = repository.findById(id)
    .orElse(null)
    ?.let(mapper::toDomain)

  override fun save(documentRequest: DocumentRequest): DocumentRequest {
    val entity = mapper.toEntity(documentRequest)
    val saved = repository.save(entity)
    return mapper.toDomain(saved)
  }

  override fun claimEligibleDocumentRequests(
    batchSize: Int,
    cutoff: Instant,
    now: Instant,
  ): Collection<DocumentRequest> {
    val entities = repository.claimEligibleDocumentRequests(DocumentRequestStatus.PENDING.name, batchSize, cutoff, now)
    return entities.map(mapper::toDomain)
  }
}

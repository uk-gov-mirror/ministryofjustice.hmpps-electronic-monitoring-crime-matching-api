package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "document_request")
class DocumentRequestEntity(
  @Id
  val id: UUID,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val type: DocumentType,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val status: DocumentRequestStatus,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  val parameters: Map<String, Any>,

  @Column(name = "created_by", nullable = false)
  val createdBy: String,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,

  @Column(name = "artefact_reference")
  val artefactReference: String? = null,

  @Column(name = "artefact_filename")
  val artefactFilename: String? = null,

  @Column(name = "artefact_content_type")
  val artefactContentType: String? = null,

  @Column(name = "claimed_at", nullable = true)
  val claimedAt: Instant?,

  @Column(name = "attempts", nullable = false)
  val attempts: Int,

  @Column(name = "last_error", nullable = true)
  val lastError: String?,

  @Version
  @Column(name = "concurrency_version", nullable = false)
  val concurrencyVersion: Long = 0,
)

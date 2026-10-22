package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.document.adapter.outbound.persistence

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence.DocumentRequestRepository
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStore
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.testutils.DocumentRequestParametersFixtures.givenContextualReportRequestParameters
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.integration.IntegrationTestBase
import java.time.Instant
import java.util.*

class DocumentRequestPersistenceAdapterTest : IntegrationTestBase() {
  @Autowired
  private lateinit var store: DocumentRequestStore

  @Autowired
  private lateinit var repository: DocumentRequestRepository

  @Autowired
  private lateinit var entityManager: EntityManager

  @Autowired
  private lateinit var transactionTemplate: TransactionTemplate

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Test
  @Transactional
  fun `it should rehydrate a document request with typed parameters`() {
    // Given a document request
    val request = DocumentRequest.create(
      parameters = givenContextualReportRequestParameters(),
      createdBy = "user123",
      createdAt = Instant.parse("2026-10-08T12:00:00.789Z"),
    )

    // When it is saved to the database
    store.save(request)

    // Flush and clear the persistence context to ensure we are retrieving from the database
    entityManager.flush()
    entityManager.clear()

    // And retrieved from the database
    val retrieved = store.findById(request.id)

    // Then it should be rehydrated into the domain model
    assertThat(retrieved).isNotNull

    assertThat(retrieved!!.id).isEqualTo(request.id)
    assertThat(retrieved.type).isEqualTo(request.type)
    assertThat(retrieved.parameters).isEqualTo(request.parameters)
    assertThat(retrieved.status).isEqualTo(request.status)
    assertThat(retrieved.createdBy).isEqualTo(request.createdBy)
    assertThat(retrieved.createdAt).isEqualTo(request.createdAt)
  }

  @Test
  fun `it should return null when a document request does not exist`() {
    val retrieved = store.findById(UUID.randomUUID())

    assertThat(retrieved).isNull()
  }

  @Test
  fun `it should reject worker A saving after worker B reclaims an expired lease`() {
    val request = DocumentRequest.create(
      parameters = givenContextualReportRequestParameters(),
      createdBy = "user123",
      createdAt = Instant.parse("2026-10-08T12:00:00.789Z"),
    )
    store.save(request)

    val workerAClaimedAt = Instant.parse("2026-10-08T12:05:00Z")
    val workerBClaimedAt = Instant.parse("2026-10-08T12:11:00Z")
    val leaseCutoff = workerBClaimedAt.minusSeconds(60)

    val workerARequest = transactionTemplate.execute {
      store.claimEligibleDocumentRequests(
        batchSize = 1,
        cutoff = workerAClaimedAt.minusSeconds(60),
        now = workerAClaimedAt,
      ).single()
    }

    val workerBRequest = transactionTemplate.execute {
      store.claimEligibleDocumentRequests(
        batchSize = 1,
        cutoff = leaseCutoff,
        now = workerBClaimedAt,
      ).single()
    }

    workerARequest.complete(mock())

    assertThatThrownBy {
      transactionTemplate.execute {
        store.save(workerARequest)
      }
    }
      .isInstanceOf(OptimisticLockingFailureException::class.java)

    workerBRequest.complete(mock())
    transactionTemplate.execute {
      store.save(workerBRequest)
    }

    val persisted = store.findById(request.id)
    assertThat(persisted).isNotNull
    assertThat(persisted!!.status).isEqualTo(DocumentRequestStatus.COMPLETED)
    assertThat(persisted.claimedAt).isEqualTo(workerBClaimedAt)
  }
}

package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.processnextdocumentrequest.ProcessDocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStore
import java.time.Clock
import java.time.Instant

@Component
class ProcessNextDocumentRequest(
  private val documentRequestStore: DocumentRequestStore,
  private val clock: Clock,
  private val processDocumentRequest: ProcessDocumentRequest,
) {
  fun process() {
    val now = Instant.now(clock)
    val cutoff = now.minusSeconds(60 * 5)
    val numReportsToGenerate = 1
    val documentRequests: Collection<DocumentRequest> = documentRequestStore.claimEligibleDocumentRequests(
      numReportsToGenerate,
      cutoff,
      now,
    )
    documentRequests.forEach { processDocumentRequest.process(it) }
  }
}

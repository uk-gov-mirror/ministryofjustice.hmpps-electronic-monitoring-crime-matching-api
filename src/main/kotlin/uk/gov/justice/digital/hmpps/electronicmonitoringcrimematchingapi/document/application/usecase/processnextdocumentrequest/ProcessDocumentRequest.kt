package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.processnextdocumentrequest

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.DocumentGeneratorRegistry
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentStore

// TODO(ACR-1101): Now that this is a sub use-case of ProcessNextDocumentRequest,
// update the ProcessDocumentRequestTest tests to cover ProcessNextDocumentRequest instead
@Component
class ProcessDocumentRequest(
  private val generatorRegistry: DocumentGeneratorRegistry,
  private val documentStore: DocumentStore,
) {
  fun process(documentRequest: DocumentRequest) {
    // TODO(ACR-1101): Consider removing this check, otherwise we're dependent on the the implementation of claimEligibleRows here
    check(documentRequest.status == DocumentRequestStatus.PENDING || documentRequest.status == DocumentRequestStatus.FAILED) {
      "DocumentRequest must be in PENDING or FAILED status"
    }

    val type = documentRequest.type
    val generatedDocument = generatorRegistry.get(type).generate(documentRequest)

    val documentArtefact = documentStore.save(generatedDocument)
    documentRequest.complete(documentArtefact)
  }
}

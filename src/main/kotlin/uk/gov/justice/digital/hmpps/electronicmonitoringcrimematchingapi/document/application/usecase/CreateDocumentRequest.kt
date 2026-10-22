package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.CreateDocumentRequestCommand
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.dto.DocumentRequestSummary
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.mapper.DocumentRequestMapper
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.parser.DocumentRequestParameterParser
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestStore
import java.time.Clock

@Service
class CreateDocumentRequest(
  private val store: DocumentRequestStore,
  private val mapper: DocumentRequestMapper,
  private val parser: DocumentRequestParameterParser,
  private val clock: Clock,
  private val processNextDocumentRequest: ProcessNextDocumentRequest,
) {
  fun create(
    request: CreateDocumentRequestCommand,
    user: String,
  ): DocumentRequestSummary {
    val parameters = parser.parse(
      request.type,
      request.parameters,
    )

    val documentRequest = DocumentRequest.create(
      parameters = parameters,
      createdAt = clock.instant(),
      createdBy = user,
    )

    val summary = mapper.toSummary(store.save(documentRequest))

    // TODO(ACR-1101): Is this the correct place?
    // We want to kick off process generation straight away, but don't want to block the thread handling the current request.
    try {
      processNextDocumentRequest.process()
    } catch (_: Exception) {
      // Do nothing, it will be retried by the scheduled job
      // TODO(ACR-1101): Add a scheduled job
    }
    return summary
  }
}

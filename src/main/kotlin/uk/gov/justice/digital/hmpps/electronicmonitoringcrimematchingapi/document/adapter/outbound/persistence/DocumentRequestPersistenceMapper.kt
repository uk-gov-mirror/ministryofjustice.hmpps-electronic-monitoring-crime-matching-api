package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.adapter.outbound.persistence

import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.parser.DocumentRequestParameterParser
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentArtefact
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequestParameters

@Component
class DocumentRequestPersistenceMapper(
  private val parser: DocumentRequestParameterParser,
  private val objectMapper: ObjectMapper,
) {
  fun toEntity(request: DocumentRequest): DocumentRequestEntity = DocumentRequestEntity(
    id = request.id,
    type = request.type,
    status = request.status,
    parameters = serializeParameters(request.parameters),
    createdBy = request.createdBy,
    createdAt = request.createdAt,
    artefactReference = request.artefact?.reference,
    artefactFilename = request.artefact?.filename,
    artefactContentType = request.artefact?.contentType,
    claimedAt = request.claimedAt,
    attempts = request.attempts,
    lastError = request.lastError,
    concurrencyVersion = request.concurrencyVersion,
  )

  fun toDomain(entity: DocumentRequestEntity): DocumentRequest = DocumentRequest.rehydrate(
    id = entity.id,
    type = entity.type,
    parameters = parser.parse(entity.type, entity.parameters),
    status = entity.status,
    createdBy = entity.createdBy,
    createdAt = entity.createdAt,
    artefact = toArtefact(entity),
    claimedAt = entity.claimedAt,
    attempts = entity.attempts,
    lastError = entity.lastError,
    concurrencyVersion = entity.concurrencyVersion,
  )

  private fun toArtefact(entity: DocumentRequestEntity): DocumentArtefact? = entity.artefactReference?.let {
    DocumentArtefact(
      reference = it,
      filename = entity.artefactFilename!!,
      contentType = entity.artefactContentType!!,
    )
  }

  private fun serializeParameters(
    parameters: DocumentRequestParameters,
  ): Map<String, Any> {
    val tree = objectMapper.valueToTree<ObjectNode>(parameters)
    tree.remove("type")

    return objectMapper.convertValue(
      tree,
      object : TypeReference<Map<String, Any>>() {},
    )
  }
}

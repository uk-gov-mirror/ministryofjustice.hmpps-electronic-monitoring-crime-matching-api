package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator

import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportConfig
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportCrimeDetails
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportDisclaimer
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportFurtherInformation
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportHeader
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportPersonDetails
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportReportPeriod
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportRequestDetails
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportSummary
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportWitnessStatement
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.DocumentType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.GeneratedDocument
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.SensitivityLabel
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.contextualreport.ContextualReportRequestParameters
import java.io.ByteArrayOutputStream

@Component
class ContextualReportDocxGenerator(
  private val documentSensitivityLabeller: DocumentSensitivityLabeller,
) : DocumentGenerator {
  override val type = DocumentType.CONTEXTUAL_REPORT

  private val config = ContextualReportConfig()

  override fun generate(documentRequest: DocumentRequest): GeneratedDocument {
    val document = WordprocessingMLPackage.createPackage()
    val parameters = documentRequest.parameters as ContextualReportRequestParameters

    documentSensitivityLabeller.apply(document, SensitivityLabel.OFFICIAL_SENSITIVE)

    ContextualReportHeader(documentRequest.createdAt, parameters, config).addTo(
      document = document,
    )

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportSummary().addTo(document)

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportPersonDetails(parameters, config).addTo(document)

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportCrimeDetails(parameters).addTo(document)

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportRequestDetails(parameters, config).addTo(document)

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportReportPeriod(parameters, config).addTo(document)

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportFurtherInformation(parameters).addTo(document)

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportDisclaimer().addTo(document)

    document.mainDocumentPart.addParagraphOfText("")

    ContextualReportWitnessStatement(documentRequest.createdAt, parameters, config).addTo(document)

    val byteStream = ByteArrayOutputStream()
    document.save(byteStream)

    return GeneratedDocument(
      filename = "placeholder.docx",
      contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      content = byteStream.toByteArray(),
    )
  }
}

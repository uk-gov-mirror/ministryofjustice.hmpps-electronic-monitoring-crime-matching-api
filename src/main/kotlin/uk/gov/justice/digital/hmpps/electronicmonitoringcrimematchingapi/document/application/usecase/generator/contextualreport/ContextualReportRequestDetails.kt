package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport

import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.contextualreport.ContextualReportRequestParameters

class ContextualReportRequestDetails(
  private val parameters: ContextualReportRequestParameters,
  private val config: ContextualReportConfig,
) {
  private val header = "Requested by:"
  private val constabulary = "Constabulary"
  private val from = "From"
  private val date = "Date"
  private val time = "Time"

  fun addTo(
    document: WordprocessingMLPackage,
  ) {
    val paragraphBuilder = ReportParagraphBuilder()
    val tableBuilder = ReportTableBuilder()
    val table = tableBuilder.create(columns = 2)

    tableBuilder.addHeader(
      table = table,
      content = listOf(
        paragraphBuilder.create(header, bold = true),
      ),
      columns = 2,
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(constabulary)),
        listOf(paragraphBuilder.create(parameters.reportRequest.policeForceArea)),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(from)),
        listOf(paragraphBuilder.create(parameters.reportRequest.requestedBy)),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(date)),
        listOf(paragraphBuilder.create(config.dateFormatter.format(parameters.reportRequest.requestedAt))),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(time)),
        listOf(paragraphBuilder.create(config.timeFormatter.format(parameters.reportRequest.requestedAt))),
      ),
    )

    document.mainDocumentPart.addObject(table)
  }
}

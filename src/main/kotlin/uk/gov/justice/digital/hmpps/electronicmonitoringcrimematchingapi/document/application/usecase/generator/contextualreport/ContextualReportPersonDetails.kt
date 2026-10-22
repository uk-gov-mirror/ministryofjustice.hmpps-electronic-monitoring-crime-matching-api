package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport

import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.contextualreport.ContextualReportRequestParameters

class ContextualReportPersonDetails(
  private val parameters: ContextualReportRequestParameters,
  private val config: ContextualReportConfig,
) {
  private val header = "Person:"
  private val name = "Full Name"
  private val dateOfBirth = "D.O.B."
  private val pncNumber = "PNC Number"
  private val specifiedAddress = "Specified Address"
  private val emsId = "EMS ID"
  private val offenderManager = "Offender Manager"

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
        listOf(paragraphBuilder.create(name)),
        listOf(paragraphBuilder.create(parameters.deviceWearer.name)),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(dateOfBirth)),
        listOf(paragraphBuilder.create(parameters.deviceWearer.dateOfBirth.format(config.dateFormatter))),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(pncNumber)),
        listOf(paragraphBuilder.create(parameters.deviceWearer.pncNumber)),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(specifiedAddress)),
        listOf(paragraphBuilder.create(parameters.deviceWearer.address)),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(emsId)),
        listOf(paragraphBuilder.create(parameters.deviceWearer.emsId)),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(paragraphBuilder.create(offenderManager)),
        listOf(paragraphBuilder.create(parameters.deviceWearer.offenderManager)),
      ),
    )

    document.mainDocumentPart.addObject(table)
  }
}

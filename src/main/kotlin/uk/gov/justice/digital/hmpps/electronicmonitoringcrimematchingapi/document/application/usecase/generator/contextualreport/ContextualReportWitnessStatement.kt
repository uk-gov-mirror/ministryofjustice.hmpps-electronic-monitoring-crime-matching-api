package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport

import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.domain.contextualreport.ContextualReportRequestParameters
import java.time.Instant

class ContextualReportWitnessStatement(
  private val createdAt: Instant,
  private val parameters: ContextualReportRequestParameters,
  private val config: ContextualReportConfig,
) {
  private val listBuilder = ReportListBuilder()
  private val paragraphBuilder = ReportParagraphBuilder()
  private val tableBuilder = ReportTableBuilder()

  private val title = "WITNESS STATEMENT"
  private val subtitle =
    "(CJ Act 1967, s.9; MC Act 1980, ss.5A (3) (a) and 5B; Criminal Procedure Rules 2005, Rule 27.1)"
  private val statementPrefix = "Statement of:"
  private val occupationPrefix = "Occupation:"

  private val declarationAge = "Age: Over 18"

  private val declarationText = "This statement consisting of [TODO - PageCount] pages, signed by me, " +
    "is true to the best of my knowledge and belief. I make it knowing that, if it is given in evidence, " +
    "I shall be liable to prosecution if I have willfully stated in it, " +
    "anything that I know to be false or do not believe to be true."

  private val statementIntro =
    "I am currently employed by the Ministry of Justice (MoJ) as a Manager " +
      "within the Electronic Monitoring Acquisitive Crime Hub (EMAC Hub)."

  private val statementDescription: String
    get() {
      val crime = this.parameters.crime
      val request = this.parameters.reportRequest
      val deviceWearer = this.parameters.deviceWearer

      return """
        Further to the statement dated [TODO-Get PA Created Date] regarding an allegation
        of ${crime.type} (Crime Reference No. ${crime.reference}).
        On ${config.isoDateFormatter.format(request.requestedAt)} the EMAC Hub received a request
        in connection with the above investigation for movement data for
        ${deviceWearer.name}, ${config.dateFormatter.format(deviceWearer.dateOfBirth)}.
        The request required the following:
      """.trimIndent().replace("\n", " ")
    }

  private val statementRequest: String
    get() {
      val name = parameters.deviceWearer.name
      val start = config.dateTimeFormatter.format(parameters.reportPeriod.start)
      val end = config.dateTimeFormatter.format(parameters.reportPeriod.end)

      return "Subject $name movements from $start to $end"
    }

  private val statementExhibitIntro = "As a result of this request, I further produce the following screen shot(s) " +
    "and data table which document the subject’s movements as requested above."

  private val statementExhibitList = listOf(
    "Exhibit EMAC/04 - Key for interpreting symbols on crime map.",
    "Exhibit EMAC/05 - Key for interpreting location data table.",
  )

  fun addTo(
    document: WordprocessingMLPackage,
  ) {
    val table = tableBuilder.create(columns = 2)

    tableBuilder.addHeader(
      table = table,
      content = listOf(
        paragraphBuilder.create(
          title,
          bold = true,
          centred = true,
        ),
        paragraphBuilder.create(
          subtitle,
          centred = true,
        ),
      ),
      columns = 2,
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        listOf(
          paragraphBuilder.create("$statementPrefix ${parameters.authorisingManager.name}"),
        ),
        listOf(
          paragraphBuilder.create("$occupationPrefix ${parameters.authorisingManager.occupation}"),
        ),
      ),
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        declaration(),
      ),
      columnSpan = 2,
    )

    tableBuilder.addRow(
      table = table,
      cells = listOf(
        statement(document),
      ),
      columnSpan = 2,
    )

    document.mainDocumentPart.addObject(table)
  }

  private fun declaration(): List<Any> = listOf(
    paragraphBuilder.create(declarationAge, spacingAfter = 120),
    paragraphBuilder.create(declarationText, spacingAfter = 120),
  ) + signature()

  private fun statement(
    document: WordprocessingMLPackage,
  ) = buildList {
    add(paragraphBuilder.create(statementIntro, spacingAfter = 120))
    add(paragraphBuilder.create(statementDescription))

    addAll(
      listBuilder.createNumberedList(
        document,
        listOf(statementRequest),
      ),
    )

    add(paragraphBuilder.create(statementExhibitIntro, spacingAfter = 120))

    addAll(
      listBuilder.createBulletedList(
        document,
        statementExhibitList,
      ),
    )

    addAll(signature())
  }

  private fun signature() = listOf(
    paragraphBuilder.create("Signature:", spacingAfter = 120),
    paragraphBuilder.create("Manager's name: ${parameters.authorisingManager.name}", spacingAfter = 120),
    paragraphBuilder.create("Date: ${config.isoDateFormatter.format(createdAt)}", spacingAfter = 120),
    paragraphBuilder.create(
      "Email: mojacquisitivecrimehub@justice.gov.uk (preferred communication method)",
      spacingAfter = 120,
    ),
    paragraphBuilder.create(
      "Address: 102 Petty France, Westminster, London, SW1H 9AJ",
    ),
  )
}

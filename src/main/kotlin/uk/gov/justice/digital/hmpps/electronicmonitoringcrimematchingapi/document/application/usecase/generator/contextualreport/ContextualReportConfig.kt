package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport

import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ContextualReportConfig(
  val timezone: ZoneId = ZoneId.of("Europe/London"),
) {
  val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")
      .withZone(timezone)

  val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")
      .withZone(timezone)

  val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
      .withZone(timezone)

  val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(timezone)
}

package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.document.application.usecase.generator.contextualreport.ContextualReportConfig
import java.time.Instant

class ContextualReportConfigTest {
  @Test
  fun `it should format dates and times in Europe London`() {
    val config = ContextualReportConfig()

    assertThat(
      config.dateTimeFormatter.format(Instant.parse("2026-01-01T12:00:00Z"))
    ).isEqualTo("12:00 01/01/2026")

    assertThat(
      config.dateTimeFormatter.format(Instant.parse("2026-07-01T12:00:00Z"))
    ).isEqualTo("13:00 01/07/2026")
  }
}
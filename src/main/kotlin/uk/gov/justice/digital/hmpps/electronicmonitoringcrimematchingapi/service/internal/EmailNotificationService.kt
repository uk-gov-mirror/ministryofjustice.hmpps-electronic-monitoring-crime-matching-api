package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.service.internal

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.config.notify.NotifyProperties
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.dto.CrimeRecordRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.EmailIngestionOutcome
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.NotifyEmailRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.entity.EmailOutbox
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.CrimeBatchEmailIngestionErrorType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.EmailOutboxState
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.IngestionStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.validation.EmailAttachmentIngestionError
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.repository.notifyEmailing.EmailOutboxRepository
import uk.gov.service.notify.NotificationClient
import java.time.Instant
import java.time.LocalDate

@Service
class EmailNotificationService(
  private val featureFlagService: FeatureFlagService,
  private val notifyClient: NotificationClient,
  private val properties: NotifyProperties,
  private val emailOutboxRepository: EmailOutboxRepository,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    const val NUM_ERRORS_TO_DISPLAY_IN_EMAIL_BODY = 5
    const val NOTIFY_EMAIL_REQUEST = "NOTIFY_EMAIL_REQUEST"
    const val MAX_EMAIL_ATTEMPTS = 3
  }

  private val log = LoggerFactory.getLogger(this::class.java)

  fun sendEmails() {
    val claimedRows = claimEligibleOutboxRows()
    claimedRows.forEach { row ->
      try {
        val payloadEvent = objectMapper.readValue(row.payload, NotifyEmailRequest::class.java)

        val templateId = emailTemplateId(payloadEvent.ingestionStatus)

        val personalisation = buildPersonalisation(
          status = payloadEvent.ingestionStatus,
          fileName = payloadEvent.fileName,
          batchId = payloadEvent.batchId,
          policeForce = payloadEvent.policeForce,
          errorType = payloadEvent.errorType,
          records = payloadEvent.records,
          errors = payloadEvent.errors,
          recordCount = payloadEvent.recordCount,
        )
        sendEmail(
          templateId = templateId,
          emailAddress = payloadEvent.emailAddress,
          personalisation = personalisation,
          reference = payloadEvent.reference,
        )
        completeClaimedRow(row, row.attempts + 1, EmailOutboxState.PUBLISHED, null)
      } catch (e: Throwable) {
        completeClaimedRow(
          row,
          row.attempts + 1,
          if (row.attempts + 1 < MAX_EMAIL_ATTEMPTS) EmailOutboxState.FAILED else EmailOutboxState.DEAD,
          e.message,
        )
      }
    }
  }

  private fun sendEmail(
    templateId: String,
    emailAddress: String,
    personalisation: Map<String, Any>,
    reference: String,
  ) {
    notifyClient.sendEmail(
      templateId,
      emailAddress,
      personalisation,
      reference,
    )
  }

  @Transactional
  fun claimEligibleOutboxRows(): List<EmailOutbox> {
    val now = Instant.now()
    val cutoff = now.minusSeconds(60)

    return emailOutboxRepository.claimEligibleRows(
      pendingState = EmailOutboxState.PENDING.name,
      failedState = EmailOutboxState.FAILED.name,
      maxAttempts = MAX_EMAIL_ATTEMPTS,
      cutoff = cutoff,
      now = now,
    )
  }

  @Transactional
  fun createEmailOutboxRequest(ingestionOutcome: EmailIngestionOutcome) {
    if (!properties.enabled) {
      return
    }

    val emailAddresses = buildList {
      add(ingestionOutcome.emailData.sender)
      if (featureFlagService.policeConfirmationEmailsEnabled()) {
        add(ingestionOutcome.emailData.originalSender)
      }
    }

    for (emailAddress in emailAddresses) {
      val payloadEvent = objectMapper.writeValueAsString(
        NotifyEmailRequest(
          type = NOTIFY_EMAIL_REQUEST,
          emailAddress = emailAddress,
          reference = ingestionOutcome.batchId,
          ingestionStatus = ingestionOutcome.ingestionStatus,
          fileName = ingestionOutcome.emailData.attachments.firstOrNull()?.name ?: "Invalid File",
          batchId = ingestionOutcome.batchId,
          policeForce = ingestionOutcome.policeForce,
          errorType = ingestionOutcome.errorType,
          records = ingestionOutcome.records,
          errors = ingestionOutcome.errors,
          recordCount = ingestionOutcome.recordCount,
        ),
      )
      emailOutboxRepository.save(
        EmailOutbox(
          payload = payloadEvent,
          state = EmailOutboxState.PENDING,
        ),
      )
    }
  }

  private fun completeClaimedRow(
    row: EmailOutbox,
    attempts: Int,
    state: EmailOutboxState,
    lastError: String?,
  ) {
    val claimedAt = row.claimedAt
    if (claimedAt == null) {
      log.warn("Skipping EmailOutbox completion for row {} because claimedAt is null", row.id)
      return
    }

    val updateCount = emailOutboxRepository.completeClaimedRow(
      id = row.id,
      claimedAt = claimedAt,
      state = state.name,
      attempts = attempts,
      lastError = lastError,
      version = row.version,
    )

    if (updateCount == 0) {
      log.warn("EmailOutbox row {} completion skipped: claim/version no longer owned", row.id)
    }
  }

  private fun emailTemplateId(
    status: IngestionStatus,
  ): String = when (status) {
    IngestionStatus.FAILED -> properties.failedIngestionTemplateId
    IngestionStatus.SUCCESSFUL -> properties.successfulIngestionTemplateId
    IngestionStatus.PARTIAL -> properties.partialIngestionTemplateId
    IngestionStatus.ERROR -> properties.errorIngestionTemplateId
    IngestionStatus.UNKNOWN -> properties.failedIngestionTemplateId
  }

  private fun List<CrimeRecordRequest>.toCsv(): String = buildString {
    this@toCsv.forEach { r ->
      appendCsvRow(
        r.policeForce.label,
        r.crimeTypeId.name,
        r.crimeTypeId.value,
        r.batchId,
        r.crimeReference,
        r.crimeDateTimeFrom,
        r.crimeDateTimeTo,
        r.easting,
        r.northing,
        r.latitude,
        r.longitude,
        r.crimeText,
      )
    }
  }

  private fun StringBuilder.appendCsvRow(vararg fields: Any?) {
    append(
      fields.joinToString(",") { it?.toString().orEmpty() },
    )
    append("\n")
  }

  private fun buildInLineErrorSummary(errors: List<EmailAttachmentIngestionError>): String {
    val numTruncatedErrors = errors.size - NUM_ERRORS_TO_DISPLAY_IN_EMAIL_BODY
    val truncationSuffix = when (numTruncatedErrors) {
      1 -> "\n...and 1 more error"
      in 2..Int.MAX_VALUE -> "\n...and $numTruncatedErrors more errors"
      else -> ""
    }
    return errors.take(NUM_ERRORS_TO_DISPLAY_IN_EMAIL_BODY).joinToString("\n") { error ->
      "Row ${error.rowNumber}: ${error.errorType.message}" +
        (if (error.field != null) " (${error.field})" else "")
    } + truncationSuffix
  }

  private fun buildErrorCsv(errors: List<EmailAttachmentIngestionError>): ByteArray = buildString {
    appendLine("Reference,Status,Error,Action Required")
    errors.forEach { error ->
      appendLine(
        "${error.crimeReference ?: ""},Failed, ${error.errorType.message},${error.errorType.requiredAction}",
      )
    }
  }.toByteArray(Charsets.UTF_8)

  private fun buildPersonalisation(
    status: IngestionStatus,
    fileName: String,
    batchId: String,
    policeForce: String,
    errorType: CrimeBatchEmailIngestionErrorType,
    errors: List<EmailAttachmentIngestionError>,
    records: List<CrimeRecordRequest>,
    recordCount: Int = 0,
  ): Map<String, Any> {
    val personalisation = hashMapOf<String, Any>()
    personalisation["fileName"] = fileName
    personalisation["ingestionDate"] = LocalDate.now().toString()
    personalisation["batchId"] = batchId
    personalisation["policeForce"] = policeForce

    when (status) {
      IngestionStatus.SUCCESSFUL -> {
        personalisation["linkToFile"] = NotificationClient.prepareUpload(records.toCsv().toByteArray(), fileName)
      }
      IngestionStatus.FAILED -> {
        personalisation["errorSummary"] = errorType.message
        personalisation["totalCount"] = recordCount
      }
      IngestionStatus.PARTIAL, IngestionStatus.ERROR -> {
        personalisation["errorSummary"] = buildInLineErrorSummary(errors)
        personalisation["totalCount"] = recordCount
        personalisation["successCount"] = records.size
        personalisation["failedCount"] = recordCount - records.size
        personalisation["linkToFile"] = NotificationClient.prepareUpload(buildErrorCsv(errors), "ingestion_errors.csv")
      }
      IngestionStatus.UNKNOWN -> {}
    }

    return personalisation
  }
}

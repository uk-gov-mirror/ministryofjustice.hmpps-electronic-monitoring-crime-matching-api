package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.service.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.mail.util.ByteArrayDataSource
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.config.notify.NotifyProperties
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.helpers.EmailData
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.EmailIngestionOutcome
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.NotifyEmailRequest
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.entity.EmailOutbox
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.CrimeBatchEmailAttachmentIngestionErrorType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.CrimeBatchEmailIngestionErrorType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.EmailOutboxState
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.IngestionStatus
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.PoliceForce
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.PublishMatchingState
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.validation.EmailAttachmentIngestionError
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.repository.notifyEmailing.EmailOutboxRepository
import uk.gov.service.notify.NotificationClient
import java.time.Instant
import java.time.LocalDate
import java.util.Date

@ActiveProfiles("test")
class EmailNotificationServiceTest {
  private lateinit var service: EmailNotificationService
  private lateinit var notifyClient: NotificationClient
  private lateinit var emailOutboxRepository: EmailOutboxRepository
  private val mapper: ObjectMapper = jacksonObjectMapper()
  private val notifyProperties: NotifyProperties = mock()
  private val featureFlagService: FeatureFlagService = mock()

  @BeforeEach
  fun setup() {
    whenever(notifyProperties.successfulIngestionTemplateId).thenReturn("templateId")
    whenever(notifyProperties.failedIngestionTemplateId).thenReturn("failedTemplateId")
    whenever(notifyProperties.partialIngestionTemplateId).thenReturn("partialTemplateId")
    whenever(notifyProperties.errorIngestionTemplateId).thenReturn("errorTemplateId")
    whenever(featureFlagService.policeConfirmationEmailsEnabled()).thenReturn(true)
    notifyClient = Mockito.mock(NotificationClient::class.java)
    emailOutboxRepository = Mockito.mock(EmailOutboxRepository::class.java)
    service = EmailNotificationService(featureFlagService, notifyClient, notifyProperties, emailOutboxRepository, mapper)
  }

  @Test
  fun `it should send a successful ingestion email when notify is enabled`() {
    whenever(notifyProperties.enabled).thenReturn(true)
    val attachment = ByteArrayDataSource("data", "message/rfc822")
    attachment.name = "attachment.csv"

    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = listOf(attachment),
    )

    val uploadFile = JSONObject()

    val personalisation = mutableMapOf(
      "fileName" to "attachment.csv",
      "ingestionDate" to LocalDate.now().toString(),
      "batchId" to "batchId",
      "policeForce" to "BEDFORDSHIRE",
      "linkToFile" to uploadFile,
    )

    mockStatic(NotificationClient::class.java).use { staticMock ->
      staticMock
        .`when`<Any> {
          NotificationClient.prepareUpload(
            any(),
            any(),
          )
        }
        .thenReturn(uploadFile)

      val ingestionOutcome = EmailIngestionOutcome(
        batchId = "batchId",
        policeForce = "BEDFORDSHIRE",
        emailData = emailData,
        ingestionStatus = IngestionStatus.SUCCESSFUL,
      )

      val outboxCaptor = argumentCaptor<EmailOutbox>()

      service.createEmailOutboxRequest(ingestionOutcome)
      verify(emailOutboxRepository, times(2)).save(outboxCaptor.capture())
      val claimedRows = outboxCaptor.allValues
      whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)

      service.sendEmails()
    }

    verify(notifyClient, times(1)).sendEmail("templateId", "sender", personalisation, "batchId")
    verify(notifyClient, times(1)).sendEmail("templateId", "originalSender", personalisation, "batchId")
  }

  @Test
  fun `it should not send a successful ingestion email when notify is not enabled`() {
    val attachment = ByteArrayDataSource("data", "message/rfc822")
    attachment.name = "attachment.csv"
    val batchId = "batchId"

    val personalisation = mutableMapOf(
      "fileName" to "attachment.csv",
      "ingestionDate" to LocalDate.now().toString(),
      "batchId" to batchId,
      "policeForce" to "BEDFORDSHIRE",
    )

    assertDoesNotThrow { service.sendEmails() }
    verify(notifyClient, times(0)).sendEmail("templateId", "sender", personalisation, batchId)
  }

  @Test
  fun `it should send a failed ingestion email when notify is enabled`() {
    whenever(notifyProperties.enabled).thenReturn(true)

    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = emptyList(),
    )

    val personalisation = mapOf(
      "fileName" to "Invalid File",
      "ingestionDate" to LocalDate.now().toString(),
      "batchId" to "Unknown due to an error",
      "policeForce" to "Unknown due to an error",
      "errorSummary" to CrimeBatchEmailIngestionErrorType.INVALID_ATTACHMENT.message,
      "totalCount" to 0,
    )

    val ingestionOutcome = EmailIngestionOutcome(
      batchId = "Unknown due to an error",
      policeForce = "Unknown due to an error",
      emailData = emailData,
      errorType = CrimeBatchEmailIngestionErrorType.INVALID_ATTACHMENT,
      ingestionStatus = IngestionStatus.FAILED,
    )

    assertDoesNotThrow {
      val outboxCaptor = argumentCaptor<EmailOutbox>()

      service.createEmailOutboxRequest(ingestionOutcome)
      verify(emailOutboxRepository, times(2)).save(outboxCaptor.capture())
      val claimedRows = outboxCaptor.allValues
      whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)

      service.sendEmails()
    }

    verify(notifyClient, times(1)).sendEmail("failedTemplateId", "sender", personalisation, "Unknown due to an error")
    verify(notifyClient, times(1)).sendEmail("failedTemplateId", "originalSender", personalisation, "Unknown due to an error")
  }

  @Test
  fun `it should send a partial ingestion email with errorSummary and CSV attachment when notify is enabled`() {
    whenever(notifyProperties.enabled).thenReturn(true)

    val errors = (1..7).map { i ->
      EmailAttachmentIngestionError(
        rowNumber = i.toLong(),
        crimeReference = "CRI0000000$i",
        crimeTypeId = null,
        errorType = CrimeBatchEmailAttachmentIngestionErrorType.INVALID_CRIME_TYPE,
        field = "crimeTypeId",
        value = "INVALID_$i",
      )
    }
    val attachment = ByteArrayDataSource("data", "message/rfc822")
    attachment.name = "attachment.csv"

    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = listOf(attachment),
    )

    val uploadFile = JSONObject()

    val batchId = "batchId"

    mockStatic(NotificationClient::class.java).use { staticMock ->
      staticMock
        .`when`<Any> {
          NotificationClient.prepareUpload(
            any(),
            any(),
          )
        }
        .thenReturn(uploadFile)

      val ingestionOutcome = EmailIngestionOutcome(
        batchId = batchId,
        policeForce = PoliceForce.METROPOLITAN.name,
        emailData = emailData,
        errors = errors,
        ingestionStatus = IngestionStatus.PARTIAL,
        recordCount = 10,
      )

      val outboxCaptor = argumentCaptor<EmailOutbox>()

      service.createEmailOutboxRequest(ingestionOutcome)
      verify(emailOutboxRepository, times(2)).save(outboxCaptor.capture())
      val claimedRows = outboxCaptor.allValues
      whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)

      service.sendEmails()
      // Now that we have an email outbox, we need to build the personalisation twice and can't share it between two calls to sendEmail:
      staticMock.verify({ NotificationClient.prepareUpload(any(), any()) }, times(2))

      verify(notifyClient, times(1)).sendEmail(eq("partialTemplateId"), eq("sender"), any(), eq(batchId))
      verify(notifyClient, times(1)).sendEmail(eq("partialTemplateId"), eq("originalSender"), any(), eq(batchId))
    }
  }

  @Test
  fun `it should include a truncation message in partial ingestion emails when there are more errors than displayed`() {
    whenever(notifyProperties.enabled).thenReturn(true)

    val errors = (1..7).map { i ->
      EmailAttachmentIngestionError(
        rowNumber = i.toLong(),
        crimeReference = "CRI0000000$i",
        crimeTypeId = null,
        errorType = CrimeBatchEmailAttachmentIngestionErrorType.INVALID_CRIME_TYPE,
        field = "crimeTypeId",
        value = "INVALID_$i",
      )
    }
    val attachment = ByteArrayDataSource("data", "message/rfc822")
    attachment.name = "attachment.csv"

    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = listOf(attachment),
    )

    val uploadFile = JSONObject()
    val batchId = "batchId"

    val personalisation = mapOf(
      "fileName" to "attachment.csv",
      "ingestionDate" to LocalDate.now().toString(),
      "batchId" to batchId,
      "policeForce" to PoliceForce.METROPOLITAN.name,
      "errorSummary" to """
        Row 1: Field must be a valid ENUM value (crimeTypeId)
        Row 2: Field must be a valid ENUM value (crimeTypeId)
        Row 3: Field must be a valid ENUM value (crimeTypeId)
        Row 4: Field must be a valid ENUM value (crimeTypeId)
        Row 5: Field must be a valid ENUM value (crimeTypeId)
        ...and 2 more errors
      """.trimIndent(),
      "totalCount" to 10,
      "successCount" to 0,
      "failedCount" to 10,
      "linkToFile" to uploadFile,
    )

    mockStatic(NotificationClient::class.java).use { staticMock ->
      staticMock
        .`when`<Any> {
          NotificationClient.prepareUpload(
            any(),
            any(),
          )
        }
        .thenReturn(uploadFile)

      val ingestionOutcome = EmailIngestionOutcome(
        batchId = batchId,
        policeForce = PoliceForce.METROPOLITAN.name,
        emailData = emailData,
        errors = errors,
        ingestionStatus = IngestionStatus.PARTIAL,
        recordCount = 10,
      )

      val outboxCaptor = argumentCaptor<EmailOutbox>()

      service.createEmailOutboxRequest(ingestionOutcome)
      verify(emailOutboxRepository, times(2)).save(outboxCaptor.capture())
      val claimedRows = outboxCaptor.allValues
      whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)

      service.sendEmails()
      // Now that we have an email outbox, we need to build the personalisation twice and can't share it between two calls to sendEmail:
      staticMock.verify({ NotificationClient.prepareUpload(any(), any()) }, times(2))
    }

    verify(notifyClient, times(1)).sendEmail("partialTemplateId", "sender", personalisation, batchId)
    verify(notifyClient, times(1)).sendEmail("partialTemplateId", "originalSender", personalisation, batchId)
  }

  @Test
  fun `it should include a truncation message in error ingestion emails when there are more errors than displayed`() {
    whenever(notifyProperties.enabled).thenReturn(true)

    val errors = (1..7).map { i ->
      EmailAttachmentIngestionError(
        rowNumber = i.toLong(),
        crimeReference = "CRI0000000$i",
        crimeTypeId = null,
        errorType = CrimeBatchEmailAttachmentIngestionErrorType.INVALID_CRIME_TYPE,
        field = "crimeTypeId",
        value = "INVALID_$i",
      )
    }
    val attachment = ByteArrayDataSource("data", "message/rfc822")
    attachment.name = "attachment.csv"

    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = listOf(attachment),
    )

    val uploadFile = JSONObject()
    val batchId = "batchId"

    val personalisation = mapOf(
      "fileName" to "attachment.csv",
      "ingestionDate" to LocalDate.now().toString(),
      "batchId" to batchId,
      "policeForce" to PoliceForce.METROPOLITAN.name,
      "errorSummary" to """
        Row 1: Field must be a valid ENUM value (crimeTypeId)
        Row 2: Field must be a valid ENUM value (crimeTypeId)
        Row 3: Field must be a valid ENUM value (crimeTypeId)
        Row 4: Field must be a valid ENUM value (crimeTypeId)
        Row 5: Field must be a valid ENUM value (crimeTypeId)
        ...and 2 more errors
      """.trimIndent(),
      "totalCount" to 10,
      "successCount" to 0,
      "failedCount" to 10,
      "linkToFile" to uploadFile,
    )

    mockStatic(NotificationClient::class.java).use { staticMock ->
      staticMock
        .`when`<Any> {
          NotificationClient.prepareUpload(
            any(),
            any(),
          )
        }
        .thenReturn(uploadFile)

      val ingestionOutcome = EmailIngestionOutcome(
        batchId = batchId,
        policeForce = PoliceForce.METROPOLITAN.name,
        emailData = emailData,
        errors = errors,
        ingestionStatus = IngestionStatus.ERROR,
        recordCount = 10,
      )

      val outboxCaptor = argumentCaptor<EmailOutbox>()

      service.createEmailOutboxRequest(ingestionOutcome)
      verify(emailOutboxRepository, times(2)).save(outboxCaptor.capture())
      val claimedRows = outboxCaptor.allValues
      whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)

      service.sendEmails()
      // Now that we have an email outbox, we need to build the personalisation twice and can't share it between two calls to sendEmail:
      staticMock.verify({ NotificationClient.prepareUpload(any(), any()) }, times(2))
    }

    verify(notifyClient, times(1)).sendEmail("errorTemplateId", "sender", personalisation, batchId)
    verify(notifyClient, times(1)).sendEmail("errorTemplateId", "originalSender", personalisation, batchId)
  }

  @Test
  fun `it should send an error ingestion email with errorSummary and CSV attachment when notify is enabled`() {
    whenever(notifyProperties.enabled).thenReturn(true)

    val errors = listOf(
      EmailAttachmentIngestionError(
        rowNumber = 1,
        crimeReference = "CRI00000001",
        crimeTypeId = null,
        errorType = CrimeBatchEmailAttachmentIngestionErrorType.INVALID_CRIME_TYPE,
        field = "crimeTypeId",
        value = "",
      ),
    )
    val attachment = ByteArrayDataSource("data", "message/rfc822")
    attachment.name = "attachment.csv"

    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = listOf(attachment),
    )

    val uploadFile = JSONObject()

    val batchId = "batchId"

    mockStatic(NotificationClient::class.java).use { staticMock ->
      staticMock
        .`when`<Any> {
          NotificationClient.prepareUpload(
            any(),
            any(),
          )
        }
        .thenReturn(uploadFile)

      val ingestionOutcome = EmailIngestionOutcome(
        batchId = batchId,
        policeForce = PoliceForce.METROPOLITAN.name,
        emailData = emailData,
        errors = errors,
        ingestionStatus = IngestionStatus.ERROR,
        recordCount = 1,
      )

      val outboxCaptor = argumentCaptor<EmailOutbox>()

      service.createEmailOutboxRequest(ingestionOutcome)
      verify(emailOutboxRepository, times(2)).save(outboxCaptor.capture())
      val claimedRows = outboxCaptor.allValues
      whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)

      service.sendEmails()
      // Now that we have an email outbox, we need to build the personalisation twice and can't share it between two calls to sendEmail:
      staticMock.verify({ NotificationClient.prepareUpload(any(), any()) }, times(2))

      verify(notifyClient, times(1)).sendEmail(eq("errorTemplateId"), eq("sender"), any(), eq(batchId))
      verify(notifyClient, times(1)).sendEmail(eq("errorTemplateId"), eq("originalSender"), any(), eq(batchId))
    }
  }

  @Test
  fun `it should not send an email to the original sender when the send police email flag is false`() {
    whenever(notifyProperties.enabled).thenReturn(true)
    whenever(featureFlagService.policeConfirmationEmailsEnabled()).thenReturn(false)

    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = emptyList(),
    )

    val personalisation = mapOf(
      "fileName" to "Invalid File",
      "ingestionDate" to LocalDate.now().toString(),
      "batchId" to "Unknown due to an error",
      "policeForce" to "Unknown due to an error",
      "errorSummary" to CrimeBatchEmailIngestionErrorType.INVALID_ATTACHMENT.message,
      "totalCount" to 0,
    )

    val ingestionOutcome = EmailIngestionOutcome(
      batchId = "Unknown due to an error",
      policeForce = "Unknown due to an error",
      emailData = emailData,
      errorType = CrimeBatchEmailIngestionErrorType.INVALID_ATTACHMENT,
      ingestionStatus = IngestionStatus.FAILED,
    )

    assertDoesNotThrow {
      val outboxCaptor = argumentCaptor<EmailOutbox>()

      service.createEmailOutboxRequest(ingestionOutcome)
      verify(emailOutboxRepository, times(1)).save(outboxCaptor.capture())
      val claimedRows = outboxCaptor.allValues
      whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)

      service.sendEmails()
    }

    verify(notifyClient, times(1)).sendEmail("failedTemplateId", "sender", personalisation, "Unknown due to an error")
    verify(notifyClient, times(0)).sendEmail("failedTemplateId", "originalSender", personalisation, "Unknown due to an error")
  }

  @Test
  fun `it should save the state of the outbox row as FAILED if an error occurs`() {
    whenever(notifyClient.sendEmail(any(), any(), any(), any())).thenThrow(RuntimeException("Notify error"))
    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = emptyList(),
    )

    val ingestionOutcome = EmailIngestionOutcome(
      batchId = "Unknown due to an error",
      policeForce = "Unknown due to an error",
      emailData = emailData,
      errorType = CrimeBatchEmailIngestionErrorType.INVALID_ATTACHMENT,
      ingestionStatus = IngestionStatus.FAILED,
    )

    val claimedRows = listOf(
      EmailOutbox(
        payload = mapper.writeValueAsString(
          NotifyEmailRequest(
            type = "NOTIFY_EMAIL_REQUEST",
            emailAddress = ingestionOutcome.emailData.sender,
            reference = ingestionOutcome.batchId,
            ingestionStatus = ingestionOutcome.ingestionStatus,
            fileName = "example.csv",
            batchId = ingestionOutcome.batchId,
            policeForce = ingestionOutcome.policeForce,
            errorType = ingestionOutcome.errorType,
            records = ingestionOutcome.records,
            errors = ingestionOutcome.errors,
            recordCount = ingestionOutcome.recordCount,
          ),
        ),
        state = EmailOutboxState.PENDING,
        attempts = 0,
        claimedAt = Instant.now(),
      ),
    )
    whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)
    assertDoesNotThrow {
      service.sendEmails()
    }
    verify(emailOutboxRepository, times(1)).completeClaimedRow(any(), any(), eq(EmailOutboxState.FAILED.name), eq(1), any(), eq(0))
  }

  @Test
  fun `it should transition the state of the outbox row to DEAD if the max attempts is reached`() {
    whenever(notifyClient.sendEmail(any(), any(), any(), any())).thenThrow(RuntimeException("Notify error"))
    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = emptyList(),
    )

    val ingestionOutcome = EmailIngestionOutcome(
      batchId = "Unknown due to an error",
      policeForce = "Unknown due to an error",
      emailData = emailData,
      errorType = CrimeBatchEmailIngestionErrorType.INVALID_ATTACHMENT,
      ingestionStatus = IngestionStatus.FAILED,
    )

    val claimedRows = listOf(
      EmailOutbox(
        payload = mapper.writeValueAsString(
          NotifyEmailRequest(
            type = "NOTIFY_EMAIL_REQUEST",
            emailAddress = ingestionOutcome.emailData.sender,
            reference = ingestionOutcome.batchId,
            ingestionStatus = ingestionOutcome.ingestionStatus,
            fileName = "example.csv",
            batchId = ingestionOutcome.batchId,
            policeForce = ingestionOutcome.policeForce,
            errorType = ingestionOutcome.errorType,
            records = ingestionOutcome.records,
            errors = ingestionOutcome.errors,
            recordCount = ingestionOutcome.recordCount,
          ),
        ),
        state = EmailOutboxState.FAILED,
        attempts = EmailNotificationService.MAX_EMAIL_ATTEMPTS - 1,
        claimedAt = Instant.now(),
      ),
    )
    whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)
    assertDoesNotThrow {
      service.sendEmails()
    }

    val stateCaptor = argumentCaptor<String>()
    val attemptsCaptor = argumentCaptor<Int>()

    verify(emailOutboxRepository, times(1)).completeClaimedRow(any(), any(), stateCaptor.capture(), attemptsCaptor.capture(), any(), eq(0))
    assertEquals(PublishMatchingState.DEAD.name, stateCaptor.firstValue)
    assertEquals(EmailNotificationService.MAX_EMAIL_ATTEMPTS, attemptsCaptor.firstValue)
  }

  @Test
  fun `it should update the state of the outbox row to PUBLISHED if the publish happens successfully`() {
    val emailData = EmailData(
      sender = "sender",
      originalSender = "originalSender",
      subject = "subject",
      sentAt = Date.from(Instant.now()),
      attachments = emptyList(),
    )

    val ingestionOutcome = EmailIngestionOutcome(
      batchId = "Unknown due to an error",
      policeForce = "Unknown due to an error",
      emailData = emailData,
      errorType = CrimeBatchEmailIngestionErrorType.INVALID_ATTACHMENT,
      ingestionStatus = IngestionStatus.FAILED,
    )

    val claimedRows = listOf(
      EmailOutbox(
        payload = mapper.writeValueAsString(
          NotifyEmailRequest(
            type = "NOTIFY_EMAIL_REQUEST",
            emailAddress = ingestionOutcome.emailData.sender,
            reference = ingestionOutcome.batchId,
            ingestionStatus = ingestionOutcome.ingestionStatus,
            fileName = "example.csv",
            batchId = ingestionOutcome.batchId,
            policeForce = ingestionOutcome.policeForce,
            errorType = ingestionOutcome.errorType,
            records = ingestionOutcome.records,
            errors = ingestionOutcome.errors,
            recordCount = ingestionOutcome.recordCount,
          ),
        ),
        state = EmailOutboxState.PENDING,
        attempts = 0,
        claimedAt = Instant.now(),
      ),
    )
    whenever(emailOutboxRepository.claimEligibleRows(eq(EmailOutboxState.PENDING.name), eq(EmailOutboxState.FAILED.name), any<Int>(), any(), any())).thenReturn(claimedRows)
    assertDoesNotThrow {
      service.sendEmails()
    }
    verify(emailOutboxRepository, times(1)).completeClaimedRow(any(), any(), eq(EmailOutboxState.PUBLISHED.name), eq(1), eq(null), eq(0))
  }
}

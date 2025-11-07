package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.service.crimeBatch

import jakarta.transaction.Transactional
import jakarta.validation.ValidationException
import jakarta.validation.Validator
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.entity.Crime
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.entity.CrimeBatch
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.CrimeType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.PoliceForce
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.repository.crimeBatch.CrimeBatchRepository
import java.io.InputStream
import java.util.UUID

@Service
class CrimeBatchService(
  private val crimeBatchRepository: CrimeBatchRepository,
  private val validator: Validator,
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  @Transactional
  fun ingestCsvData(csvData: InputStream) {
    val parser = CSVParser.parse(csvData, Charsets.UTF_8, CSVFormat.DEFAULT)
    val records = parser.records

    val firstRecord = records.first()
    val crimeBatch = CrimeBatch(
      id = UUID.randomUUID().toString(),
      policeForce = PoliceForce.from(firstRecord[0])?.name ?: "",
    )

    val batchViolations = validator.validate(crimeBatch)
    if (batchViolations.isNotEmpty()) {
      throw ValidationException("Batch data violation found: ${batchViolations.first().message}")
    }

    records.forEach { record ->
      try {
        val crime = constructCrime(record, crimeBatch)

        val crimeDataViolations = validator.validate(crime)
        if (crimeDataViolations.isEmpty()) {
          crimeBatch.crimes.add(crime)
        } else {
          for (violation in crimeDataViolations) {
            log.error("Crime data violation found: ${violation.message}")
          }
        }
      } catch (e: Exception) {
        log.error("Unexpected crime data validation error: ${e.message}")
      }
    }

    crimeBatchRepository.save(crimeBatch)
  }

  fun constructCrime(record: CSVRecord, crimeBatch: CrimeBatch) = Crime(
    crimeTypeId = record[1],
    crimeTypeDescription = CrimeType.from(record[1]).value,
    crimeReference = record[4],
    crimeDateTimeFrom = record[5],
    crimeDateTimeTo = record[6],
    easting = record[7],
    northing = record[8],
    latitude = record[9],
    longitude = record[10],
    datum = record[11],
    crimeText = record[12],
    crimeBatch = crimeBatch,
  )
}

package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.entity

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.data.ValidationErrors.Crime.INVALID_CRIME_DATE
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.data.ValidationErrors.Crime.INVALID_CRIME_REFERENCE
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.data.ValidationErrors.Crime.INVALID_CRIME_TYPE
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.data.ValidationErrors.Crime.INVALID_LOCATION_DATA
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.model.enums.CrimeType
import uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.validation.annotation.ValidEnum
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

@Entity
@Table(name = "crime")
data class Crime(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,
  @field:ValidEnum(enumClass = CrimeType::class, message = INVALID_CRIME_TYPE)
  val crimeTypeId: String,
  val crimeTypeDescription: String,
  @field:NotBlank(message = INVALID_CRIME_REFERENCE)
  val crimeReference: String,
  val crimeDateTimeFrom: String,
  val crimeDateTimeTo: String,
  val easting: String?,
  val northing: String?,
  val latitude: String?,
  val longitude: String?,
  val datum: String?,
  val crimeText: String?,

  @Schema(hidden = true)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  var crimeBatch: CrimeBatch,
) {
  @AssertTrue(message = INVALID_CRIME_DATE)
  fun isValidCrimeDateRange(): Boolean {
    try {
      val startDate = LocalDateTime.parse(crimeDateTimeFrom, formatter)
      val endDate = LocalDateTime.parse(crimeDateTimeTo, formatter)
      return startDate.isBefore(endDate) || startDate.isEqual(endDate)
    } catch (e: Exception) {
      return false
    }
  }

  @AssertTrue(message = INVALID_LOCATION_DATA)
  fun isValidLocationData(): Boolean {
    val hasGridRef = !easting.isNullOrBlank() && !northing.isNullOrBlank()
    val hasLatLong = !latitude.isNullOrBlank() && !longitude.isNullOrBlank()

    if (hasGridRef && hasLatLong) {
      return false
    }

    return if (hasGridRef) {
      validGridRef(easting, northing)
    } else if (hasLatLong) {
      validLatLong(latitude, longitude)
    } else {
      return true
    }
  }

  private fun validGridRef(easting: String, northing: String): Boolean = try {
    val east = easting.toInt()
    val north = northing.toInt()

    east in 0..600000 && north in 0..1300000
  } catch (e: NumberFormatException) {
    false
  }

  private fun validLatLong(latitude: String, longitude: String): Boolean = try {
    val lat = latitude.toDouble()
    val long = longitude.toDouble()

    lat in 49.5..61.5 && long in -8.5..2.6
  } catch (e: NumberFormatException) {
    false
  }
}

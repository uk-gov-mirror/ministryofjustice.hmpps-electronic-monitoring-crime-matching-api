package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.data

class ValidationErrors {
  object CrimeBatch {
    const val INVALID_POLICE_FORCE: String = "A valid police force must be provided"
  }

  object Crime {
    const val INVALID_CRIME_TYPE: String = "A valid crime type must be provided"
    const val INVALID_CRIME_REFERENCE: String = "A crime reference must be provided"
    const val INVALID_CRIME_DATE: String = "A valid crime date range must be provided"
    const val INVALID_LOCATION_DATA: String = "Valid location data must be provided"
  }
}

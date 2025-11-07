package uk.gov.justice.digital.hmpps.electronicmonitoringcrimematchingapi.helper

fun createCsvRow(
  policeForce: String = "Metropolitan",
  crimeTypeId: String = "TOMV",
  crimeReference: String = "CRI00000001",
  crimeDateTimeFrom: String = "20250125083000",
  crimeDateTimeTo: String = "20250125083000",
  easting: String? = "",
  northing: String? = "",
  latitude: String? = "54.732410000000002",
  longitude: String? = "-1.38542",
  datum: String = "WGS84",
  crimeText: String = "",
) = "$policeForce,$crimeTypeId,crimeDesc,oldBatchId,$crimeReference,$crimeDateTimeFrom,$crimeDateTimeTo,$easting,$northing,$latitude,$longitude,$datum,$crimeText"

fun createEmailFile(csvContent: String) = """
  Subject: Test-SQS-Email
  Date: Wed, 15 Oct 2025 13:56:58 +0000
  X-MS-Has-Attach: yes
  Content-Type: multipart/mixed;
  	boundary="_004_CWXP123MB325699A8F40C4C6DB54C4B90A9E8ACWXP123MB3256GBRP_"
  
  --_004_CWXP123MB325699A8F40C4C6DB54C4B90A9E8ACWXP123MB3256GBRP_
  Content-Type: text/csv;
  	name="crime-data.csv"
  Content-Description: crime-data.csv
  Content-Disposition: attachment;
  	filename="crime-data.csv";
  	size=1449; creation-date="Wed, 15 Oct 2025 13:56:53 GMT";
  	modification-date="Wed, 15 Oct 2025 13:56:53 GMT"
  Content-Transfer-Encoding: base64

  $csvContent

  --_004_CWXP123MB325699A8F40C4C6DB54C4B90A9E8ACWXP123MB3256GBRP_--
""".trimIndent()

fun createEmailFileWithoutAttachment() = """
  Subject: Test-SQS-Email
  Date: Wed, 15 Oct 2025 13:56:58 +0000
  X-MS-Has-Attach: no
  Content-Type: multipart/mixed;
  	boundary="_004_CWXP123MB325699A8F40C4C6DB54C4B90A9E8ACWXP123MB3256GBRP_"
  
  --_004_CWXP123MB325699A8F40C4C6DB54C4B90A9E8ACWXP123MB3256GBRP_

  --_004_CWXP123MB325699A8F40C4C6DB54C4B90A9E8ACWXP123MB3256GBRP_--
""".trimIndent()

package eu.lopaciuk.covidcertificateverifier

import COSE.HeaderKeys
import COSE.Message
import COSE.OneKey
import COSE.Sign1Message
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import java.util.*

typealias EntryType = CertificateRecord.HealthCertificate.CertificateEntryType

class CertificateRecord(
    val issuer: String,     // 1
    val issueDate: Date,    // 4
    val expiryDate: Date,   // 6
    val healthCertificate: HealthCertificate // -260
    ) {
    private var coseMessage: Message? = null

    class HealthCertificate(
        val version: String,        // ver
        val givenName: String,      // nam/gn
        val lastName: String,       // nam/fn
        val givenNameStd: String,   // nam/gnt
        val lastNameStd: String,    // nam/fnt
        val dateOfBirth: Date,      // dob
        val entry: CertificateEntry // (v|t|r)[0]
    ) {
        enum class CertificateEntryType {
            VACCINATION, TEST, RECOVERY
        }
        interface CertificateEntry {
            val diseaseCode: String // */tg
            val countryCode: String     // */co
            val issuer: String      // */is
            val uid: String         // */ci
            fun getType(): CertificateEntryType
            fun getDiseaseName(): String {
                // http://hl7.org/fhir/uv/ips/STU1/ValueSet-snomed-intl-gps.html
                return when (diseaseCode) {
                    "840539006" -> "COVID-19"
                    else -> "Unknown"
                }
            }
        }
        class VaccinationCertificate(
            override val diseaseCode: String, // v/tg
            override val countryCode: String,     // v/co
            override val issuer: String,      // v/is
            override val uid: String,         // v/ci
            val prophylaxisCode: String,      // v/vp
            val product: String,              // v/mp
            val manufacturer: String,         // v/ma
            val doseNo: Int,                  // v/dn
            val dosesInSeries: Int,           // v/sd
            val date: Date,                   // v/dt
        ) : CertificateEntry {
            override fun getType(): CertificateEntryType {
                return CertificateEntryType.VACCINATION
            }
            fun getProphylaxisName(): String {
                // https://github.com/ehn-dcc-development/ehn-dcc-schema/blob/release/1.3.0/valuesets/vaccine-prophylaxis.json
                return when(prophylaxisCode) {
                    "1119349007" -> "SARS-CoV-2 mRNA vaccine"
                    "1119305005" -> "SARS-CoV-2 antigen vaccine"
                    "J07BX03" -> "COVID-19 vaccines"
                    else -> "Unknown (${prophylaxisCode})"
                }
            }

            fun getProductName(): String {
                // https://github.com/ehn-dcc-development/ehn-dcc-schema/blob/release/1.3.0/valuesets/vaccine-medicinal-product.json
                return when(product) {
                    "EU/1/20/1528" -> "Comirnaty"
                    "EU/1/20/1507" -> "COVID-19 Vaccine Moderna"
                    "EU/1/21/1529" -> "Vaxzevria"
                    "EU/1/20/1525" -> "COVID-19 Vaccine Janssen"
                    "CVnCoV" -> "CVnCoV"
                    "Sputnik-V" -> "Sputnik-V"
                    "Convidecia" -> "Convidecia"
                    "EpiVacCorona" -> "EpiVacCorona"
                    "BBIBP-CorV" -> "BBIBP-CorV"
                    "Inactivated-SARS-CoV-2-Vero-Cell" -> "Inactivated SARS-CoV-2 (Vero Cell)"
                    "CoronaVac" -> "CoronaVac"
                    "Covaxin" -> "Covaxin (also known as BBV152 A, B, C)"
                    else -> "Unknown (${product})"
                }
            }

            fun getManufacturerName(): String {
                // https://github.com/ehn-dcc-development/ehn-dcc-schema/blob/release/1.3.0/valuesets/vaccine-mah-manf.json
                return when(manufacturer) {
                    "ORG-100001699" -> "AstraZeneca AB"
                    "ORG-100030215" -> "Biontech Manufacturing GmbH"
                    "ORG-100001417" -> "Janssen-Cilag International"
                    "ORG-100031184" -> "Moderna Biotech Spain S.L."
                    "ORG-100006270" -> "Curevac AG"
                    "ORG-100013793" -> "CanSino Biologics"
                    "ORG-100020693" -> "China Sinopharm International Corp. - Beijing location"
                    "ORG-100010771" -> "Sinopharm Weiqida Europe Pharmaceutical s.r.o. - Prague location"
                    "ORG-100024420" -> "Sinopharm Zhijun (Shenzhen) Pharmaceutical Co. Ltd. - Shenzhen location"
                    "ORG-100032020" -> "Novavax CZ AS"
                    "Gamaleya-Research-Institute" -> "Gamaleya Research Institute"
                    "Vector-Institute" -> "Vector Institute"
                    "Sinovac-Biotech" -> "Sinovac Biotech"
                    "Bharat-Biotech" -> "Bharat Biotech"
                    else -> "Unknown ${manufacturer}"
                }
            }
        }
    }

    companion object {
        fun fromQRCode(qrData: String): CertificateRecord {
            val b64decoder = Base45.getDecoder()
            val decodedBytes = b64decoder.decode(qrData)
            val cose = zlibDecompress(decodedBytes)
            var message = Message.DecodeFromBytes(cose)
            val node = CBORMapper().readTree(message.GetContent())
            val certificate = fromJsonNode(node)
            certificate.addCOSEMessage(message)
            return certificate
        }
        
        private fun fromJsonNode(node: JsonNode): CertificateRecord {
            val certNode = node["-260"]["1"]

            return CertificateRecord(
                issuer = node["1"].asText(),
                issueDate = Date(node["6"].asLong() * 1000),
                expiryDate = Date(node["4"].asLong() * 1000),
                healthCertificate = HealthCertificate(
                    version = certNode["ver"].asText(),
                    givenName = certNode["nam"]["gn"].asText(),
                    lastName = certNode["nam"]["fn"].asText(),
                    givenNameStd = certNode["nam"]["gnt"].asText(),
                    lastNameStd = certNode["nam"]["fnt"].asText(),
                    dateOfBirth = dateFromPartialISO(certNode["dob"].asText()),
                    entry = when {
                        certNode.has("v") -> HealthCertificate.VaccinationCertificate(
                            diseaseCode = certNode["v"][0]["tg"].asText(),
                            countryCode = certNode["v"][0]["co"].asText(),
                            issuer = certNode["v"][0]["is"].asText(),
                            uid = certNode["v"][0]["ci"].asText(),
                            prophylaxisCode = certNode["v"][0]["vp"].asText(),
                            product = certNode["v"][0]["mp"].asText(),
                            manufacturer = certNode["v"][0]["ma"].asText(),
                            doseNo = certNode["v"][0]["dn"].asInt(),
                            dosesInSeries = certNode["v"][0]["sd"].asInt(),
                            date = dateFromPartialISO(certNode["v"][0]["dt"].asText()),
                        )
                        else -> throw InvalidCertificateType("No valid certificate group (v|r|t) found")
                    }
                )
            )
        }
    }

    private fun addCOSEMessage(cose: Message) {
        coseMessage = cose
    }

    fun verifySignature(keyDBHelper: KeyDatabaseHelper): Boolean {
        val kid = getKid() ?: return false
        val signed = coseMessage as Sign1Message

        val alg = signed.findAttribute(HeaderKeys.Algorithm)

        Log.d("SIGN", "Required key: $kid '${String(kid)}'")
        Log.d("SIGN", "For algorithm: ${alg.type} $alg")

        val publicKey = keyDBHelper.getPublicKeyByKid(kid)

        Log.d("SIGN", "Matched key in DB: $publicKey")

        return when(publicKey) {
            null -> false
            else -> signed.validate(OneKey(publicKey, null))
        }
    }

    fun getKid(): ByteArray? {
        if (coseMessage == null) return null
        val signed = coseMessage as Sign1Message
        return signed.findAttribute(HeaderKeys.KID).GetByteString()
    }

    class InvalidCertificateType(s: String) : Exception()
}
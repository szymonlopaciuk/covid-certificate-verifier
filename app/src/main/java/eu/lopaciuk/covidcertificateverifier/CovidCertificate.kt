package eu.lopaciuk.covidcertificateverifier

import COSE.HeaderKeys
import COSE.Message
import COSE.OneKey
import COSE.Sign1Message
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import org.joda.time.DateTime
import java.util.*

abstract class CovidCertificate(node: JsonNode) {
    protected val certNode: JsonNode = node["-260"]["1"]

    val issuer: String = node["1"].asText()
    val issueDate = Date(node["6"].asLong() * 1000)
    val expiryDate = Date(node["4"].asLong() * 1000)
    val version = certNode["ver"].asText()
    val givenName: String = certNode["nam"]["gn"].asText()
    val lastName: String = certNode["nam"]["fn"].asText()
    val givenNameStd: String = certNode["nam"]["gnt"].asText()
    val lastNameStd: String = certNode["nam"]["fnt"].asText()
    val dateOfBirth = dateFromPartialISO(certNode["dob"].asText())

    abstract val diseaseCode: String
    abstract val countryCode: String
    abstract val entryIssuer: String
    abstract val uid: String

    var coseMessage: Message? = null

    fun getDiseaseName(): String {
        // http://hl7.org/fhir/uv/ips/STU1/ValueSet-snomed-intl-gps.html
        return when (diseaseCode) {
            "840539006" -> "COVID-19"
            else -> "Unknown"
        }
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

    companion object {
        fun fromQRCode(qrData: String): CovidCertificate {
            val b64decoder = Base45.getDecoder()
            val decodedBytes = b64decoder.decode(qrData)
            val cose = zlibDecompress(decodedBytes)
            val message = Message.DecodeFromBytes(cose)
            val node = CBORMapper().readTree(message.GetContent())
            val certNode = node["-260"]["1"]

            val certificate: CovidCertificate = when {
                certNode.has("v") -> VaccinationCertificate(node)
                certNode.has("t") -> TestCertificate(node)
                certNode.has("r") -> RecoveryCertificate(node)
                else -> throw InvalidCertificateType("No valid certificate group (v|t|r) found")
            }

            certificate.coseMessage = message

            return certificate
        }
    }

    class InvalidCertificateType(s: String) : Exception()
}


class VaccinationCertificate(node: JsonNode) : CovidCertificate(node) {
    override val diseaseCode: String = certNode["v"][0]["tg"].asText()
    override val countryCode: String = certNode["v"][0]["co"].asText()
    override val entryIssuer: String = certNode["v"][0]["is"].asText()
    override val uid: String = certNode["v"][0]["ci"].asText()

    val prophylaxisCode: String = certNode["v"][0]["vp"].asText()
    val product: String = certNode["v"][0]["mp"].asText()
    val manufacturer: String = certNode["v"][0]["ma"].asText()
    val doseNo: Int = certNode["v"][0]["dn"].asInt()
    val dosesInSeries: Int = certNode["v"][0]["sd"].asInt()
    val date: Date = dateFromPartialISO(certNode["v"][0]["dt"].asText())

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
            else -> "Unknown (${manufacturer})"
        }
    }
}


class TestCertificate(node: JsonNode) : CovidCertificate(node) {
    override val diseaseCode: String = certNode["t"][0]["tg"].asText()
    override val countryCode: String = certNode["t"][0]["co"].asText()
    override val entryIssuer: String = certNode["t"][0]["is"].asText()
    override val uid: String = certNode["t"][0]["ci"].asText()

    val type: String = certNode["t"][0]["tt"].asText()
    val name: String? = certNode["t"][0]["nm"]?.asText()
    val deviceId: String = certNode["t"][0]["ma"].asText()
    val collectionTime: DateTime = DateTime.parse(certNode["t"][0]["sc"].asText())
    val result: String = certNode["t"][0]["tr"].asText()
    val facility: String = certNode["t"][0]["tc"].asText()

    fun getResultText(): String {
        // https://github.com/ehn-dcc-development/ehn-dcc-schema/blob/release/1.3.0/valuesets/test-result.json
        return when(result) {
            "260415000" -> "Negative"
            "260373001" -> "Positive"
            else -> "Unknown (${result})"
        }
    }

    fun isTestNegative(): Boolean {
        return result == "260415000"
    }

    fun getTypeText(): String {
        // https://github.com/ehn-dcc-development/ehn-dcc-schema/blob/release/1.3.0/valuesets/test-type.json
        return when(type) {
            "LP6464-4" -> "Nucleic acid amplification with probe detection"
            "LP217198-3" -> "Rapid immunoassay"
            else -> "Unknown (${type})"
        }
    }
}


class RecoveryCertificate(node: JsonNode) : CovidCertificate(node) {
    override val diseaseCode: String = certNode["r"][0]["tg"].asText()
    override val countryCode: String = certNode["r"][0]["co"].asText()
    override val entryIssuer: String = certNode["r"][0]["is"].asText()
    override val uid: String = certNode["r"][0]["ci"].asText()

    val diseaseTargeted = certNode["r"][0]["tg"]
    val firstResult = dateFromPartialISO(certNode["r"][0]["fr"].asText())
    val validFrom = dateFromPartialISO(certNode["r"][0]["df"].asText())
    val validUntil = dateFromPartialISO(certNode["r"][0]["du"].asText())
}
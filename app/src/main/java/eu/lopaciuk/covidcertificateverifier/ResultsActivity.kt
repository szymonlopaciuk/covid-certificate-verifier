package eu.lopaciuk.covidcertificateverifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Verified
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.lopaciuk.covidcertificateverifier.ui.theme.CovidCertificateVerifierTheme
import eu.lopaciuk.covidcertificateverifier.ui.theme.VerificationResultTheme
import org.joda.time.DateTime
import java.util.concurrent.Executors


class ResultsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qrData = this.intent.getStringArrayExtra("qrData")
        val cert = CovidCertificate.fromQRCode(qrData!![0])

        setContent {
            CovidCertificateVerifierTheme {
                val certTypeText = when (cert) {
                    is VaccinationCertificate -> "Vaccination"
                    is RecoveryCertificate -> "Recovery"
                    is TestCertificate -> "Test"
                    else -> throw Exception("Cannot display an unsupported certificate type: ${cert.javaClass}")
                }
                val disease = cert.getDiseaseName()

                Scaffold(
                    scaffoldState = rememberScaffoldState(),
                    backgroundColor = MaterialTheme.colors.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "$disease $certTypeText Certificate") }
                        )
                    },
                ) {
                    CertificateCompose(cert)
                }
            }
        }
    }
}

@Composable
fun CertificateCompose(cert: CovidCertificate) {
    val name = "${cert.givenName} ${cert.lastName}"
    val dateOfBirth = dateToText(cert.dateOfBirth)

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        val context = LocalContext.current.applicationContext
        val verified = produceState(initialValue = false) {
            Executors.newSingleThreadExecutor().execute {
                value = cert.verifySignature(KeyDatabaseHelper(context))
            }
        }

        var statusText = "VERIFIED & VALID"
        var valid = true
        if (DateTime(cert.expiryDate).plusDays(1).isBeforeNow) {  // assume expires at 23:59,
            valid = false                                               // so at 00:00 the next day
            statusText = "EXPIRED ON ${dateToText(cert.expiryDate)}"
        }
        else if (cert is TestCertificate && !cert.isTestNegative()) {
            valid = false
            statusText = "NOT A NEGATIVE TEST"
        }
        else if (cert is RecoveryCertificate && DateTime(cert.validFrom).isAfterNow)
        {
            valid = false
            statusText = "VALID FROM ${dateToText(cert.validFrom)}"
        }
        else if (cert is RecoveryCertificate
            && DateTime(cert.validUntil).plusDays(1).isBeforeNow)  // assume expires at 23:59
        {
            valid = false
            statusText = "EXPIRED ON ${dateToText(cert.validUntil)}"
        }
        else if (!verified.value) {
            valid = false
            statusText = "VERIFICATION FAILED"
        }

        DetailsSummary(
            name = name,
            dateOfBirth = dateOfBirth,
            valid = valid,
            statusText = statusText
        )
        if (cert is VaccinationCertificate) {
            BriefEntry(
                label = "Dose",
                value = "${cert.doseNo} of ${cert.dosesInSeries}"
            )
            BriefEntry(
                label = "Date administered",
                value = "${dateToText(cert.date)} (${relativeDateText(cert.date)})"
            )
            BriefEntry(
                label = "Manufacturer",
                value = cert.getManufacturerName()
            )
            BriefEntry(
                label = "Vaccine product",
                value = cert.getProductName()
            )
            BriefEntry(
                label = "Prophylaxis",
                value = cert.getProphylaxisName()
            )
        }
        else if (cert is TestCertificate) {
            BriefEntry(
                label = "Result",
                value = cert.getResultText()
            )
            BriefEntry(
                label = "Test type",
                value = cert.getTypeText()
            )
            if (cert.name != null)
                BriefEntry(
                    label = "Test name",
                    value = cert.name
                )
            BriefEntry(
                label = "Test device ID",
                value = cert.deviceId
            )
            BriefEntry(
                label = "Sample collection date and time",
                value = "${dateTimeToText(cert.collectionTime)} (${relativeTimeText(cert.collectionTime)})"
            )
            BriefEntry(
                label = "Testing centre/facility",
                value = cert.facility
            )
        }
        else if (cert is RecoveryCertificate) {
            BriefEntry(
                label = "Date of first positive result",
                value = dateToText(cert.firstResult)
            )
            BriefEntry(
                label = "Certificate validity (from/until)",
                value = "${dateToText(cert.validFrom)}/${dateToText(cert.validUntil)}"
            )
        }

        BriefEntry(
            label = "Certificate expiry date",
            value = dateToText(cert.expiryDate)
        )
        BriefEntry(
            label = "Transliterated name",
            value = "${cert.lastNameStd}, ${cert.givenNameStd}",
            fontFamily = FontFamily.Monospace
        )
        BriefEntry(
            label = "Disease targeted",
            value = cert.getDiseaseName()
        )
        BriefEntry(
            label = "Country",
            value = countryFromCode(cert.countryCode)
        )
        BriefEntry(
            label = "Certificate issuer",
            value = cert.entryIssuer
        )
        BriefEntry(label = "Unique reference", value = cert.uid)
        BriefEntry(
            label = "Public key ID",
            value = "${byteArrayToHexString(cert.getKid())}",
            fontFamily = when(cert.getKid()) {
                null -> FontFamily.Default
                else -> FontFamily.Monospace
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun BriefEntry(label: String, value: String, fontFamily: FontFamily = FontFamily.Default) {
    Column(
        modifier = Modifier.padding(
            horizontal = 16.dp,
            vertical = 8.dp
        )
    ) {
        Text(text = label, style = MaterialTheme.typography.caption)
        Text(text = value, style = MaterialTheme.typography.body1, fontFamily = fontFamily)
    }
}

@Composable
fun DetailsSummary(name: String, dateOfBirth: String, valid: Boolean, statusText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = name, style = MaterialTheme.typography.subtitle1)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Date of birth: $dateOfBirth", style = MaterialTheme.typography.subtitle2)
        Spacer(modifier = Modifier.height(8.dp))
        VerificationStatusBlip(valid, statusText)
    }
}

@Composable
fun VerificationStatusBlip(valid: Boolean, statusText: String) {
    VerificationResultTheme(good = valid) {
        val icon = when (valid) {
            true -> Icons.Filled.Verified
            false -> Icons.Filled.Dangerous
        }

        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colors.background,
                    shape = MaterialTheme.shapes.small
                )
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = statusText,
                tint = contentColorFor(backgroundColor = MaterialTheme.colors.background)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.overline,
                color = contentColorFor(backgroundColor = MaterialTheme.colors.background)
            )
            Spacer(modifier = Modifier.width(24.dp))
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun DefaultPreview() {
//    CovidCertificateVerifierTheme {
//        Surface(
//            color = MaterialTheme.colors.background
//        ) {
//            Column {
//                TopAppBar(
//                    title = { Text(text = "COVID-19 Vaccine Certificate") }
//                )
//                CertificateCompose(
//                    record = CertificateRecord(
//                        issuer = "Health Organisation",
//                        issueDate = dateFromPartialISO("2021-08-20"),
//                        expiryDate = dateFromPartialISO("2021-09-28"),
//                        version = "1.0.1",
//                        givenName = "Erika",
//                        lastName = "Müstermann",
//                        givenNameStd = "ERIKA",
//                        lastNameStd = "MUESTERMANN",
//                        dateOfBirth = dateFromPartialISO("1980-01-01"),
//                        cert = CertificateRecord.VaccinationCertificate(
//                            countryCode = "DE",
//                            issuer = "Health Organisation",
//                            date = dateFromPartialISO("2021-08-20"),
//                            diseaseCode = "840539006",
//                            prophylaxisCode = "1119349007",
//                            doseNo = 2,
//                            dosesInSeries = 2,
//                            product = "EU/1/20/1528",
//                            manufacturer = "EU/1/20/1528",
//                            uid = "URN:UVCI:01:DE:0123456789"
//                        )
//                    )
//                )
//            }
//        }
//    }
//}
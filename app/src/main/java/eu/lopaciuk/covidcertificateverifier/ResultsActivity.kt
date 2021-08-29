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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.lopaciuk.covidcertificateverifier.ui.theme.CovidCertificateVerifierTheme
import eu.lopaciuk.covidcertificateverifier.ui.theme.VerificationResultTheme
import org.joda.time.DateTime
import java.util.concurrent.Executors


class ResultsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qrData = this.intent.getStringArrayExtra("qrData")
        val certRecord = CertificateRecord.fromQRCode(qrData!![0])

        setContent {
            CovidCertificateVerifierTheme {
                val entryTypeText = when (certRecord.healthCertificate.entry.getType()) {
                    CertificateRecord.HealthCertificate.CertificateEntryType.VACCINATION -> "Vaccination"
                    CertificateRecord.HealthCertificate.CertificateEntryType.RECOVERY -> "Recovery"
                    CertificateRecord.HealthCertificate.CertificateEntryType.TEST -> "Test"
                }
                val disease = certRecord.healthCertificate.entry.getDiseaseName()

                Scaffold(
                    scaffoldState = rememberScaffoldState(),
                    backgroundColor = MaterialTheme.colors.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "$disease $entryTypeText Certificate") }
                        )
                    },
                ) {
                    VaccineCertificateCompose(record = certRecord)
                }
            }
        }
    }
}

@Composable
fun VaccineCertificateCompose(record: CertificateRecord) {
    val cert = record.healthCertificate
    val vax = record.healthCertificate.entry
            as CertificateRecord.HealthCertificate.VaccinationCertificate
    val name = "${cert.givenName} ${cert.lastName}"
    val dateOfBirth = dateToText(cert.dateOfBirth)

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        val scope = rememberCoroutineScope()
        val context = LocalContext.current.applicationContext
        val verified = produceState(initialValue = false) {
            Executors.newSingleThreadExecutor().execute {
                value = record.verifySignature(KeyDatabaseHelper(context))
            }
        }

        var statusText = "VERIFIED & VALID"
        var valid = true
        if (DateTime(record.expiryDate).isBeforeNow) {
            valid = false
            statusText = "EXPIRED ON ${dateToText(record.expiryDate)}"
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
        BriefEntry(
            label = "Dose",
            value = "${vax.doseNo} of ${vax.dosesInSeries}"
        )
        BriefEntry(
            label = "Disease targeted",
            value = vax.getDiseaseName()
        )
        BriefEntry(
            label = "Date administered",
            value = "${dateToText(vax.date)} (${relativeDateText(vax.date)})"
        )
        BriefEntry(
            label = "Transliterated name",
            value = "${cert.lastNameStd}, ${cert.givenNameStd}"
        )
        BriefEntry(
            label = "Manufacturer",
            value = vax.getManufacturerName()
        )
        BriefEntry(
            label = "Vaccine product",
            value = vax.getProductName()
        )
        BriefEntry(
            label = "Prophylaxis",
            value = vax.getProphylaxisName()
        )
        BriefEntry(
            label = "Certificate expiry date",
            value = dateToText(record.expiryDate)
        )
        BriefEntry(
            label = "Country",
            value = countryFromCode(vax.countryCode)
        )
        BriefEntry(
            label = "Certificate issuer",
            value = vax.issuer
        )
        BriefEntry(label = "Unique reference", value = vax.uid)
        BriefEntry(
            label = "Public key ID",
            value = "${byteArrayToHexString(record.getKid())}",
            fontFamily = when(record.getKid()) {
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CovidCertificateVerifierTheme {
        Surface(
            color = MaterialTheme.colors.background
        ) {
            Column {
                TopAppBar(
                    title = { Text(text = "COVID-19 Vaccine Certificate") }
                )
                VaccineCertificateCompose(
                    record = CertificateRecord(
                        issuer = "Health Organisation",
                        issueDate = dateFromPartialISO("2021-08-20"),
                        expiryDate = dateFromPartialISO("2021-09-28"),
                        healthCertificate = CertificateRecord.HealthCertificate(
                            version = "1.0.1",
                            givenName = "Erika",
                            lastName = "Müstermann",
                            givenNameStd = "ERIKA",
                            lastNameStd = "MUESTERMANN",
                            dateOfBirth = dateFromPartialISO("1980-01-01"),
                            entry = CertificateRecord.HealthCertificate.VaccinationCertificate(
                                countryCode = "DE",
                                issuer = "Health Organisation",
                                date = dateFromPartialISO("2021-08-20"),
                                diseaseCode = "840539006",
                                prophylaxisCode = "1119349007",
                                doseNo = 2,
                                dosesInSeries = 2,
                                product = "EU/1/20/1528",
                                manufacturer = "EU/1/20/1528",
                                uid = "URN:UVCI:01:DE:0123456789"
                            )
                        )
                    )
                )
            }
        }
    }
}
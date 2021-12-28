package eu.lopaciuk.covidcertificateverifier

import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.format.ISODateTimeFormat
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.Inflater


fun dateFromPartialISO(dateString: String): Date {
    val parser = ISODateTimeFormat.dateParser()
    return parser.parseDateTime(dateString).toDate()
}

fun dateToText(date: Date): String {
    val datetime = DateTime(date)
    return datetime.toString("yyyy-MM-dd")
}

fun dateTimeToText(time: DateTime): String {
    val datetime = DateTime(time)
    return datetime.toString("yyyy-MM-dd HH:mm")
}

fun relativeDateText(date: Date): String {
    val duration = Duration(DateTime(date), DateTime.now())
    return when {
        duration.standardDays < 1L -> "less than a day ago"
        duration.standardDays == 1L -> "yesterday"
        duration.standardDays <= 14L -> "${duration.standardDays} days ago"
        duration.standardDays <= 30 -> {
            val qualifier = if (duration.standardDays % 7 != 0L) "over " else ""
            "${qualifier}${duration.standardDays / 7} weeks ago"
        }
        else -> "over a month ago"
    }
}

fun relativeTimeText(time: DateTime): String {
    val duration = Duration(time, DateTime.now())
    return when {
        duration.standardHours < 72L -> "${duration.standardHours} hours ago"
        else -> "over 72 hours ago"
    }
}

fun zlibDecompress(compressed: ByteArray): ByteArray {
    val inflater = Inflater()
    val outputStream = ByteArrayOutputStream()

    return outputStream.use {
        val buffer = ByteArray(1024)

        inflater.setInput(compressed)

        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        outputStream.toByteArray()
    }
}

fun countryFromCode(code: String): String {
    val loc = Locale("", code)
    return loc.displayCountry
}

fun byteArrayToHexString(byteArray: ByteArray?): String? {
    if (byteArray == null) return null
    return byteArray.joinToString(" ") { String.format("%02x", it) }
}
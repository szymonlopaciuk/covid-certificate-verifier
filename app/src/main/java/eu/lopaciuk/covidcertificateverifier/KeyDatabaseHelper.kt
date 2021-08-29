package eu.lopaciuk.covidcertificateverifier

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

class KeyDatabaseHelper(context: Context) {
    companion object {
        private const val KEY_URL_UK = "https://covid-status.service.nhsx.nhs.uk/pubkeys/keys.json"
        private const val KEY_URL_EU_NL = "https://verifier-api.coronacheck.nl/v4/verifier/public_keys"
    }

    private val db: KeyDatabase = getKeyDatabase(context)

    fun updateKeys() {
        updateKeysUK()
        updateKeysEUNL()
    }

    private fun updateKeysUK() {
        val dao = db.keyDao()
        val ukKeys = URL(KEY_URL_UK).readText()
        val ukKeysArray = JSONArray(ukKeys)
        for (i in 0 until ukKeysArray.length()) {
            val jsonKeyObj = ukKeysArray[i] as JSONObject
            val kid = Base64.decode(jsonKeyObj["kid"] as String, Base64.DEFAULT)
            val key = Base64.decode(jsonKeyObj["publicKey"] as String, Base64.DEFAULT)
            val aKey = Key(kid, key)
            dao.insert(aKey)
        }
    }

    private fun updateKeysEUNL() {
        val dao = db.keyDao()
        val euKeysEncoded = URL(KEY_URL_EU_NL).readText()
        val euKeysPayload = Base64.decode(
            JSONObject(euKeysEncoded)["payload"] as String, Base64.DEFAULT)
        val euKeysMap = JSONObject(String(euKeysPayload))["eu_keys"] as JSONObject
        for (kidB64 in euKeysMap.keys()) {
            val keyB64 = ((euKeysMap[kidB64] as JSONArray)[0] as JSONObject)["subjectPk"] as String
            val kid = Base64.decode(kidB64, Base64.DEFAULT)
            val key = Base64.decode(keyB64, Base64.DEFAULT)
            val aKey = Key(kid, key)
            dao.insert(aKey)
        }
    }

    fun getPublicKeyByKid(kid: ByteArray): PublicKey? {
        val key = db.keyDao().getKeyByKid(kid)

        Log.d("SIGN", "Looking for key ${String(kid)}")
        Log.d("SIGN", "Keys available: ${getAllKids().joinToString()}")

        if (key != null) {
            val keySpec = X509EncodedKeySpec(key.publicKey)
            return KeyFactory.getInstance("EC").generatePublic(keySpec)
        } else {
            return null
        }
    }

    fun getAllKids(): List<ByteArray> {
        val keys = db.keyDao().getAllKeys()
        return keys.map { it.kid }
    }
}
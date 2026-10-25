package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * La identidad con la que el teléfono se presenta como «PC» ante el ADB de la tele.
 *
 * Una sola llave por teléfono, guardada en el almacenamiento privado de la app
 * (`filesDir/adb/`): se genera al emparejar la primera vez y a partir de ahí el ADB de la
 * tele la reconoce sin volver a pedir el código. Es el equivalente al `~/.android/adbkey`
 * de una computadora — quien la tenga puede abrir el shell de la tele, así que no sale
 * de este directorio.
 */
internal object AdbKeys {

    private const val KEY_FILE = "vision_adb_key.der"
    private const val CERT_FILE = "vision_adb_cert.der"
    private const val DEVICE_NAME = "VisionPlusMovil"

    @Volatile
    private var cached: Pair<PrivateKey, Certificate>? = null

    @Synchronized
    fun load(context: Context): Pair<PrivateKey, Certificate> {
        cached?.let { return it }
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val keyFile = File(dir, KEY_FILE)
        val certFile = File(dir, CERT_FILE)
        if (keyFile.exists() && certFile.exists()) {
            runCatching { read(keyFile, certFile) }.getOrNull()?.let {
                cached = it
                return it
            }
        }
        val created = create()
        keyFile.writeBytes(created.first.encoded)
        certFile.writeBytes(created.second.encoded)
        cached = created
        return created
    }

    /** El nombre que aparecerá en la tele al emparejar. */
    fun deviceName(): String = DEVICE_NAME

    private fun read(keyFile: File, certFile: File): Pair<PrivateKey, Certificate> {
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(certFile.inputStream())
        return privateKey to certificate
    }

    /**
     * RSA 2048 + certificado autofirmado SHA-256 de 10 años: es exactamente lo que `adb`
     * genera en una PC y lo único que el demonio de la tele necesita para autorizar la llave.
     */
    private fun create(): Pair<PrivateKey, Certificate> {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keyPair = generator.generateKeyPair()
        val subject = X500Name("CN=$DEVICE_NAME")
        val notBefore = Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        val notAfter = Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000)
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val builder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return keyPair.private to certificate as Certificate
    }
}

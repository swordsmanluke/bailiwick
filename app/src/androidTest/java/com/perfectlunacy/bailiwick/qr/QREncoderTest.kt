package com.perfectlunacy.bailiwick.qr

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class QREncoderTest {

    @Test
    fun decode() {
        val data = "This is a QR barcode".toByteArray()
        val image = QREncoder().encode(data)
        val tmpFile = File.createTempFile("aimg", ".png")
        tmpFile.outputStream().use { f ->
            image.compress(Bitmap.CompressFormat.PNG, 90, f)
        }

        val result = QREncoder().decode(tmpFile)
        Assert.assertEquals(String(result), String(data))
    }

    @Test
    fun encode() {
        val image = QREncoder().encode("This is a QR barcode".toByteArray())
        Assert.assertNotNull(image)
    }
}
package com.perfectlunacy.bailiwick

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.*

class QRCode {
    companion object {
        @JvmStatic
        fun create(content: ByteArray): Bitmap {
            return create(Base64.getEncoder().encodeToString(content))
        }

        @JvmStatic
        private fun create(content: String): Bitmap {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.getWidth ()
            val height = bitMatrix.getHeight ();
            val bmp = Bitmap.createBitmap (width, height, Bitmap.Config.RGB_565);

            for (x in (0 until width)) {
                for (y in (0 until height)) {
                    val color = if (bitMatrix.get(x, y)) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                    bmp.setPixel(x, y, color);
                }
            }

            return bmp
        }
    }
}
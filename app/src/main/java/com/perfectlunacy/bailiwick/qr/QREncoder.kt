package com.perfectlunacy.bailiwick.qr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.perfectlunacy.bailiwick.QRCode
import java.io.File
import java.io.InputStream
import java.util.*


class QREncoder {

    fun decode(f: File): ByteArray {
        return decode(f.inputStream())
    }

    fun decode(s: InputStream): ByteArray {
        val bmp = BitmapFactory.decodeStream(s)!!
        return decode(bmp)
    }

    fun decode(bmp: Bitmap): ByteArray {
        val pixels = IntArray(bmp.height*bmp.width)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, pixels)))
        val result = QRCodeReader().decode(bitmap, mutableMapOf(Pair(DecodeHintType.PURE_BARCODE, 1)))
        return result.text.toByteArray()
    }

    fun encode(data: ByteArray): Bitmap {
        return QRCode.create(data)
    }

}
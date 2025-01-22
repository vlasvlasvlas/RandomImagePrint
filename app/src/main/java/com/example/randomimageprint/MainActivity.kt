package com.example.randomimageprint

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import java.io.InputStream
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        /**
         * Permisos requeridos para Bluetooth (y se podrían añadir otros).
         * Replicando la idea de tu amigo, si usas más cosas (cámara, audio, etc.),
         * también los pondrías aquí.
         */
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).apply {
            // En dispositivos con Android <= P, podrías añadir WRITE_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                // add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    // Manejo de la conexión con la impresora
    private var connection: BluetoothConnection? = null
    private var printer: EscPosPrinter? = null

    /**
     * Igual que en el código de tu amigo, al finalizar el request de permisos,
     * se llama de nuevo a [setupPrinter], sin hacer un chequeo manual de si se concedió
     * todo o no. (Tu amigo llama a startCamera() de inmediato.)
     */
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Tu amigo ignora la verificación y llama directamente a la acción principal.
            // Aquí, llamamos a setupPrinter() (en su caso, a startCamera()).
            setupPrinter()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencia a tu botón de imprimir
        val btnPrint = findViewById<Button>(R.id.btnPrint)

        // Verificamos si ya están concedidos los permisos
        if (allPermissionsGranted()) {
            setupPrinter()
        } else {
            requestPermissions()
        }

        // Evento al hacer clic en el botón
        btnPrint.setOnClickListener {
            val bitmap = getRandomBitmapFromAssets()
            if (bitmap == null) {
                Toast.makeText(this, "No se encontraron imágenes en /assets", Toast.LENGTH_SHORT).show()
            } else {
                printBluetooth(bitmap)
            }
        }
    }

    /**
     * Comprueba si todos los permisos de la lista están concedidos.
     */
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all { permiso ->
            ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Lanza el diálogo del sistema para pedir permisos (igual que el requestPermissions
     * de tu amigo).
     */
    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    /**
     * Configura la conexión con la impresora Bluetooth,
     * usando la misma lógica que en tu versión previa.
     */
    @SuppressLint("MissingPermission")
    private fun setupPrinter() {
        try {
            // Selecciona la primera impresora Bluetooth vinculada
            connection = BluetoothPrintersConnections.selectFirstPaired()
            if (connection == null) {
                Toast.makeText(this, "No hay impresora vinculada. Vincúlala en ajustes BT.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Conectado a: ${connection?.device?.name}", Toast.LENGTH_SHORT).show()
                // Ajusta los parámetros según tu impresora
                // 203 dpi / 48 mm ancho de impresión / 32 caracteres
                printer = EscPosPrinter(connection, 203, 48f, 32)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al configurar la impresora: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Obtiene una imagen aleatoria de la carpeta assets.
     * Filtra por .png, .jpg, .jpeg (ignora mayúsculas/minúsculas).
     */
    private fun getRandomBitmapFromAssets(): Bitmap? {
        return try {
            val fileNames = assets.list("")?.filter {
                it.endsWith(".png", true) || it.endsWith(".jpg", true) || it.endsWith(".jpeg", true)
            } ?: emptyList()

            if (fileNames.isNotEmpty()) {
                val randomFileName = fileNames[Random.nextInt(fileNames.size)]
                val inputStream: InputStream = assets.open(randomFileName)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Imprime la imagen usando la librería
     * e implementa el dithering Floyd-Steinberg y escalado.
     */
    @SuppressLint("MissingPermission")
    private fun printBluetooth(bitmap: Bitmap) {
        try {
            if (connection == null || printer == null) {
                Toast.makeText(this, "No hay impresora conectada", Toast.LENGTH_LONG).show()
                return
            }

            Log.i(TAG, "Iniciando impresión...")

            // Conectar físicamente (si no estaba conectado ya)
            connection?.connect()

            // Escalar a 384 de ancho (típico en impresoras de 58mm)
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap, 384,
                (384f / bitmap.width * bitmap.height).toInt(), true
            )
            Log.i(TAG, "Imagen escalada")

            // Convertir a escala de grises
            val grayscale = toGrayscale(resizedBitmap)
            Log.i(TAG, "Imagen en grises")

            // Aplicar dithering Floyd-Steinberg
            val dithered = floydSteinbergDithering(grayscale)
            Log.i(TAG, "Dithering aplicado")

            // Generar el texto que la impresora entiende para imprimir
            val textBuilder = StringBuilder()
            for (y in 0 until dithered.height step 32) {
                val segmentHeight = if (y + 32 > dithered.height) dithered.height - y else 32
                val segment = Bitmap.createBitmap(dithered, 0, y, dithered.width, segmentHeight)

                textBuilder.append("<img>")
                    .append(
                        PrinterTextParserImg.bitmapToHexadecimalString(
                            printer, segment, false
                        )
                    )
                    .append("</img>\n")
            }

            // Imprimir
            printer?.printFormattedText(textBuilder.toString())

            // Desconectar
            connection?.disconnect()

            Toast.makeText(this, "Impresión finalizada", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al imprimir: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Convierte la imagen a escala de grises.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val oldPixel = pixels[i]
            val red = (oldPixel shr 16) and 0xFF
            val green = (oldPixel shr 8) and 0xFF
            val blue = oldPixel and 0xFF
            val gray = (0.3 * red + 0.59 * green + 0.11 * blue).toInt()
            pixels[i] = Color.rgb(gray, gray, gray)
        }

        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        grayBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return grayBitmap
    }

    /**
     * Algoritmo Floyd-Steinberg para binarizar con dithering.
     */
    private fun floydSteinbergDithering(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val oldPixel = pixels[index]
                val oldGray = Color.red(oldPixel)
                val newGray = if (oldGray > 128) 255 else 0
                val error = oldGray - newGray

                pixels[index] = Color.rgb(newGray, newGray, newGray)

                // Difundir el error
                if (x + 1 < width) {
                    val idx = y * width + (x + 1)
                    val c = Color.red(pixels[idx]) + (error * 7 / 16)
                    pixels[idx] = Color.rgb(c.coerceIn(0,255), c.coerceIn(0,255), c.coerceIn(0,255))
                }
                if (y + 1 < height) {
                    if (x - 1 >= 0) {
                        val idx = (y + 1) * width + (x - 1)
                        val c = Color.red(pixels[idx]) + (error * 3 / 16)
                        pixels[idx] = Color.rgb(c.coerceIn(0,255), c.coerceIn(0,255), c.coerceIn(0,255))
                    }
                    val idx = (y + 1) * width + x
                    val c = Color.red(pixels[idx]) + (error * 5 / 16)
                    pixels[idx] = Color.rgb(c.coerceIn(0,255), c.coerceIn(0,255), c.coerceIn(0,255))

                    if (x + 1 < width) {
                        val idx2 = (y + 1) * width + (x + 1)
                        val c2 = Color.red(pixels[idx2]) + (error * 1 / 16)
                        pixels[idx2] = Color.rgb(c2.coerceIn(0,255), c2.coerceIn(0,255), c2.coerceIn(0,255))
                    }
                }
            }
        }

        val ditheredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        ditheredBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return ditheredBitmap
    }
}

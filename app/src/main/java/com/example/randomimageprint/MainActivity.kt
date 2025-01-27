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
         */
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    /**
     * Lista de frases disponibles para imprimir de forma aleatoria.
     */
    private val listaFrases = listOf(
        "compartido",
        "torpedo",
        "ring ring",
        "misterio (homenaje a David lynch)",
        "aceitoso",
        "baba",
        "jugo",
        "tuki",
        "sangre",
        "ciempiés",
        "salado",
        "vértice",
        "piano",
        "robot solitario",
        "robot melancólico",
        "robot acompañado o compañía",
        "lengua",
        "salpicar y salticar",
        "perezoso",
        "pasito",
        "anamorfia o grayskull",
        "comer",
        "algodón de azúcar y pochoclos",
        "reintentar",
        "carameloso",
        "olor a mar",
        "acá",
        "espiar o tortuga gigante",
        "culo al aire",
        "aburrido",
        "energúmeno",
        "piernas separadas o abiertas",
        "nublado maquinaria",
        "mareo",
        "monster hormiga",
        "croqueta bb",
        "trío",
        "ano",
        "tibieza",
        "corridos",
        "dedito o despeinado",
        "pecas",
        "caldo",
        "brillantina",
        "sailor Moon",
        "nosotros",
        "reunión de tupper",
        "atrevida",
        "fluo",
        "cuádriceps",
        "tensión muscular",
        "habitual"
    )

    // Manejo de la conexión con la impresora
    private var connection: BluetoothConnection? = null
    private var printer: EscPosPrinter? = null

    /**
     * Se encarga de manejar la respuesta del sistema tras solicitar permisos.
     */
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
            // Log para saber qué devolvió
            permissionsMap.forEach { (perm, granted) ->
                Log.d(TAG, "Permiso $perm concedido? $granted")
            }
            // Reintentamos setupPrinter()
            setupPrinter()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Iniciando la actividad")
        setContentView(R.layout.activity_main)

        // Referencia a tu botón de imprimir
        val btnPrint = findViewById<Button>(R.id.btnPrint)

        // Verificamos si ya están concedidos los permisos
        if (allPermissionsGranted()) {
            Log.i(TAG, "Permisos ya concedidos. Se procede con setupPrinter()")
            setupPrinter()
        } else {
            Log.w(TAG, "Faltan permisos. Mostrando diálogo para solicitarlos...")
            requestPermissions()
        }

        // Al hacer clic en el botón, intentamos imprimir
        btnPrint.setOnClickListener {
            Log.d(TAG, "Botón imprimir clicado. Obteniendo imagen aleatoria.")
            val result = getRandomBitmapFromAssets() // Retorna un Pair con (Bitmap?, nombreArchivo?)
            val bitmap = result.first
            val fileName = result.second

            if (bitmap == null || fileName == null) {
                Toast.makeText(this, "No se encontraron imágenes en /assets", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "No se encontraron imágenes en assets o no se pudo cargar el archivo.")
            } else {
                // Muestro nombre del archivo en Toast y logs
                Toast.makeText(this, "Se imprimirá la imagen: $fileName", Toast.LENGTH_LONG).show()
                Log.i(TAG, "La imagen elegida para imprimir: $fileName")

                printBluetooth(bitmap)
            }
        }
    }

    /**
     * Comprueba si ya tenemos todos los permisos concedidos.
     */
    private fun allPermissionsGranted(): Boolean {
        val allGranted = REQUIRED_PERMISSIONS.all { permiso ->
            ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "allPermissionsGranted? -> $allGranted")
        return allGranted
    }

    /**
     * Lanza el diálogo del sistema para pedir permisos
     */
    private fun requestPermissions() {
        Log.i(TAG, "requestPermissions: Lanzando diálogo para pedir permisos.")
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    /**
     * Configura la conexión con la impresora Bluetooth
     */
    @SuppressLint("MissingPermission")
    private fun setupPrinter() {
        Log.i(TAG, "setupPrinter: Iniciando configuración de la impresora.")
        try {
            // Selecciona la primera impresora Bluetooth vinculada
            connection = BluetoothPrintersConnections.selectFirstPaired()

            if (connection == null) {
                Toast.makeText(this, "No hay impresora vinculada. Vincúlala en ajustes BT.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "No se encontró impresora vinculada.")
            } else {
                Toast.makeText(this, "Conectado a: ${connection?.device?.name}", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Impresora detectada: ${connection?.device?.name} - Iniciando EscPosPrinter...")

                // Ajusta los parámetros según tu impresora
                // 203 dpi / 48 mm / 32 chars
                printer = EscPosPrinter(connection, 203, 48f, 32)
                Log.i(TAG, "EscPosPrinter inicializado correctamente.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al configurar la impresora: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Excepción en setupPrinter: ${e.message}", e)
        }
    }

    /**
     * Obtiene un Pair<Bitmap?, String?> con un bitmap aleatorio de la carpeta assets y su nombre.
     */
    private fun getRandomBitmapFromAssets(): Pair<Bitmap?, String?> {
        Log.d(TAG, "getRandomBitmapFromAssets: Buscando archivos en assets.")
        return try {
            val fileNames = assets.list("")?.filter {
                it.endsWith(".bmp", true) || it.endsWith(".png", true) || it.endsWith(".jpg", true) || it.endsWith(".jpeg", true)
            } ?: emptyList()

            if (fileNames.isNotEmpty()) {
                Log.d(TAG, "Imágenes encontradas en assets: $fileNames")
                val randomFileName = fileNames[Random.nextInt(fileNames.size)]
                Log.d(TAG, "Seleccionando archivo aleatorio: $randomFileName")

                val inputStream: InputStream = assets.open(randomFileName)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                Log.i(TAG, "Imagen $randomFileName cargada correctamente.")
                Pair(bitmap, randomFileName)
            } else {
                Log.w(TAG, "No se encontraron archivos de imagen en assets.")
                Pair(null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error al abrir imagen de assets: ${e.message}", e)
            Pair(null, null)
        }
    }

    /**
     * Imprime la imagen usando la librería
     * e implementa el dithering Floyd-Steinberg y escalado.
     */
    @SuppressLint("MissingPermission")
    private fun printBluetooth(bitmap: Bitmap) {
        Log.d(TAG, "printBluetooth: Iniciando impresión.")
        try {
            if (connection == null || printer == null) {
                Toast.makeText(this, "No hay impresora conectada", Toast.LENGTH_LONG).show()
                Log.e(TAG, "No se puede imprimir: connection=$connection, printer=$printer")
                return
            }

            Log.i(TAG, "Intentando conectar con la impresora físicamente (BluetoothConnection.connect())...")
            // Conectar físicamente (si no estaba conectado ya)
            try {
                connection?.connect()
                Log.i(TAG, "Conexión Bluetooth establecida exitosamente.")
                Toast.makeText(this, "Conexión establecida", Toast.LENGTH_SHORT).show()
            } catch (ce: Exception) {
                Toast.makeText(this, "Error al conectar: ${ce.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error al conectar con la impresora: ${ce.message}", ce)
                return
            }

            // Escalar a 384 px de ancho (típico en impresoras de 58mm)
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap,
                384,
                (384f / bitmap.width * bitmap.height).toInt(),
                true
            )
            Log.i(TAG, "Imagen escalada a 384px de ancho")

            // Convertir a escala de grises
            val grayscale = toGrayscale(resizedBitmap)
            Log.i(TAG, "Imagen convertida a grises")

            // Aplicar dithering Floyd-Steinberg
            val dithered = floydSteinbergDithering(grayscale)
            Log.i(TAG, "Algoritmo Floyd-Steinberg aplicado")

            // Generar el texto que la impresora entiende para imprimir
            val textBuilder = StringBuilder()
            for (y in 0 until dithered.height step 16) {
                val segmentHeight = if (y + 16 > dithered.height) dithered.height - y else 16
                val segment = Bitmap.createBitmap(dithered, 0, y, dithered.width, segmentHeight)

                textBuilder.append("<img>")
                    .append(
                        PrinterTextParserImg.bitmapToHexadecimalString(
                            printer, segment, false
                        )
                    )
                    .append("</img>\n")
            }

            // Elige una frase aleatoria y agrégala al final
            val randomPhrase = listaFrases[Random.nextInt(listaFrases.size)]
            textBuilder.append("\n$randomPhrase\n\n")

            Log.d(TAG, "Texto para impresión generado. Longitud: ${textBuilder.length}")

            // Imprimir
            printer?.printFormattedText(textBuilder.toString())
            Log.i(TAG, "Envío de datos a la impresora completado.")

            // Desconectar
            connection?.disconnect()
            Log.i(TAG, "Conexión Bluetooth desconectada.")
            Toast.makeText(this, "Impresión finalizada", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al imprimir: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Excepción durante printBluetooth: ${e.message}", e)
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

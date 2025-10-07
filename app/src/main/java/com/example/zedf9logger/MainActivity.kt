    package com.example.zedf9logger

    import android.hardware.usb.UsbManager
    import android.os.Bundle
    import android.widget.Button
    import android.widget.TextView
    import androidx.appcompat.app.AppCompatActivity
    import kotlinx.coroutines.*
    import java.io.*
    import java.nio.charset.StandardCharsets
    import java.text.SimpleDateFormat
    import java.util.*
    import java.util.concurrent.atomic.AtomicReference
    import com.hoho.android.usbserial.driver.UsbSerialProber
    import com.hoho.android.usbserial.driver.UsbSerialPort

    class MainActivity : AppCompatActivity() {
        private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        private lateinit var tvStatus: TextView
        private lateinit var btnConnectUsb: Button
        private lateinit var btnCapture: Button
        private lateinit var tvLastPos: TextView

        private val serialInput = AtomicReference<InputStream?>(null)
        private val serialOutput = AtomicReference<OutputStream?>(null)

        private var lastPosition: Position? = null

        private lateinit var csvFile: File
        private lateinit var csvWriter: BufferedWriter

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            tvStatus = findViewById(R.id.tvStatus)
            btnConnectUsb = findViewById(R.id.btnConnectUsb)
            btnCapture = findViewById(R.id.btnCapture)
            tvLastPos = findViewById(R.id.tvLastPos)

            createCsvFile()

            btnConnectUsb.setOnClickListener { connectUsb() }
            btnCapture.setOnClickListener { capturePoint() }

            mainScope.launch { readerLoop() }
        }

        private fun createCsvFile() {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val name = "session_${sdf.format(Date())}.csv"
            csvFile = File(getExternalFilesDir(null), name)
            csvWriter = BufferedWriter(FileWriter(csvFile, true))
            csvWriter.write("timestamp,lat,lon,alt
")
            csvWriter.flush()
            tvStatus.text = "CSV créé: ${csvFile.name}"
        }

        private fun connectUsb() {
            val usbManager = getSystemService(USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (availableDrivers.isEmpty()) {
                tvStatus.text = "Aucun périphérique USB-Serial trouvé"
                return
            }
            val driver = availableDrivers[0]
            val device = driver.device
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                tvStatus.text = "Permission USB requise"
                return
            }
            val port = driver.ports[0]
            try {
                port.open(connection)
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                serialOutput.set(port.outputStream)
                serialInput.set(port.inputStream)
                tvStatus.text = "USB connecté: ${device.deviceName}"
            } catch (e: Exception) {
                tvStatus.text = "Erreur ouverture port USB: ${e.message}"
            }
        }

        private suspend fun readerLoop() = withContext(Dispatchers.IO) {
            val buf = ByteArray(4096)
            val sb = StringBuilder()
            while (isActive) {
                val inStream = serialInput.get()
                if (inStream == null) {
                    delay(500)
                    continue
                }
                try {
                    val r = inStream.read(buf)
                    if (r > 0) {
                        val s = String(buf, 0, r, StandardCharsets.US_ASCII)
                        sb.append(s)
                        var idx = sb.indexOf("\n")
                        while (idx >= 0) {
                            val line = sb.substring(0, idx).trim()
                            sb.delete(0, idx + 1)
                            parseNmeaLine(line)
                            idx = sb.indexOf("\n")
                        }
                    } else {
                        delay(50)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { tvStatus.text = "Reader erreur: ${e.message}" }
                    delay(500)
                }
            }
        }

        private fun parseNmeaLine(line: String) {
            try {
                if (!line.startsWith("$")) return
                val parts = line.split(",")
                when {
                    parts[0].endsWith("GGA") -> {
                        val timeStr = parts[1]
                        val lat = nmeaToDecimal(parts[2], parts[3])
                        val lon = nmeaToDecimal(parts[4], parts[5])
                        val alt = parts[9].toDoubleOrNull() ?: 0.0
                        val ts = nmeaTimeToDate(timeStr)
                        val pos = Position(lat, lon, alt, ts)
                        lastPosition = pos
                        runOnUiThread { tvLastPos.text = "Dernière position: ${pos.lat}, ${pos.lon} (alt ${pos.alt})" }
                    }
                    parts[0].endsWith("RMC") -> {
                        val timeStr = parts[1]
                        val dateStr = parts[9]
                        val lat = nmeaToDecimal(parts[3], parts[4])
                        val lon = nmeaToDecimal(parts[5], parts[6])
                        val ts = nmeaRmcToDate(timeStr, dateStr)
                        val pos = Position(lat, lon, 0.0, ts)
                        lastPosition = pos
                        runOnUiThread { tvLastPos.text = "Dernière position: ${pos.lat}, ${pos.lon}" }
                    }
                }
            } catch (t: Throwable) {
                // ignore
            }
        }

        private fun capturePoint() {
            val pos = lastPosition
            if (pos == null) {
                tvStatus.text = "Aucune position disponible pour capture"
                return
            }
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val line = "${sdf.format(pos.timestamp)},${pos.lat},${pos.lon},${pos.alt}\n"
                csvWriter.write(line)
                csvWriter.flush()
                tvStatus.text = "Point enregistré"
            } catch (e: Exception) {
                tvStatus.text = "Erreur CSV: ${e.message}"
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            mainScope.cancel()
            try { csvWriter.close() } catch (_: Exception) {}
        }

        data class Position(val lat: Double, val lon: Double, val alt: Double, val timestamp: Date = Date())

        private fun nmeaToDecimal(value:String?, hemi:String?): Double {
            if (value.isNullOrEmpty()) return 0.0
            val dot = value.indexOf('.')
            val degPartLen = (if (dot > 2) dot else value.length) - 2
            val degStr = value.substring(0, degPartLen)
            val minStr = value.substring(degPartLen)
            val deg = degStr.toDoubleOrNull() ?: 0.0
            val min = minStr.toDoubleOrNull() ?: 0.0
            var dec = deg + (min / 60.0)
            if (hemi == "S" || hemi == "W") dec = -dec
            return dec
        }

        private fun nmeaTimeToDate(timeStr: String): Date {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            try {
                val hh = timeStr.substring(0,2).toInt()
                val mm = timeStr.substring(2,4).toInt()
                val ss = timeStr.substring(4,6).toInt()
                cal.set(Calendar.HOUR_OF_DAY, hh)
                cal.set(Calendar.MINUTE, mm)
                cal.set(Calendar.SECOND, ss)
            } catch (_: Exception) {}
            return cal.time
        }

        private fun nmeaRmcToDate(timeStr: String, dateStr: String): Date {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            try {
                val dd = dateStr.substring(0,2).toInt()
                val mm = dateStr.substring(2,4).toInt()
                val yy = dateStr.substring(4,6).toInt() + 2000
                val hh = timeStr.substring(0,2).toInt()
                val min = timeStr.substring(2,4).toInt()
                val ss = timeStr.substring(4,6).toInt()
                cal.set(yy, mm-1, dd, hh, min, ss)
            } catch (_: Exception) {}
            return cal.time
        }
    }

package com.example.mousecontrollemobile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private var pcIpAddress = "192.168.0.10"
    private val pcPort = 5005
    private var sensitivity = 1.0f

    private var lastX = 0f
    private var lastY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Загружаем сохраненные настройки
        val prefs = getSharedPreferences("RemotePadPrefs", Context.MODE_PRIVATE)
        pcIpAddress = prefs.getString("ip_address", "192.168.0.10") ?: "192.168.0.10"
        val savedSens = prefs.getInt("sensitivity_val", 10)
        sensitivity = savedSens / 10f

        val touchPad = findViewById<View>(R.id.touchPadView)
        val btnLeft = findViewById<Button>(R.id.btnLeftClick)
        val btnRight = findViewById<Button>(R.id.btnRightClick)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        // Переменные для фиксации времени и координат тапа (добавь их внутри onCreate перед touchPad)
        var startClickTime = 0L
        val maxClickDuration = 200 // Максимальная длительность тапа в миллисекундах
        val maxMoveDistance = 10 // Максимальное смещение пальца, чтобы оно еще считалось тапом
        var startX = 0f
        var startY = 0f

        // Обработка движений и тапов по тачпаду
        touchPad.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    startX = event.x
                    startY = event.y
                    startClickTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = ((event.x - lastX) * sensitivity).toInt()
                    val deltaY = ((event.y - lastY) * sensitivity).toInt()

                    if (deltaX != 0 || deltaY != 0) {
                        sendUdpMessage("$deltaX,$deltaY")
                    }
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - startClickTime
                    // Считаем расстояние между точкой нажатия и точкой отпускания пальца
                    val absX = Math.abs(event.x - startX)
                    val absY = Math.abs(event.y - startY)

                    // Если палец находился на экране меньше 200мс и почти не сдвинулся — это ТАП!
                    if (clickDuration < maxClickDuration && absX < maxMoveDistance && absY < maxMoveDistance) {
                        sendUdpMessage("click")
                    }
                }
            }
            true
        }

        // Клики мыши
        btnLeft.setOnClickListener { sendUdpMessage("click") }
        btnRight.setOnClickListener { sendUdpMessage("r_click") }

        // Красивое всплывающее окно настроек при нажатии на шестеренку
        btnSettings.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
            val etDialogIp = dialogView.findViewById<EditText>(R.id.etDialogIp)
            val sbDialogSensitivity = dialogView.findViewById<SeekBar>(R.id.sbDialogSensitivity)

            // Подставляем текущие значения
            etDialogIp.setText(pcIpAddress)
            sbDialogSensitivity.progress = (sensitivity * 10).toInt()

            AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Сохранить") { _, _ ->
                    val inputIp = etDialogIp.text.toString().trim()
                    if (inputIp.isNotEmpty()) {
                        pcIpAddress = inputIp
                        val progress = sbDialogSensitivity.progress
                        sensitivity = progress / 10f

                        // Сохраняем в постоянную память смартфона
                        prefs.edit().apply {
                            putString("ip_address", pcIpAddress)
                            putInt("sensitivity_val", progress)
                            apply()
                        }
                        Toast.makeText(this, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    // Отправка UDP пакетов на ПК
    private fun sendUdpMessage(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(pcIpAddress)
                val bytes = message.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, address, pcPort)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
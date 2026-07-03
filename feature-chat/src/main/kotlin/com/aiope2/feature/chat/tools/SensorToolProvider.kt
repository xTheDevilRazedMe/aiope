package com.aiope2.feature.chat.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Provides access to device sensors: accelerometer, gyroscope, magnetometer,
 * barometer, light, proximity, temperature, humidity, step counter.
 */
class SensorToolProvider(private val ctx: Context) {
  private val TAG = "SensorTool"
  private val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager

  /** List all available sensors */
  fun listSensors(): String {
    val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
    return buildString {
      appendLine("=== Available Sensors (${sensors.size}) ===")
      sensors.groupBy { it.type }.forEach { (type, list) ->
        val name = sensorTypeName(type)
        val s = list.first()
        appendLine("- $name (type=$type, vendor=${s.vendor}, ver=${s.version}, res=${s.resolution})")
      }
    }
  }

  /** Read from a specific sensor */
  fun readSensor(sensorType: String, durationMs: Long = 1000, samples: Int = 10): String {
    val type = parseSensorType(sensorType)
    if (type < 0) return "Unknown sensor type: $sensorType. Use list_sensors to see available types."
    
    val sensor = sensorManager.getDefaultSensor(type)
      ?: return "Sensor not available on this device: $sensorType"

    return try {
      val readings = mutableListOf<Triple<Float, Float, Float>>()
      val latch = CountDownLatch(1)
      var count = 0
      
      val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          when (event.values.size) {
            1 -> readings.add(Triple(event.values[0], 0f, 0f))
            2 -> readings.add(Triple(event.values[0], event.values[1], 0f))
            else -> readings.add(Triple(event.values[0], event.values[1], event.values[2]))
          }
          count++
          if (count >= samples) latch.countDown()
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
      }
      
      sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
      val gotData = latch.await(durationMs, TimeUnit.MILLISECONDS)
      sensorManager.unregisterListener(listener)
      
      if (readings.isEmpty()) {
        "No readings from $sensorType. Try a longer duration."
      } else {
        val avgX = readings.map { it.first }.average()
        val avgY = readings.map { it.second }.average()
        val avgZ = readings.map { it.third }.average()
        
        buildString {
          appendLine("=== $sensorType Readings (${readings.size} samples) ===")
          appendLine("Average: x=${"%.3f".format(avgX)}, y=${"%.3f".format(avgY)}, z=${"%.3f".format(avgZ)}")
          if (readings.size == 1) {
            appendLine("Value: ${readings.first().first}")
          }
          // For accelerometer, calculate total acceleration
          if (type == Sensor.TYPE_ACCELEROMETER) {
            val magnitude = sqrt(avgX * avgX + avgY * avgY + avgZ * avgZ)
            appendLine("Total acceleration: ${"%.3f".format(magnitude)} m/s²")
          }
          appendLine("(Last: ${readings.last()})")
        }
      }
    } catch (e: Exception) {
      "Error reading sensor: ${e.message}"
    }
  }

  /** Get device orientation using rotation vector or accelerometer + magnetometer */
  fun getOrientation(): String {
    return try {
      // Try rotation vector first (most accurate)
      val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
      if (rotationSensor != null) {
        val latch = CountDownLatch(1)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        
        val listener = object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            latch.countDown()
          }
          override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL)
        latch.await(2000, TimeUnit.MILLISECONDS)
        sensorManager.unregisterListener(listener)
        
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
        val pitch = Math.toDegrees(orientationAngles[1].toDouble())
        val roll = Math.toDegrees(orientationAngles[2].toDouble())
        
        buildString {
          appendLine("=== Device Orientation ===")
          appendLine("Azimuth: ${"%.1f".format(azimuth)}° (${compassDirection(azimuth)})")
          appendLine("Pitch: ${"%.1f".format(pitch)}°")
          appendLine("Roll: ${"%.1f".format(roll)}°")
        }
      } else {
        "Rotation vector sensor not available. Try read_sensor with accelerometer."
      }
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  /** Get step count if available */
  fun getStepCount(): String {
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
      ?: return "Step counter not available on this device."
    return readSensor("step_counter", 500, 1)
  }

  private fun compassDirection(azimuth: Double): String {
    val normalized = ((azimuth + 360) % 360)
    return when {
      normalized >= 337.5 || normalized < 22.5 -> "N"
      normalized < 67.5 -> "NE"
      normalized < 112.5 -> "E"
      normalized < 157.5 -> "SE"
      normalized < 202.5 -> "S"
      normalized < 247.5 -> "SW"
      normalized < 292.5 -> "W"
      else -> "NW"
    }
  }

  private fun parseSensorType(name: String): Int {
    return when (name.lowercase().replace(" ", "_")) {
      "accelerometer" -> Sensor.TYPE_ACCELEROMETER
      "gyroscope" -> Sensor.TYPE_GYROSCOPE
      "magnetometer", "magnetic_field" -> Sensor.TYPE_MAGNETIC_FIELD
      "light" -> Sensor.TYPE_LIGHT
      "proximity" -> Sensor.TYPE_PROXIMITY
      "pressure", "barometer" -> Sensor.TYPE_PRESSURE
      "temperature" -> Sensor.TYPE_AMBIENT_TEMPERATURE
      "humidity" -> Sensor.TYPE_RELATIVE_HUMIDITY
      "step_counter" -> Sensor.TYPE_STEP_COUNTER
      "step_detector" -> Sensor.TYPE_STEP_DETECTOR
      "heart_rate" -> Sensor.TYPE_HEART_RATE
      "rotation_vector" -> Sensor.TYPE_ROTATION_VECTOR
      "game_rotation_vector" -> Sensor.TYPE_GAME_ROTATION_VECTOR
      "gravity" -> Sensor.TYPE_GRAVITY
      "linear_acceleration" -> Sensor.TYPE_LINEAR_ACCELERATION
      "significant_motion" -> Sensor.TYPE_SIGNIFICANT_MOTION
      else -> -1
    }
  }

  private fun sensorTypeName(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
    Sensor.TYPE_GYROSCOPE -> "Gyroscope"
    Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
    Sensor.TYPE_LIGHT -> "Light"
    Sensor.TYPE_PROXIMITY -> "Proximity"
    Sensor.TYPE_PRESSURE -> "Barometer"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
    Sensor.TYPE_STEP_COUNTER -> "Step Counter"
    Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
    Sensor.TYPE_HEART_RATE -> "Heart Rate"
    Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
    Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"
    Sensor.TYPE_GRAVITY -> "Gravity"
    Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
    Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
    else -> "Unknown($type)"
  }
}

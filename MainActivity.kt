
package com.example.cw1

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.cw1.ui.theme.CW1Theme
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var darkTheme by remember { mutableStateOf(false) }

            CW1Theme(darkTheme = darkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SensorsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onLightChanged = { lux ->
                            if (!darkTheme && lux < 20f) {
                                darkTheme = true
                            } else if (darkTheme && lux > 60f) {
                                darkTheme = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SensorsScreen(
    modifier: Modifier = Modifier,
    onLightChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // Three sensors
    val lightSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    val rotationSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    val accelerometer = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    // Sensor values
    var light by remember { mutableStateOf<Float?>(null) }
    var rotation by remember { mutableStateOf<Float?>(null) }
    var isLandscape by remember { mutableStateOf(false) }
    var shakeCount by remember { mutableIntStateOf(0) }

    DisposableEffect(lightSensor, rotationSensor, accelerometer) {

        var previousMagnitude: Float? = null
        var lastShakeTime = 0L

        val listener = object : SensorEventListener {

            override fun onSensorChanged(event: SensorEvent) {

                when (event.sensor.type) {

                    // 1. Light sensor
                    Sensor.TYPE_LIGHT -> {
                        light = event.values[0]
                    }

                    // 2. Rotation sensor
                    Sensor.TYPE_ROTATION_VECTOR -> {

                        val rotationMatrix = FloatArray(9)
                        val orientationAngles = FloatArray(3)

                        SensorManager.getRotationMatrixFromVector(
                            rotationMatrix,
                            event.values
                        )

                        SensorManager.getOrientation(
                            rotationMatrix,
                            orientationAngles
                        )

                        // Roll angle in degrees
                        val roll = Math.toDegrees(
                            orientationAngles[2].toDouble()
                        ).toFloat()

                        rotation = roll

                        // Detect landscape orientation
                        isLandscape = abs(roll) > 45f
                    }

                    // 3. Accelerometer: detect phone shakes
                    Sensor.TYPE_ACCELEROMETER -> {

                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]

                        // Calculate acceleration magnitude
                        val magnitude = sqrt(
                            x * x + y * y + z * z
                        )

                        val previous = previousMagnitude

                        if (previous != null) {

                            val difference = abs(magnitude - previous)
                            val currentTime = SystemClock.elapsedRealtime()

                            // Count a shake if acceleration changes
                            // significantly, with a cooldown.
                            if (
                                difference > 3.5f &&
                                currentTime - lastShakeTime > 500
                            ) {
                                shakeCount++
                                lastShakeTime = currentTime

                                Log.d("Sensors", "Shake detected!")
                            }
                        }

                        previousMagnitude = magnitude
                    }
                }
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                accuracy: Int
            ) {}
        }

        fun registerSensor(sensor: Sensor?, name: String) {
            if (sensor != null) {
                sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                Log.d("Sensors", "$name registered")
            } else {
                Log.w("Sensors", "$name not available")
            }
        }

        registerSensor(lightSensor, "Light sensor")
        registerSensor(rotationSensor, "Rotation sensor")
        registerSensor(accelerometer, "Accelerometer")

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Update theme when light changes
    LaunchedEffect(light) {
        light?.let(onLightChanged)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {

        if (isLandscape) {

            // Landscape: arrange cards horizontally
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                SensorCard(
                    title = "Light",
                    value = light?.let {
                        "%.1f lx".format(it)
                    } ?: "-- lx",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                SensorCard(
                    title = "Rotation",
                    value = rotation?.let {
                        "%.1f°".format(it)
                    } ?: "--°",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                SensorCard(
                    title = "Shake Counter",
                    value = shakeCount.toString(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

        } else {

            // Portrait: arrange cards vertically
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    "Sensor Dashboard",
                    style = MaterialTheme.typography.headlineMedium
                )

                SensorCard(
                    title = "Light Sensor",
                    value = light?.let {
                        "%.1f lx".format(it)
                    } ?: "-- lx"
                )

                SensorCard(
                    title = "Rotation Sensor",
                    value = rotation?.let {
                        "%.1f°".format(it)
                    } ?: "--°"
                )

                SensorCard(
                    title = "Shake Counter",
                    value = shakeCount.toString()
                )
            }
        }
    }
}

@Composable
fun SensorCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                value,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

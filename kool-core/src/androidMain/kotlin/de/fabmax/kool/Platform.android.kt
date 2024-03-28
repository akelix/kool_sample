package de.fabmax.kool

import android.app.Activity
import de.fabmax.kool.math.clamp
import de.fabmax.kool.platform.KoolContextAndroid
import java.util.*

actual fun Double.toString(precision: Int): String = "%.${precision.clamp(0, 12)}f".format(Locale.ENGLISH, this)

val KoolSystem.configAndroid: KoolConfigAndroid get() = config as KoolConfigAndroid

actual fun defaultKoolConfig(): KoolConfig = KoolConfigAndroid()

actual fun createContext(config: KoolConfig): KoolContext {
    KoolSystem.initialize(config)
    return KoolContextAndroid(config as KoolConfigAndroid)
}

fun Activity.createContextAndroid(config: KoolConfigAndroid = KoolConfigAndroid()): KoolContextAndroid {
    val configWithContext = config.copy(appContext = this)
    val ctx = createContext(configWithContext)
    return ctx as KoolContextAndroid
}

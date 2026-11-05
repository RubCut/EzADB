package dev.ezadb.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Expressive motion tokens.
 *
 * Material 3 Expressive's official MotionScheme is still internal in the stable
 * material3 artifact used here, so EzADB provides equivalent spring-based tokens
 * on top of stable Compose APIs. They are intentionally bouncy for spatial
 * changes and short for effects, and they collapse to snap() when the user has
 * disabled animations at the system level.
 */
/** Non-composable variants for transitionSpec lambdas (NavHost / AnimatedContent). */
object EzMotionSpecs {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 900f)
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    fun <T> fade(): FiniteAnimationSpec<T> = tween(durationMillis = 220, easing = FastOutSlowInEasing)
}

object EzMotion {
    @Composable
    fun reduced(): Boolean {
        if (LocalInspectionMode.current) return false
        val context = LocalContext.current
        return runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    @Composable
    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else spring(dampingRatio = 0.72f, stiffness = 900f)

    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

    @Composable
    fun <T> slowSpatial(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)

    @Composable
    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f)

    @Composable
    fun <T> effects(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    @Composable
    fun <T> fade(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else tween(durationMillis = 220, easing = FastOutSlowInEasing)
}

/*
 * Nomacast addition: blurred, dimmed backdrop behind an open folder (HyperOS-like).
 *
 * The folder panel lives in the drag layer, so blurring the views underneath it
 * (workspace + hotseat on the home screen, the apps list in the drawer) gives a
 * frosted backdrop without touching the folder itself. Uses RenderEffect, so it is
 * GPU-only, costs nothing while no folder is open and is skipped below Android 12.
 */
package com.android.launcher3.folder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.util.Log
import android.view.animation.PathInterpolator
import java.lang.ref.WeakReference
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState

object FolderBackdrop {

    private const val TAG = "FolderBackdrop"

    /** Blur radius in dp once the folder is fully open. */
    private const val MAX_BLUR_DP = 22f

    /** Black overlay alpha applied to the blurred content once fully open. */
    private const val MAX_DIM = 0.22f

    private const val OPEN_DURATION_MS = 240L
    private const val CLOSE_DURATION_MS = 180L

    private val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** Views currently carrying the backdrop effect (captured when the folder opens). */
    private var activeTargets: List<View> = emptyList()

    /** Folder that owns the current backdrop, so a closing folder can't wipe a newer one. */
    private var owner: WeakReference<Folder>? = null

    /**
     * True while a folder backdrop is shown. The depth controller checks it so it does not
     * wipe the backdrop when the folder animation nudges the wallpaper zoom.
     */
    @JvmStatic
    fun isActive(): Boolean = activeTargets.isNotEmpty()

    private fun targetsFor(folder: Folder): List<View> {
        val launcher = folder.mActivityContext as? Launcher ?: return emptyList()
        return if (launcher.isInState(LauncherState.ALL_APPS)) {
            listOfNotNull(launcher.appsView)
        } else {
            launcher.depthBlurTargets
        }
    }

    /**
     * Returns an animator that fades the backdrop in (opening) or out (closing), or null when
     * the effect is unsupported (pre Android 12, or not hosted by the main launcher).
     */
    @JvmStatic
    fun createAnimator(folder: Folder, isOpening: Boolean): Animator? = try {
        createAnimatorInternal(folder, isOpening)
    } catch (t: Throwable) {
        Log.e(TAG, "Could not create backdrop animator", t)
        null
    }

    private fun createAnimatorInternal(folder: Folder, isOpening: Boolean): Animator? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val views = if (isOpening || activeTargets.isEmpty()) targetsFor(folder) else activeTargets
        if (views.isEmpty()) return null
        val maxRadius = MAX_BLUR_DP * folder.resources.displayMetrics.density

        return ValueAnimator.ofFloat(if (isOpening) 0f else 1f, if (isOpening) 1f else 0f)
            .apply {
                duration = if (isOpening) OPEN_DURATION_MS else CLOSE_DURATION_MS
                interpolator = EMPHASIZED
                addUpdateListener {
                    try {
                        apply(views, it.animatedValue as Float, maxRadius)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Backdrop update failed", t)
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        if (isOpening) {
                            activeTargets = views
                            owner = WeakReference(folder)
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!isOpening) clear(folder)
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        if (!isOpening) clear(folder)
                    }
                })
            }
    }

    /**
     * Removes the backdrop immediately (e.g. the folder closed without animation). Ignored if
     * another folder has since taken over the backdrop.
     */
    @JvmStatic
    fun clear(folder: Folder) {
        val current = owner?.get()
        if (current != null && current !== folder) return
        val views = activeTargets
        activeTargets = emptyList()
        owner = null
        clearViews(views)
    }

    private fun clearViews(views: List<View>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            views.forEach { it.setRenderEffect(null) }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not clear backdrop", t)
        }
    }

    private fun apply(views: List<View>, progress: Float, maxRadius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val radius = maxRadius * progress
        if (radius < 0.5f) {
            clearViews(views)
            return
        }
        val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        val dimAlpha = (MAX_DIM * progress * 255).toInt().coerceIn(0, 255)
        val dim = RenderEffect.createColorFilterEffect(
            PorterDuffColorFilter(Color.argb(dimAlpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP),
        )
        val effect = RenderEffect.createChainEffect(dim, blur)
        views.forEach { it.setRenderEffect(effect) }
    }
}

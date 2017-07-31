package de.fabmax.kool.scene

import de.fabmax.kool.InputManager
import de.fabmax.kool.platform.Math
import de.fabmax.kool.platform.RenderContext
import de.fabmax.kool.util.*

/**
 * A special kind of transform group which translates mouse input into a spherical transform. This is mainly useful
 * for camera manipulation.
 *
 * @author fabmax
 */

fun sphericalInputTransform(name: String? = null, block: SphericalInputTransform.() -> Unit): SphericalInputTransform {
    val sit = SphericalInputTransform(name)
    sit.block()
    return sit
}

open class SphericalInputTransform(name: String? = null) : TransformGroup(name), InputManager.DragHandler {

    var leftDragMethod = DragMethod.ROTATE
    var middleDragMethod = DragMethod.NONE
    var rightDragMethod = DragMethod.PAN
    var zoomMethod = ZoomMethod.ZOOM_TRANSLATE

    var verticalAxis = Vec3f.Y_AXIS
    var horizontalAxis = Vec3f.X_AXIS
    var minHorizontalRot = -90f
    var maxHorizontalRot = 90f

    val translation = MutableVec3f()
    var verticalRotation = 0f
    var horizontalRotation = 0f
    var zoom = 10f
        set(value) { field = Math.clamp(value, minZoom, maxZoom) }

    var minZoom = 1f
    var maxZoom = 100f
    var translationBounds: BoundingBox? = null

    var panMethod: PanBase = yPlanePan()

    var stiffness = 0f
    var damping = 0f

    val vertRotAnimator = AnimatedVal(0f)
    val horiRotAnimator = AnimatedVal(0f)
    val zoomAnimator = AnimatedVal(zoom)

    private var prevButtonMask = 0
    private var dragMethod = DragMethod.NONE
    private var dragStart = false
    private val deltaPos = MutableVec2f()
    private var deltaScroll = 0f

    private val ptrPos = MutableVec2f()
    private val panPlane = Plane()
    private val pointerHitStart = MutableVec3f()
    private val pointerHit = MutableVec3f()
    private val tmpVec1 = MutableVec3f()
    private val tmpVec2 = MutableVec3f()

    private val mouseTransform = Mat4f()
    private val mouseTransformInv = Mat4f()

    var smoothness: Float = 0f
        set(value) {
            field = value
            if (!Math.isZero(value)) {
                stiffness = 50.0f / value
                damping = 2f * Math.sqrt(stiffness.toDouble()).toFloat()
            }
        }

    init {
        smoothness = 0.5f
        panPlane.p.set(Vec3f.ZERO)
        panPlane.n.set(Vec3f.Y_AXIS)
    }

    fun setMouseRotation(vertical: Float, horizontal: Float) {
        vertRotAnimator.set(vertical)
        horiRotAnimator.set(horizontal)
        verticalRotation = vertical
        horizontalRotation = horizontal
    }

    fun setMouseTranslation(x: Float, y: Float, z: Float) {
        translation.set(x, y, z)
    }

    fun updateTransform() {
        translationBounds?.clampToBounds(translation)

        mouseTransform.invert(mouseTransformInv)
        mul(mouseTransformInv)

        val z = zoomAnimator.actual
        val vr = vertRotAnimator.actual
        val hr = horiRotAnimator.actual
        mouseTransform.setIdentity()
        mouseTransform.translate(translation.x, translation.y, translation.z)
        mouseTransform.scale(z, z, z)
        mouseTransform.rotate(vr, verticalAxis)
        mouseTransform.rotate(hr, horizontalAxis)
        mul(mouseTransform)

    }

    override fun render(ctx: RenderContext) {
        val scene = this.scene ?: return

        if (panMethod.computePanPoint(pointerHit, scene, ptrPos, ctx)) {
            if (dragStart) {
                dragStart = false
                pointerHitStart.set(pointerHit)

                // stop any ongoing smooth motion, as we start a new one
                stopSmoothMotion()

            } else if (dragMethod == DragMethod.PAN) {
                val s = Math.clamp(1 - smoothness, 0.1f, 1f)
                tmpVec1.set(pointerHitStart).subtract(pointerHit).scale(s)

                // limit panning speed
                val tLen = tmpVec1.length()
                if (tLen > scene.camera.globalRange * 0.5f) {
                    tmpVec1.scale(scene.camera.globalRange * 0.5f / tLen)
                }

                translation.add(tmpVec1)
            }
        } else {
            pointerHit.set(scene.camera.globalLookAt)
        }

        if (!Math.isZero(deltaScroll)) {
            zoom *= 1f - deltaScroll / 10f
            deltaScroll = 0f
        }

        if (dragMethod == DragMethod.ROTATE) {
            verticalRotation -= deltaPos.x / 3
            horizontalRotation -= deltaPos.y / 3
            horizontalRotation = Math.clamp(horizontalRotation, minHorizontalRot, maxHorizontalRot)
            deltaPos.set(Vec2f.ZERO)
        }

        vertRotAnimator.desired = verticalRotation
        horiRotAnimator.desired = horizontalRotation
        zoomAnimator.desired = zoom

        val oldZ = zoomAnimator.actual
        val z = zoomAnimator.animate(ctx.deltaT)
        if (!Math.isEqual(oldZ, z) && zoomMethod == ZoomMethod.ZOOM_TRANSLATE) {
            computeZoomTranslationPerspective(scene, oldZ, z)
        }

        vertRotAnimator.animate(ctx.deltaT)
        horiRotAnimator.animate(ctx.deltaT)
        updateTransform()

        super.render(ctx)
    }

    /**
     * Computes the required camera translation so that the camera zooms to the point under the pointer (only works
     * with perspective cameras)
     */
    protected open fun computeZoomTranslationPerspective(scene: Scene, oldZoom: Float, newZoom: Float) {
        // tmpVec1 = zoomed pos on pointer ray
        scene.camera.globalPos.subtract(pointerHit, tmpVec1).scale(newZoom / oldZoom).add(pointerHit)
        // tmpVec2 = zoomed pos on view center ray
        scene.camera.globalPos.subtract(scene.camera.globalLookAt, tmpVec2).scale(newZoom / oldZoom)
                .add(scene.camera.globalLookAt)
        translation.add(tmpVec1).subtract(tmpVec2)
    }

    private fun stopSmoothMotion() {
        vertRotAnimator.set(vertRotAnimator.actual)
        horiRotAnimator.set(horiRotAnimator.actual)
        zoomAnimator.set(zoomAnimator.actual)

        verticalRotation = vertRotAnimator.actual
        horizontalRotation = horiRotAnimator.actual
        zoom = zoomAnimator.actual
    }

    override fun onSceneChanged(oldScene: Scene?, newScene: Scene?) {
        super.onSceneChanged(oldScene, newScene)
        oldScene?.removeDragHandler(this)
        newScene?.registerDragHandler(this)
    }

    override fun handleDrag(dragPtrs: List<InputManager.Pointer>, ctx: RenderContext): Int {
        if (dragPtrs.size == 1 && dragPtrs[0].isInViewport(ctx)) {
            if (dragPtrs[0].buttonEventMask != 0 || dragPtrs[0].buttonMask != prevButtonMask) {
                if (dragPtrs[0].isLeftButtonDown) {
                    dragMethod = leftDragMethod
                } else if (dragPtrs[0].isRightButtonDown) {
                    dragMethod = rightDragMethod
                } else if (dragPtrs[0].isMiddleButtonDown) {
                    dragMethod = middleDragMethod
                } else {
                    dragMethod = DragMethod.NONE
                }
                dragStart = dragMethod != DragMethod.NONE
            }

            prevButtonMask = dragPtrs[0].buttonMask
            ptrPos.set(dragPtrs[0].x, dragPtrs[0].y)
            deltaPos.set(dragPtrs[0].deltaX, dragPtrs[0].deltaY)
            deltaScroll = dragPtrs[0].deltaScroll

        } else {
            deltaPos.set(Vec2f.ZERO)
            deltaScroll = 0f
        }
        // let other drag handlers do their job
        return 0
    }

    enum class DragMethod {
        NONE,
        ROTATE,
        PAN
    }

    enum class ZoomMethod {
        ZOOM_CENTER,
        ZOOM_TRANSLATE
    }

    inner class AnimatedVal(value: Float) {
        var desired = value
        var actual = value
        var speed = 0f

        fun set(value: Float) {
            desired = value
            actual = value
            speed = 0f
        }

        fun animate(deltaT: Float): Float {
            if (Math.isZero(smoothness) || deltaT > 0.2f) {
                // don't care about smoothing on low frame rates
                actual = desired
                return actual
            }

            var t = 0f
            while (t < deltaT) {
                // with js math library there is no min for Float?!
                val dt = Math.min(0.05, (deltaT - t).toDouble()).toFloat()
                t += dt + 0.001f

                val err = desired - actual
                speed += (err * stiffness - speed * damping) * dt
                actual += speed * dt
            }
            return actual
        }
    }
}

abstract class PanBase {
    abstract fun computePanPoint(result: MutableVec3f,
                                 scene: Scene, ptrPos: Vec2f, ctx: RenderContext): Boolean
}

class CameraOrthogonalPan : PanBase() {
    val panPlane = Plane()
    private val pointerRay = Ray()

    override fun computePanPoint(result: MutableVec3f,
                                   scene: Scene, ptrPos: Vec2f, ctx: RenderContext): Boolean {
        panPlane.p.set(scene.camera.globalLookAt)
        panPlane.n.set(scene.camera.globalLookDir)
        return scene.camera.computePickRay(pointerRay, ptrPos.x, ptrPos.y, ctx) &&
                panPlane.intersectionPoint(result, pointerRay)
    }
}

class FixedPlanePan(planeNormal: Vec3f) : PanBase() {
    val panPlane = Plane()
    private val pointerRay = Ray()

    init {
        panPlane.n.set(planeNormal)
    }

    override fun computePanPoint(result: MutableVec3f,
                                 scene: Scene, ptrPos: Vec2f, ctx: RenderContext): Boolean {
        panPlane.p.set(scene.camera.globalLookAt)
        return scene.camera.computePickRay(pointerRay, ptrPos.x, ptrPos.y, ctx) &&
                panPlane.intersectionPoint(result, pointerRay)
    }
}

fun xPlanePan() = FixedPlanePan(Vec3f.X_AXIS)
fun yPlanePan() = FixedPlanePan(Vec3f.Y_AXIS)
fun zPlanePan() = FixedPlanePan(Vec3f.Z_AXIS)

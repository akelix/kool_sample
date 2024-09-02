package de.fabmax.kool.scene

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.CursorMode
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.input.PointerState
import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBoxD
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.scene.animation.ExponentialDecayDouble
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.Viewport
import kotlin.math.abs

/**
 * A special kind of transform group which translates mouse input into a orbit transform. This is mainly useful
 * for camera manipulation.
 *
 * @author fabmax
 */

fun orbitCamera(view: RenderPass.View, name: String? = null, block: OrbitInputTransform.() -> Unit): OrbitInputTransform {
    val orbitCam = OrbitInputTransform(name)
    orbitCam.addNode(view.camera)
    orbitCam.block()
    view.drawNode.addNode(orbitCam)

    InputStack.defaultInputHandler.pointerListeners += orbitCam
    orbitCam.onRelease {
        InputStack.defaultInputHandler.pointerListeners -= orbitCam
    }
    return orbitCam
}

fun Scene.orbitCamera(name: String? = null, block: OrbitInputTransform.() -> Unit): OrbitInputTransform {
    return orbitCamera(mainRenderPass.screenView, name, block)
}

fun Scene.defaultOrbitCamera(yaw: Float = 20f, pitch: Float = -30f): OrbitInputTransform {
    return orbitCamera {
        setMouseRotation(yaw, pitch)
    }
}

open class OrbitInputTransform(name: String? = null) : Node(name), InputStack.PointerListener {
    var leftDragMethod = DragMethod.ROTATE
    var middleDragMethod = DragMethod.NONE
    var rightDragMethod = DragMethod.PAN
    var zoomMethod = ZoomMethod.ZOOM_CENTER
    var isConsumingPtr = true

    var verticalAxis = Vec3d.Y_AXIS
    var horizontalAxis = Vec3d.X_AXIS
    var minHorizontalRot = -90.0
    var maxHorizontalRot = 90.0

    val translation = MutableVec3d()
    var verticalRotation = 0.0
    var horizontalRotation = 0.0
    var zoom = 10.0
        set(value) {
            field = value.clamp(minZoom, maxZoom)
        }

    var isKeepingStandardTransform = false
    var isInfiniteDragCursor = false
    var isApplyTranslation = true

    var invertRotX = false
    var invertRotY = false

    var minZoom = 1.0
    var maxZoom = 100.0
    var translationBounds: BoundingBoxD? = null

    var panMethod: PanBase = CameraOrthogonalPan()

    val vertRotAnimator = ExponentialDecayDouble(0.0)
    val horiRotAnimator = ExponentialDecayDouble(0.0)
    val zoomAnimator = ExponentialDecayDouble(zoom)

    private var prevButtonMask = 0
    private var dragMethod = DragMethod.NONE
    private var prevDragMethod = DragMethod.NONE
    private var dragStart = false
    private val deltaPos = MutableVec2d()
    private var deltaScroll = 0.0
    private val panStartTranslation = MutableVec3d()

    private val ptrPos = MutableVec2d()
    private val panPlane = PlaneD()
    private val pointerHitStart = MutableVec3d()
    private val pointerHit = MutableVec3d()
    private val tmpVec1 = MutableVec3d()
    private val tmpVec2 = MutableVec3d()

    private val mouseTransform = MutableMat4d()
    private val mouseTransformInv = MutableMat4d()

    private val matrixTransform: MatrixTransformD
        get() = transform as MatrixTransformD

    var panSmoothness: Double by panMethod::panDecay
    var zoomRotationDecay: Double = 0.0
        set(value) {
            field = value
            vertRotAnimator.decay = value
            horiRotAnimator.decay = value
            zoomAnimator.decay = value
        }

    init {
        transform = MatrixTransformD()
        zoomRotationDecay = 16.0
        panPlane.p.set(Vec3f.ZERO)
        panPlane.n.set(Vec3f.Y_AXIS)

        onUpdate += { ev ->
            doCamTransform(ev.view)
        }
    }

    fun setMouseRotation(yaw: Float, pitch: Float) = setMouseRotation(yaw.toDouble(), pitch.toDouble())

    fun setMouseRotation(yaw: Double, pitch: Double) {
        vertRotAnimator.set(yaw)
        horiRotAnimator.set(pitch)
        verticalRotation = yaw
        horizontalRotation = pitch
    }

    fun setMouseTranslation(x: Float, y: Float, z: Float) = setMouseTranslation(x.toDouble(), y.toDouble(), z.toDouble())

    fun setMouseTranslation(x: Double, y: Double, z: Double) {
        translation.set(x, y, z)
    }

    fun setZoom(newZoom: Float) = setZoom(newZoom.toDouble())

    fun setZoom(newZoom: Double, min: Double = minZoom, max: Double = maxZoom) {
        zoom = newZoom
        zoomAnimator.set(zoom)
        minZoom = min
        maxZoom = max
    }

    fun updateTransform() {
        translationBounds?.clampToBounds(translation)

        if (isKeepingStandardTransform) {
            mouseTransform.invert(mouseTransformInv)
            matrixTransform.mul(mouseTransformInv)
        }

        val z = zoomAnimator.actual
        val vr = vertRotAnimator.actual
        val hr = horiRotAnimator.actual
        mouseTransform.setIdentity()
        if (isApplyTranslation) {
            mouseTransform.translate(translation.x, translation.y, translation.z)
        }
        mouseTransform.scale(z)
        mouseTransform.rotate(vr.deg, verticalAxis)
        mouseTransform.rotate(hr.deg, horizontalAxis)

        if (isKeepingStandardTransform) {
            matrixTransform.mul(mouseTransform)
        } else {
            matrixTransform.setMatrix(mouseTransform)
        }
    }

    private fun doCamTransform(view: RenderPass.View) {
        if (dragMethod == DragMethod.PAN) {
            if (dragStart) {
                dragStart = false
                panMethod.startPan(view, ptrPos)
                pointerHitStart.set(pointerHit)
                panStartTranslation.set(translation)

                // stop any ongoing smooth motion, as we start a new one
                stopSmoothMotion()

            } else {
                tmpVec1.set(panMethod.computePan(view, ptrPos))
                parent?.toLocalCoords(tmpVec1, 0.0)
                translation.set(panStartTranslation).add(tmpVec1)
            }
        } else {
            pointerHit.set(view.camera.globalLookAt)
        }

        if (!deltaScroll.isFuzzyZero()) {
            zoom *= 1f - deltaScroll / 10f
            deltaScroll = 0.0
        }

        if (dragMethod == DragMethod.ROTATE) {
            verticalRotation -= deltaPos.x / 3 * if (invertRotX) { -1f } else { 1f }
            horizontalRotation -= deltaPos.y / 3 * if (invertRotY) { -1f } else { 1f }
            horizontalRotation = horizontalRotation.clamp(minHorizontalRot, maxHorizontalRot)
            deltaPos.set(Vec2d.ZERO)
        }

        vertRotAnimator.desired = verticalRotation
        horiRotAnimator.desired = horizontalRotation
        zoomAnimator.desired = zoom

        val oldZ = zoomAnimator.actual
        val z = zoomAnimator.animate(Time.deltaT)
        if (!isFuzzyEqual(oldZ, z) && zoomMethod == ZoomMethod.ZOOM_TRANSLATE) {
            panMethod.startPan(view, ptrPos)
            if (panMethod.computePanPoint(view, ptrPos, pointerHit)) {
                computeZoomTranslationPerspective(view.camera, oldZ, z)
            }
        }

        vertRotAnimator.animate(Time.deltaT)
        horiRotAnimator.animate(Time.deltaT)
        updateTransform()
    }

    /**
     * Computes the required camera translation so that the camera zooms to the point under the pointer (only works
     * with perspective cameras)
     */
    protected open fun computeZoomTranslationPerspective(camera: Camera, oldZoom: Double, newZoom: Double) {
        // tmpVec1 = zoomed pos on pointer ray
        val s = newZoom / oldZoom
        camera.dataD.globalPos.subtract(pointerHit, tmpVec1).mul(s).add(pointerHit)
        // tmpVec2 = zoomed pos on view center ray
        camera.dataD.globalPos.subtract(camera.dataD.globalLookAt, tmpVec2).mul(s)
            .add(camera.globalLookAt)
        tmpVec1.subtract(tmpVec2)
        parent?.toLocalCoords(tmpVec1, 0.0)
        translation.add(tmpVec1)
    }

    private fun stopSmoothMotion() {
        vertRotAnimator.set(vertRotAnimator.actual)
        horiRotAnimator.set(horiRotAnimator.actual)
        zoomAnimator.set(zoomAnimator.actual)

        verticalRotation = vertRotAnimator.actual
        horizontalRotation = horiRotAnimator.actual
        zoom = zoomAnimator.actual
    }

    override fun handlePointer(pointerState: PointerState, ctx: KoolContext) {
        if (!isVisible) {
            return
        }

        val dragPtr = pointerState.primaryPointer
        if (dragPtr.isConsumed()) {
            deltaPos.set(Vec2d.ZERO)
            deltaScroll = 0.0
            return
        }

        if (isInfiniteDragCursor) {
            if (dragPtr.isDrag && dragMethod != DragMethod.NONE && PointerInput.cursorMode != CursorMode.LOCKED) {
                PointerInput.cursorMode = CursorMode.LOCKED
            } else if (prevDragMethod != DragMethod.NONE && dragMethod == DragMethod.NONE) {
                PointerInput.cursorMode = CursorMode.NORMAL
            }
        }

        prevDragMethod = dragMethod
        if (dragPtr.buttonEventMask != 0 || dragPtr.buttonMask != prevButtonMask) {
            dragMethod = when {
                dragPtr.isLeftButtonDown -> leftDragMethod
                dragPtr.isRightButtonDown -> rightDragMethod
                dragPtr.isMiddleButtonDown -> middleDragMethod
                else -> DragMethod.NONE
            }
            dragStart = dragMethod != DragMethod.NONE
        }

        prevButtonMask = dragPtr.buttonMask
        ptrPos.set(dragPtr.x, dragPtr.y)
        deltaPos.set(dragPtr.deltaX, dragPtr.deltaY)
        deltaScroll = dragPtr.deltaScroll
        if (isConsumingPtr) {
            dragPtr.consume()
        }
    }

    private fun MutableVec3d.add(v: Vec3f) {
        x += v.x
        y += v.y
        z += v.z
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
}

abstract class PanBase {
    private var camFar = 1000.0
    private var isZeroToOneDepth = false
    private var isReverseDepth = false
    private val invViewProj = MutableMat4d()
    private val pointerRay = RayD()
    private val tmpVec3d = MutableVec3d()
    private val tmpVec4d = MutableVec4d()

    private val startPanPos = MutableVec3d()
    private val panPos = MutableVec3d()
    private val pan = MutableVec3d()

    var panDecay = 50.0

    open fun startPan(view: RenderPass.View, ptrPos: Vec2d) {
        camFar = view.camera.clipFar.toDouble()
        isZeroToOneDepth = view.camera.isZeroToOneDepth
        isReverseDepth = view.camera.isReverseDepthProjection
        invViewProj.set(view.camera.dataD.lazyInvViewProj.get())
        computePanPoint(view, ptrPos, startPanPos)
        pan.set(Vec3f.ZERO)
    }

    open fun computePan(view: RenderPass.View, ptrPos: Vec2d): Vec3d {
        if (computePanPoint(view, ptrPos, panPos)) {
            tmpVec3d.set(startPanPos).subtract(panPos)
            pan.expDecay(tmpVec3d, panDecay)
        }
        return pan
    }

    abstract fun computePanPoint(view: RenderPass.View, ptrPos: Vec2d, result: MutableVec3d): Boolean

    protected fun computePickRay(ptrPos: Vec2d, viewport: Viewport): RayD? {
        var valid = unProjectScreen(tmpVec3d.set(ptrPos.x, ptrPos.y, 0.0), viewport, pointerRay.origin)
        valid = valid && unProjectScreen(tmpVec3d.set(ptrPos.x, ptrPos.y, 1.0), viewport, pointerRay.direction)

        if (valid) {
            pointerRay.direction.subtract(pointerRay.origin)
            pointerRay.direction.norm()
            return pointerRay
        } else {
            return null
        }
    }

    private fun unProject(proj: Vec4d, viewport: Viewport, result: MutableVec3d): Boolean {
        val x = proj.x - viewport.x
        val y = viewport.y + viewport.height - proj.y

        tmpVec4d.set((2.0 * x / viewport.width - 1.0) * proj.w, (2.0 * y / viewport.height - 1.0) * proj.w, proj.z, proj.w)
        invViewProj.transform(tmpVec4d)
        val s = 1.0 / tmpVec4d.w
        result.set(tmpVec4d.x * s, tmpVec4d.y * s, tmpVec4d.z * s)
        return true
    }

    open fun unProjectScreen(screen: Vec3d, viewport: Viewport, result: MutableVec3d): Boolean {
        val viewX = screen.x - viewport.x
        val viewY = viewport.y + viewport.height - screen.y
        val x = 2f * viewX / viewport.width - 1f
        val y = 2f * viewY / viewport.height - 1f
        val z = if (isZeroToOneDepth) screen.z else 2f * screen.z - 1f

        if (isReverseDepth) {
            val w = camFar * z
            tmpVec4d.set(x * w, y * w, 1.0, w)
        } else {
            tmpVec4d.set(x, y, z, 1.0)
        }
        invViewProj.transform(tmpVec4d)
        val s = 1.0 / tmpVec4d.w
        result.set(tmpVec4d.x * s, tmpVec4d.y * s, tmpVec4d.z * s)
        return true
    }
}

class CameraOrthogonalPan : PanBase() {
    private val panPlane = PlaneD()

    override fun startPan(view: RenderPass.View, ptrPos: Vec2d) {
        panPlane.p.set(view.camera.globalLookAt)
        panPlane.n.set(view.camera.globalLookDir)
        super.startPan(view, ptrPos)
    }

    override fun computePanPoint(view: RenderPass.View, ptrPos: Vec2d, result: MutableVec3d): Boolean {
        val ray = computePickRay(ptrPos, view.viewport) ?: return false
        return panPlane.intersectionPoint(ray, result)
    }
}

class FixedPlanePan(planeNormal: Vec3f) : PanBase() {
    private val panPlane = PlaneD()

    private val prevPan = MutableVec3d()
    private val deltaPan = MutableVec3d()
    private val panLimited = MutableVec3d()

    init {
        panPlane.n.set(planeNormal)
    }

    override fun startPan(view: RenderPass.View, ptrPos: Vec2d) {
        panPlane.p.set(view.camera.globalLookAt)
        prevPan.set(Vec3f.ZERO)
        super.startPan(view, ptrPos)
    }

    override fun computePanPoint(view: RenderPass.View, ptrPos: Vec2d, result: MutableVec3d): Boolean {
        val ray = computePickRay(ptrPos, view.viewport) ?: return false
        if (abs(ray.direction dot panPlane.n) < 0.01) {
            return false
        }
        return panPlane.intersectionPoint(ray, result)
    }

    override fun computePan(view: RenderPass.View, ptrPos: Vec2d): Vec3d {
        panLimited.set(super.computePan(view, ptrPos))
        deltaPan.set(panLimited).subtract(prevPan)

        // limit panning speed
        val tLen = deltaPan.length()
        if (tLen > view.camera.globalRange * 0.5f) {
            deltaPan.mul(view.camera.globalRange * 0.5f / tLen)
            panLimited.set(prevPan).add(deltaPan)
        }
        prevPan.set(panLimited)

        return panLimited
    }
}

fun xPlanePan() = FixedPlanePan(Vec3f.X_AXIS)
fun yPlanePan() = FixedPlanePan(Vec3f.Y_AXIS)
fun zPlanePan() = FixedPlanePan(Vec3f.Z_AXIS)

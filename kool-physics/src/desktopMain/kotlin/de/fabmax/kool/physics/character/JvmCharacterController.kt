package de.fabmax.kool.physics.character

import de.fabmax.kool.math.*
import de.fabmax.kool.physics.*
import physx.character.*
import physx.common.PxVec3
import kotlin.math.acos
import kotlin.math.cos

class JvmCharacterController(
    private val pxController: PxCapsuleController,
    private val hitListener: ControllerHitListener,
    private val behaviorCallback: ControllerBahaviorCallback,
    manager: CharacterControllerManager,
    world: PhysicsWorld
) : CharacterController(manager, world) {

    private val bufPosition = MutableVec3d()
    private val bufPxPosition = PxExtendedVec3()
    private val bufPxVec3 = PxVec3()
    private val pxControllerFilters = PxControllerFilters()

    init {
        hitListener.controller = this
        behaviorCallback.controller = this
    }

    override var position: Vec3d
        get() = pxController.position.toVec3d(bufPosition)
        set(value) {
            pxController.position = value.toPxExtendedVec3(bufPxPosition)
            prevPosition.set(value)
        }

    override val actor: RigidDynamic = RigidDynamicImpl(1f, Mat4f.IDENTITY, false, pxController.actor)

    override var height: Float = pxController.height
        set(value) {
            field = value
            pxController.height = value
        }

    override var radius: Float = pxController.radius
        set(value) {
            field = value
            pxController.radius = value
        }

    override var slopeLimitDeg: Float = acos(pxController.slopeLimit).toDeg()
        set(value) {
            field = value
            pxController.slopeLimit = cos(value.toRad())
        }

    override var nonWalkableMode: NonWalkableMode = when (pxController.nonWalkableMode!!) {
            PxControllerNonWalkableModeEnum.ePREVENT_CLIMBING -> NonWalkableMode.PREVENT_CLIMBING
            PxControllerNonWalkableModeEnum.ePREVENT_CLIMBING_AND_FORCE_SLIDING -> NonWalkableMode.PREVENT_CLIMBING_AND_FORCE_SLIDING
        }
        set(value) {
            field = value
            pxController.nonWalkableMode = when (value) {
                NonWalkableMode.PREVENT_CLIMBING -> PxControllerNonWalkableModeEnum.ePREVENT_CLIMBING
                NonWalkableMode.PREVENT_CLIMBING_AND_FORCE_SLIDING -> PxControllerNonWalkableModeEnum.ePREVENT_CLIMBING_AND_FORCE_SLIDING
            }
        }

    override fun move(displacement: Vec3f, timeStep: Float) {
        val flags = pxController.move(displacement.toPxVec3(bufPxVec3), 0.001f, timeStep, pxControllerFilters)

        isDownCollision = flags.isSet(PxControllerCollisionFlagEnum.eCOLLISION_DOWN)
        isUpCollision = flags.isSet(PxControllerCollisionFlagEnum.eCOLLISION_UP)
        isSideCollision = flags.isSet(PxControllerCollisionFlagEnum.eCOLLISION_SIDES)
    }

    override fun resize(height: Float) {
        pxController.resize(height)
    }

    override fun release() {
        super.release()
        pxController.release()
        bufPxPosition.destroy()
        bufPxVec3.destroy()
        pxControllerFilters.destroy()
        hitListener.destroy()
        behaviorCallback.destroy()
    }
}
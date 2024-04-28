package de.fabmax.kool.editor.actions

import de.fabmax.kool.editor.components.RigidBodyComponent

class SetRigidBodyMassAction(
    private val editedRigidBodyComponent: RigidBodyComponent,
    private val oldMass: Float,
    private val newMass: Float
) : EditorAction {

    override fun doAction() {
        val props = editedRigidBodyComponent.bodyState.value.copy(mass = newMass)
        editedRigidBodyComponent.bodyState.set(props)
    }

    override fun undoAction() {
        val props = editedRigidBodyComponent.bodyState.value.copy(mass = oldMass)
        editedRigidBodyComponent.bodyState.set(props)
    }
}
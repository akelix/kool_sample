package de.fabmax.kool.app

import de.fabmax.kool.editor.api.ClassFactory
import de.fabmax.kool.editor.model.MProceduralMesh
import de.fabmax.kool.scene.Mesh

actual object AppClassFactory : ClassFactory {
    override fun createProceduralMesh(procMesh: MProceduralMesh): Mesh {
        return Class.forName(procMesh.generatorClass).getDeclaredConstructor().newInstance() as Mesh
    }
}
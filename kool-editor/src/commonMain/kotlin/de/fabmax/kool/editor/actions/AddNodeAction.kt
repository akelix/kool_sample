package de.fabmax.kool.editor.actions

import de.fabmax.kool.editor.KoolEditor
import de.fabmax.kool.editor.data.SceneNodeData
import de.fabmax.kool.editor.model.NodeModel
import de.fabmax.kool.editor.model.SceneModel
import de.fabmax.kool.editor.model.SceneNodeModel
import de.fabmax.kool.util.launchOnMainThread

class AddNodeAction(
    val addNodeDatas: List<SceneNodeData>,
    parent: NodeModel,
    sceneModel: SceneModel
) : EditorAction {

    private val parentId = parent.nodeId
    private val sceneId = sceneModel.nodeId
    private val parentModel: NodeModel? get() {
        val scene = sceneModel(sceneId)
        return if (parentId == sceneId) scene else scene?.nodeModels?.get(parentId)
    }

    override fun doAction() {
        val scene = sceneModel(sceneId) ?: return
        val parent = parentModel ?: return

        launchOnMainThread {
            addNodeDatas.forEach {
                scene.addSceneNode(SceneNodeModel(it, parent, scene))
            }
            KoolEditor.instance.ui.sceneBrowser.refreshSceneTree()
        }
    }

    override fun undoAction() {
        val scene = sceneModel(sceneId) ?: return
        addNodeDatas.forEach {
            sceneNodeModel(it.nodeId, sceneId)?.let { nodeModel ->
                if (nodeModel in KoolEditor.instance.selectionOverlay.selection) {
                    KoolEditor.instance.selectionOverlay.selection -= nodeModel
                }
                scene.removeSceneNode(nodeModel)
            }
        }
        KoolEditor.instance.ui.sceneBrowser.refreshSceneTree()
    }
}
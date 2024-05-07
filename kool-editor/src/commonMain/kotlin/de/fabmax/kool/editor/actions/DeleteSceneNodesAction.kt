package de.fabmax.kool.editor.actions

import de.fabmax.kool.editor.KoolEditor
import de.fabmax.kool.editor.data.NodeId
import de.fabmax.kool.editor.data.SceneNodeData
import de.fabmax.kool.editor.model.NodeModel
import de.fabmax.kool.editor.model.SceneNodeModel
import de.fabmax.kool.editor.util.nodeModel
import de.fabmax.kool.editor.util.sceneModel
import de.fabmax.kool.editor.util.sceneNodeModel
import de.fabmax.kool.util.launchOnMainThread

class DeleteSceneNodesAction(
    nodeModels: List<SceneNodeModel>
) : SceneNodeAction(removeChildNodes(nodeModels)) {

    private val removeNodeInfos = mutableListOf<NodeInfo>()

    init {
        sceneNodes.forEach { appendNodeInfo(it) }
    }

    private fun appendNodeInfo(nodeModel: SceneNodeModel) {
        val nodeIdx = nodeModel.parent.nodeData.childNodeIds.indexOf(nodeModel.nodeId)
        val pos = if (nodeIdx > 0) {
            NodeModel.InsertionPos.After(nodeModel.parent.nodeData.childNodeIds[nodeIdx - 1])
        } else {
            val before = nodeModel.parent.nodeData.childNodeIds.getOrNull(1)
            before?.let { NodeModel.InsertionPos.Before(it) } ?: NodeModel.InsertionPos.End
        }
        removeNodeInfos += NodeInfo(nodeModel.nodeData, nodeModel.parent.nodeId, pos)

        nodeModel.nodeData.childNodeIds.mapNotNull { it.sceneNodeModel }.forEach { child ->
            appendNodeInfo(child)
        }
    }

    override fun doAction() {
        KoolEditor.instance.selectionOverlay.reduceSelection(sceneNodes)
        sceneNodes.forEach {
            it.sceneModel.removeSceneNode(it)
        }
        refreshComponentViews()
    }

    override fun undoAction() {
        launchOnMainThread {
            // removed node model was destroyed, crate a new one only using the old data
            removeNodeInfos.forEach { (nodeData, parentId, pos) ->
                parentId.nodeModel?.let { parent ->
                    val scene = parent.sceneModel
                    val node = SceneNodeModel(nodeData, parent, scene)
                    scene.addSceneNode(node)
                    parent.removeChild(node)
                    parent.addChild(node, pos)
                }
            }
            refreshComponentViews()
        }
    }

    private data class NodeInfo(val nodeData: SceneNodeData, val parentId: NodeId, val position: NodeModel.InsertionPos)

    companion object {
        fun removeChildNodes(allNodes: List<SceneNodeModel>): List<SceneNodeModel> {
            val asSet = allNodes.toSet()
            return allNodes.filter {
                var p = it.parent
                while (p is SceneNodeModel) {
                    if (p in asSet) {
                        return@filter false
                    }
                    p = p.parent
                }
                true
            }
        }
    }
}

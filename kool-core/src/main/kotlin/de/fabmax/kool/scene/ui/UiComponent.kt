package de.fabmax.kool.scene.ui

import de.fabmax.kool.platform.RenderContext
import de.fabmax.kool.scene.TransformGroup
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.MeshBuilder
import de.fabmax.kool.util.RayTest

/**
 * Base class for all UI components.
 *
 * @author fabmax
 */
open class UiComponent(name: String, val root: UiRoot) : TransformGroup(name) {

    val contentBounds = BoundingBox()
    val width: Float get() = contentBounds.size.x
    val height: Float get() = contentBounds.size.y
    val depth: Float get() = contentBounds.size.z

    var layoutSpec = LayoutSpec()
    var padding = Margin(dps(16f), dps(16f), dps(16f), dps(16f))
        set(value) {
            if (value != field) {
                field = value
                isUiUpdate = true
            }
        }

    val ui: ThemeOrCustomProp<ComponentUi> = ThemeOrCustomProp(BlankComponentUi())

    var alpha = 1f
        set(value) {
            field = value
            updateComponentAlpha()
        }

    val dpi: Float
        get() = root.uiDpi

    private var isThemeUpdate = true
    private var isUiUpdate = true

    open fun setupBuilder(builder: MeshBuilder) {
        builder.clear()
        builder.identity()
        builder.translate(contentBounds.min)
    }

    open fun requestThemeUpdate() {
        isThemeUpdate = true
    }

    open fun requestUiUpdate() {
        isUiUpdate = true
    }

    protected open fun updateComponentAlpha() {
        ui.prop.updateComponentAlpha()
    }

    protected open fun updateUi(ctx: RenderContext) {
        ui.prop.updateUi(ctx)
    }

    protected open fun updateTheme(ctx: RenderContext) {
        ui.prop.removeUi(ctx)
        ui.setTheme(createThemeUi(ctx)).apply()
        setThemeProps()
        ui.prop.createUi(ctx)
        requestUiUpdate()
    }

    protected open fun setThemeProps() {
        // no props to set
    }

    protected open fun createThemeUi(ctx: RenderContext): ComponentUi {
        return root.theme.componentUi(this)
    }

    override fun render(ctx: RenderContext) {
        if (isThemeUpdate) {
            isThemeUpdate = false
            updateTheme(ctx)
        }
        if (isUiUpdate) {
            isUiUpdate = false
            updateUi(ctx)
        }

        ui.prop.onRender(ctx)
        super.render(ctx)
    }

    open fun doLayout(bounds: BoundingBox, ctx: RenderContext) {
        if (!contentBounds.isEqual(bounds)) {
            contentBounds.set(bounds)
            requestUiUpdate()
        }
    }

    override fun rayTest(test: RayTest) {
        val hitNode = test.hitNode
        super.rayTest(test)
        if (hitNode != test.hitNode && test.hitNode !is UiComponent) {
            // an element of this component (and not a sub-component in case this is a container) was hit!
            test.hitNode = this
            test.hitPositionLocal.subtract(contentBounds.min)
        }
    }
}

package de.fabmax.kool.editor.ui

import de.fabmax.kool.Assets
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logT
import kotlin.math.roundToInt

object IconMap {

    private var windowScale = 1f
    private val iconMapSize = Vec2i(480, 480)
    private val iconTexProps = TextureProps(
        format = TexFormat.RGBA,
        generateMipMaps = false,
        defaultSamplerSettings = SamplerSettings().nearest()
    )

    val small = EditorIconMap(Dp(18f))
    val medium = EditorIconMap(Dp(24f))

    init {
        val ctx = KoolSystem.requireContext()
        windowScale = ctx.windowScale
        ctx.onWindowScaleChanged += {
            windowScale = it.windowScale
            small.invalidate()
            medium.invalidate()
        }
    }

    class EditorIconMap(val iconSize: Dp) {
        private val iconLoader = AsyncTextureLoader {
            val s = iconSize.value / 24f * windowScale
            val width = (iconMapSize.x * s).roundToInt()
            val height = (iconMapSize.y * s).roundToInt()
            val loadProps = iconTexProps.copy(resolveSize = Vec2i(width, height))
            logT { "Render icon map: $width x $height (${iconSize.value} dp)" }
            Assets.loadTextureData("assets/icons/icons-24px.svg", loadProps)
        }

        private val iconTex = Texture2d(iconTexProps, "icon-map", iconLoader)
        private val iconMap = ImageIconMap(20)

        init {
            iconMap[24] = iconTex
        }

        fun invalidate() {
            if (iconTex.loadingState == Texture.LoadingState.LOADED) {
                iconLoader.invalidate()
                iconTex.dispose()
            }
        }

        val cube = IconProvider(this, iconMap.IconImageProvider(0, 0))
        val tree = IconProvider(this, iconMap.IconImageProvider(1, 0))
        val light = IconProvider(this, iconMap.IconImageProvider(2, 0))
        val camera = IconProvider(this, iconMap.IconImageProvider(3, 0))
        val circleDot = IconProvider(this, iconMap.IconImageProvider(4, 0))
        val circleCrosshair = IconProvider(this, iconMap.IconImageProvider(5, 0))
        val emptyObject = IconProvider(this, iconMap.IconImageProvider(6, 0))
        val picture = IconProvider(this, iconMap.IconImageProvider(7, 0))
        val fullscreen = IconProvider(this, iconMap.IconImageProvider(8, 0))
        val rotate = IconProvider(this, iconMap.IconImageProvider(9, 0))
        val move = IconProvider(this, iconMap.IconImageProvider(10, 0))
        val trash = IconProvider(this, iconMap.IconImageProvider(11, 0))
        val world = IconProvider(this, iconMap.IconImageProvider(12, 0))
        val palette = IconProvider(this, iconMap.IconImageProvider(13, 0))
        val eye = IconProvider(this, iconMap.IconImageProvider(14, 0))
        val eyeOff = IconProvider(this, iconMap.IconImageProvider(15, 0))
        val grab = IconProvider(this, iconMap.IconImageProvider(16, 0))
        val rectTopLt = IconProvider(this, iconMap.IconImageProvider(17, 0))
        val rectInside = IconProvider(this, iconMap.IconImageProvider(18, 0))
        val rectOutside = IconProvider(this, iconMap.IconImageProvider(19, 0))
        val duplicate = IconProvider(this, iconMap.IconImageProvider(0, 1))
        val undo = IconProvider(this, iconMap.IconImageProvider(1, 1))
        val redo = IconProvider(this, iconMap.IconImageProvider(2, 1))
        val code = IconProvider(this, iconMap.IconImageProvider(3, 1))
        val codeFolder = IconProvider(this, iconMap.IconImageProvider(4, 1))
        val codeFile = IconProvider(this, iconMap.IconImageProvider(5, 1))
        val doubleHex = IconProvider(this, iconMap.IconImageProvider(6, 1))
        val properties = IconProvider(this, iconMap.IconImageProvider(7, 1))
        val connectedCircles = IconProvider(this, iconMap.IconImageProvider(8, 1))
        val quadBox = IconProvider(this, iconMap.IconImageProvider(9, 1))
        val select = IconProvider(this, iconMap.IconImageProvider(10, 1))
        val selectOff = IconProvider(this, iconMap.IconImageProvider(11, 1))
        val selectPlus = IconProvider(this, iconMap.IconImageProvider(12, 1))
        val nodeV = IconProvider(this, iconMap.IconImageProvider(13, 1))
        val nodeH = IconProvider(this, iconMap.IconImageProvider(14, 1))
        val nodeCircle = IconProvider(this, iconMap.IconImageProvider(15, 1))
        val folder = IconProvider(this, iconMap.IconImageProvider(16, 1))
        val folderOpen = IconProvider(this, iconMap.IconImageProvider(17, 1))
        val shadowedSphere = IconProvider(this, iconMap.IconImageProvider(18, 1))
        val shadow = IconProvider(this, iconMap.IconImageProvider(19, 1))
        val transform = IconProvider(this, iconMap.IconImageProvider(0, 2))
        val background = IconProvider(this, iconMap.IconImageProvider(1, 2))
        val listTree = IconProvider(this, iconMap.IconImageProvider(2, 2))
        val download = IconProvider(this, iconMap.IconImageProvider(3, 2))
        val console = IconProvider(this, iconMap.IconImageProvider(4, 2))
        val github = IconProvider(this, iconMap.IconImageProvider(5, 2))
        val magnet = IconProvider(this, iconMap.IconImageProvider(6, 2))
        val magnetOff = IconProvider(this, iconMap.IconImageProvider(7, 2))
        val lockOpen = IconProvider(this, iconMap.IconImageProvider(8, 2))
        val lock = IconProvider(this, iconMap.IconImageProvider(9, 2))
        val scrollLock = IconProvider(this, iconMap.IconImageProvider(10, 2))
        val copy = IconProvider(this, iconMap.IconImageProvider(11, 2))
        val paste = IconProvider(this, iconMap.IconImageProvider(12, 2))
        val save = IconProvider(this, iconMap.IconImageProvider(13, 2))
        val settings = IconProvider(this, iconMap.IconImageProvider(14, 2))
        val filter = IconProvider(this, iconMap.IconImageProvider(15, 2))
        val search = IconProvider(this, iconMap.IconImageProvider(16, 2))
        val plus = IconProvider(this, iconMap.IconImageProvider(17, 2))
        val minus = IconProvider(this, iconMap.IconImageProvider(18, 2))
        val edit = IconProvider(this, iconMap.IconImageProvider(19, 2))
        val physics = IconProvider(this, iconMap.IconImageProvider(0, 3))
        val alert = IconProvider(this, iconMap.IconImageProvider(1, 3))
        val alertFilled = IconProvider(this, iconMap.IconImageProvider(2, 3))
        val resize = IconProvider(this, iconMap.IconImageProvider(3, 3))
        val character = IconProvider(this, iconMap.IconImageProvider(4, 3))
        val car = IconProvider(this, iconMap.IconImageProvider(5, 3))
        val minimizeWin = IconProvider(this, iconMap.IconImageProvider(6, 3))
        val maximizeWin = IconProvider(this, iconMap.IconImageProvider(7, 3))
        val demaximizeWin = IconProvider(this, iconMap.IconImageProvider(8, 3))
        val closeWin = IconProvider(this, iconMap.IconImageProvider(9, 3))
        val cylinder = IconProvider(this, iconMap.IconImageProvider(10, 3))
        val rect = IconProvider(this, iconMap.IconImageProvider(11, 3))
        val capsule = IconProvider(this, iconMap.IconImageProvider(12, 3))
        val mountain = IconProvider(this, iconMap.IconImageProvider(13, 3))
        val file3d = IconProvider(this, iconMap.IconImageProvider(14, 3))
        val sun = IconProvider(this, iconMap.IconImageProvider(15, 3))
        val spotLight = IconProvider(this, iconMap.IconImageProvider(16, 3))

        val editorIcon = IconProvider(this, iconMap.IconImageProvider(0, 4))
    }
}

class IconProvider(val iconMap: IconMap.EditorIconMap, val provider: ImageIconMap.IconImageProvider)

fun ImageModifier.iconImage(iconProvider: IconProvider, tintColor: Color? = null): ImageModifier {
    size(iconProvider.iconMap.iconSize, iconProvider.iconMap.iconSize)
    imageProvider(iconProvider.provider)
    tintColor?.let { tint(it) }
    return this
}

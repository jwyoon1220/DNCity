package com.atsuishio.superbwarfare.client.overlay

import com.atsuishio.superbwarfare.client.RenderHelper
import com.atsuishio.superbwarfare.event.ClientMouseHandler
import com.atsuishio.superbwarfare.tools.mc
import net.minecraft.client.CameraType
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * War Thunder风格Mouse Aim的准星：驾驶固定翼/直升机且鼠标被释放(见[ClientMouseHandler.mouseAimActive])时，
 * 在自由光标当前位置画一个空心圆环，代替vanilla的准星十字。位置对齐vanilla准星本身的绘制坐标
 * (screenWidth/2f, screenHeight/2f——对照[CrossHairOverlay]自己的准星绘制调用确认过，
 * 没有额外的热键栏/GUI布局偏移)，因为进入Mouse Aim时光标本来就是被摆正到屏幕正中心的。
 */
@OnlyIn(Dist.CLIENT)
object MouseAimReticleOverlay : CommonOverlay("mouse_aim_reticle") {
    // GUI坐标系下的固定像素半径——GUI坐标系本身已经按guiScale换算过，
    // 所以这里不需要再手动乘guiScale，视觉大小会随GUI缩放自动跟着变。
    // 第一人称=5mm参照(外径10px/直径20px)，第三人称=10mm参照(外径20px/直径40px)，
    // 描边约为直径的1/30(2/3px @ 20px直径，1又1/3px @ 40px直径)，两档比例保持一致。
    private const val FIRST_PERSON_OUTER_RADIUS = 10f
    private const val FIRST_PERSON_STROKE = 2f / 3f
    private const val FIRST_PERSON_INNER_RADIUS = FIRST_PERSON_OUTER_RADIUS - FIRST_PERSON_STROKE

    private const val THIRD_PERSON_OUTER_RADIUS = 20f
    private const val THIRD_PERSON_STROKE = 4f / 3f
    private const val THIRD_PERSON_INNER_RADIUS = THIRD_PERSON_OUTER_RADIUS - THIRD_PERSON_STROKE

    private val RING_COLOR = floatArrayOf(1f, 1f, 1f, 0.85f)

    override fun shouldRender() = super.shouldRender() && ClientMouseHandler.mouseAimActive

    override fun RenderContext.render() {
        val firstPerson = mc.options.cameraType == CameraType.FIRST_PERSON
        val outerRadius = if (firstPerson) FIRST_PERSON_OUTER_RADIUS else THIRD_PERSON_OUTER_RADIUS
        val innerRadius = if (firstPerson) FIRST_PERSON_INNER_RADIUS else THIRD_PERSON_INNER_RADIUS

        RenderHelper.renderCircularRing(
            guiGraphics,
            ClientMouseHandler.reticleGuiX,
            ClientMouseHandler.reticleGuiY,
            outerRadius, innerRadius,
            RING_COLOR, RING_COLOR,
            1f, false
        )
    }
}

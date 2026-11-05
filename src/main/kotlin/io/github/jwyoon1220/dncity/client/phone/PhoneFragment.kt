package io.github.jwyoon1220.dncity.client.phone

import icyllis.modernui.fragment.Fragment
import icyllis.modernui.mc.ScreenCallback
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import io.github.jwyoon1220.dncity.client.phone.nanovg.PhoneAction
import io.github.jwyoon1220.dncity.client.phone.nanovg.PhoneCanvasView
import io.github.jwyoon1220.dncity.client.phone.nanovg.PhoneNanoVgSurface
import io.github.jwyoon1220.dncity.client.phone.nanovg.PhonePage
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap
import net.minecraft.client.Minecraft

/**
 * Native ModernUI/NanoVG replacement for the removed JCEF-based phone overlay (the old
 * `client.phone.PhoneOverlay`/`PhoneWebServer` -- see git history) -- a plain
 * [icyllis.modernui.mc.MuiScreen], so it runs entirely on Minecraft's own render/UI thread. No
 * child process, HTTP server, or native window involved.
 *
 * The phone's chrome/keypad/status-bar/call-banner are drawn by [PhoneNanoVgSurface] (NanoVG,
 * rendered to an offscreen FBO on the render thread) and displayed via [PhoneCanvasView] (a
 * ModernUI [View] that composites that FBO's pixels each UI-thread frame). Real text entry
 * (dialer number, message recipient/body -- needs Korean IME support) stays real ModernUI
 * [EditText] widgets layered on top via [FrameLayout], positioned to line up exactly with the
 * NanoVG-drawn field backgrounds using [PhoneNanoVgSurface]'s shared layout constants. See
 * [PhoneNanoVgSurface]'s doc comment for why the drawing and the text-entry widgets are split
 * this way instead of doing either one thing fully.
 *
 * The domain layer this drives ([PhoneCallManager], [PhoneMessageManager]) is unchanged from the
 * old UI.
 */
class PhoneFragment : Fragment(), ScreenCallback {

    private lateinit var dialerField: EditText
    private lateinit var messageToField: EditText
    private lateinit var messageBodyField: EditText

    override fun isPauseScreen(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?,
    ): View {
        val context = requireContext()

        // Fills the whole game window; the actual phone panel is positioned within it via
        // [panelParams]'s gravity/margins below, the way `client.ui.MaterialMenuFragment`
        // (now removed) positioned its own content within a full-size returned root view.
        val screenRoot = FrameLayout(context)

        val panel = FrameLayout(context)
        val panelParams = FrameLayout.LayoutParams(PhoneNanoVgSurface.PANEL_WIDTH, PhoneNanoVgSurface.PANEL_HEIGHT)
        panelParams.gravity = Gravity.BOTTOM or Gravity.RIGHT
        panelParams.setMargins(0, 0, SCREEN_MARGIN, SCREEN_MARGIN)
        screenRoot.addView(panel, panelParams)

        val canvasView = PhoneCanvasView(context)
        canvasView.onAction = { handleAction(it) }
        panel.addView(canvasView, FrameLayout.LayoutParams(PhoneNanoVgSurface.PANEL_WIDTH, PhoneNanoVgSurface.PANEL_HEIGHT))

        dialerField = EditText(context)
        dialerField.textSize = 18f
        panel.addView(dialerField, overlayParams(
            PhoneNanoVgSurface.FIELD_MARGIN, PhoneNanoVgSurface.DIALER_FIELD_Y,
            PhoneNanoVgSurface.PANEL_WIDTH - PhoneNanoVgSurface.BEZEL * 2 - PhoneNanoVgSurface.FIELD_MARGIN * 2, PhoneNanoVgSurface.DIALER_FIELD_HEIGHT,
        ))

        messageToField = EditText(context)
        messageToField.setHint("받는 번호")
        panel.addView(messageToField, overlayParams(
            PhoneNanoVgSurface.FIELD_MARGIN, PhoneNanoVgSurface.MESSAGE_TO_FIELD_Y,
            PhoneNanoVgSurface.PANEL_WIDTH - PhoneNanoVgSurface.BEZEL * 2 - PhoneNanoVgSurface.FIELD_MARGIN * 2, PhoneNanoVgSurface.MESSAGE_TO_FIELD_HEIGHT,
        ))

        messageBodyField = EditText(context)
        messageBodyField.setHint("메시지 내용")
        panel.addView(messageBodyField, overlayParams(
            PhoneNanoVgSurface.FIELD_MARGIN, PhoneNanoVgSurface.MESSAGE_BODY_FIELD_Y,
            PhoneNanoVgSurface.PANEL_WIDTH - PhoneNanoVgSurface.BEZEL * 2 - PhoneNanoVgSurface.FIELD_MARGIN * 2, PhoneNanoVgSurface.MESSAGE_BODY_FIELD_HEIGHT,
        ))

        for (field in listOf(dialerField, messageToField, messageBodyField)) {
            field.setBackground(null)
            field.setTextColor(0xFFFFFFFF.toInt())
        }

        updateFieldVisibility()
        return screenRoot
    }

    /** [x]/[y] are screen-local (post-bezel) coordinates, same space as [PhoneNanoVgSurface]'s field-position constants -- offset by [PhoneNanoVgSurface.BEZEL] here since these fields sit inside the phone panel's [PhoneCanvasView], which draws the bezel starting at its own (0,0). */
    private fun overlayParams(x: Int, y: Int, w: Int, h: Int): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(w, h)
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.setMargins(x + PhoneNanoVgSurface.BEZEL, y + PhoneNanoVgSurface.BEZEL, 0, 0)
        return params
    }

    private companion object {
        const val SCREEN_MARGIN = 24
    }

    private fun handleAction(action: PhoneAction) {
        when (action) {
            is PhoneAction.Navigate -> {
                PhoneNanoVgSurface.navigate(action.page)
                updateFieldVisibility()
            }
            is PhoneAction.KeypadDigit -> {
                dialerField.setText(dialerField.getText().toString() + action.digit)
            }
            PhoneAction.KeypadBackspace -> {
                val text = dialerField.getText().toString()
                if (text.isNotEmpty()) dialerField.setText(text.substring(0, text.length - 1))
            }
            PhoneAction.Call -> {
                val number = dialerField.getText().toString()
                Minecraft.getInstance().execute { PhoneCallManager.connect(number) }
            }
            PhoneAction.Accept -> Minecraft.getInstance().execute { PhoneCallManager.accept() }
            PhoneAction.Decline -> Minecraft.getInstance().execute { PhoneCallManager.decline() }
            PhoneAction.Hangup -> Minecraft.getInstance().execute { PhoneCallManager.hangup() }
            PhoneAction.SendMessage -> {
                val to = messageToField.getText().toString()
                val text = messageBodyField.getText().toString()
                Minecraft.getInstance().execute { PhoneMessageManager.sendMessage(to, text) }
            }
            // Replaces the phone screen with VoxelMap's own fullscreen world map -- VoxelMap is
            // bundled as a required companion mod (see settings.gradle.kts/build.gradle.kts's
            // VoxelMap entries), not an optional one, so calling its class directly is safe.
            // Closes the phone (frees its NanoVG surface), then asks the server for the market: the snapshot that comes
            // back opens ShopFragment (see client.shop.ShopClient)
            PhoneAction.OpenShop -> Minecraft.getInstance().execute {
                PhoneController.close()
                Minecraft.getInstance().player?.connection?.sendCommand("shop")
            }
            PhoneAction.OpenAmmoShop -> Minecraft.getInstance().execute {
                PhoneController.close()
                Minecraft.getInstance().player?.connection?.sendCommand("ammoshop")
            }
            PhoneAction.OpenMap -> Minecraft.getInstance().execute {
                Minecraft.getInstance().setScreen(GuiPersistentMap(null))
            }
        }
    }

    private fun updateFieldVisibility() {
        val page = PhoneNanoVgSurface.currentPage
        dialerField.visibility = if (page == PhonePage.DIALER) View.VISIBLE else View.GONE
        messageToField.visibility = if (page == PhonePage.MESSAGES) View.VISIBLE else View.GONE
        messageBodyField.visibility = if (page == PhonePage.MESSAGES) View.VISIBLE else View.GONE
    }
}

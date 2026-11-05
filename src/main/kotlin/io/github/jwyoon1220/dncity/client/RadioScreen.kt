package io.github.jwyoon1220.dncity.client

import io.github.jwyoon1220.dncity.client.dispatch.DispatchClientState
import io.github.jwyoon1220.dncity.item.RadioItem
import io.github.jwyoon1220.dncity.network.RadioSetActivePayload
import io.github.jwyoon1220.dncity.network.RadioSetPoweredPayload
import io.github.jwyoon1220.dncity.network.RadioTunePayload
import io.github.jwyoon1220.dncity.radio.RadioBand
import io.github.jwyoon1220.dncity.radio.RadioData
import io.github.jwyoon1220.dncity.radio.RadioMode
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.abs

/**
 * The radio's tuning UI. Client-only: reads the item's current [RadioData] (synced to the client as part of the
 * ItemStack) for display, and every action sends one of the radio_* payloads (see [io.github.jwyoon1220.dncity.network])
 * to the server, which is the only authority that actually mutates the stack.
 *
 * One card per slot: slot number, seek arrows around a frequency box (type a number and press Enter or Set to tune by
 * hand), mode, a TX selector, and a readout line under it with the frequency in MHz/kHz, the band, and a marker when the
 * slot sits on the police channel. A law-job player also gets a "경찰 채널" button on every card that tunes that slot to
 * the dispatch frequency in one press ([DispatchClientState]).
 */
class RadioScreen(private val radio: RadioItem, stack: ItemStack, @Suppress("unused") private val hand: InteractionHand) :
    Screen(Component.literal("Radio")) {

    private var data: RadioData = radio.dataOf(stack)

    private class SlotRow(val index: Int, val freqBox: EditBox, var mode: RadioMode, val modeButton: Button, val activeButton: Button?)

    private val rows = ObjectArrayList<SlotRow>()
    private lateinit var powerButton: Button

    private val panelWidth = 240
    private val headerHeight = 30
    private val rowHeight = 40
    private val panelHeight get() = headerHeight + radio.tier * rowHeight + 8
    private val panelLeft get() = (width - panelWidth) / 2
    private val panelTop get() = ((height - panelHeight) / 2).coerceAtLeast(4)
    private fun rowTop(index: Int) = panelTop + headerHeight + index * rowHeight

    override fun init() {
        rows.clear()
        val left = panelLeft
        val police = DispatchClientState.police

        powerButton = addRenderableWidget(
            Button.builder(powerLabel()) { togglePower() }
                .bounds(left + panelWidth - 62, panelTop + 7, 54, 16)
                .build(),
        )

        val supportedModes = radio.bandGroup.modes.toList()

        for (i in 0 until radio.tier) {
            val slot = data.slots.getOrNull(i)
            val y = rowTop(i) + 4
            val x = left + 14

            // Seek buttons: step the tuned frequency within its current band by a band-appropriate channel spacing
            // (see seekStepKhz), clamped at the band's edge rather than wrapping into a different band, since jumping
            // families (e.g. MW -> VHF) needs a deliberate retype, not an accidental seek press.
            addRenderableWidget(Button.builder(Component.literal("<")) { seekRow(i, -1) }.bounds(x, y, 14, 16).build())

            val freqBox = EditBox(font, x + 16, y, 62, 16, Component.literal("freq_$i"))
            freqBox.setMaxLength(12)
            freqBox.value = if (slot != null && slot.enabled) trimFrequency(slot.frequencyKhz) else ""
            addRenderableWidget(freqBox)

            addRenderableWidget(Button.builder(Component.literal(">")) { seekRow(i, 1) }.bounds(x + 80, y, 14, 16).build())

            // Only cycle through modes this physical radio's band group actually supports (see RadioBandGroup) --
            // tuning to any other mode would just be rejected server-side.
            val mode = slot?.mode?.takeIf { it in radio.bandGroup.modes } ?: supportedModes.first()
            val modeButton: Button = addRenderableWidget(
                Button.builder(Component.literal(mode.name)) {
                    val current = rows.first { it.index == i }
                    val nextIndex = (supportedModes.indexOf(current.mode) + 1) % supportedModes.size
                    current.mode = supportedModes[nextIndex]
                    current.modeButton.message = Component.literal(current.mode.name)
                    sendTune(current)
                }.bounds(x + 98, y, 44, 16).build(),
            )

            // A receive-only radio (RadioItem.canTransmit == false, see RadioBandGroup.SCANNER) never has anything to
            // make "active" -- there's nothing to transmit with -- so it gets no TX button at all rather than one that
            // would just be rejected server-side.
            val activeButton = if (radio.canTransmit) {
                addRenderableWidget(Button.builder(activeLabel(i)) { sendActive(i) }.bounds(x + 146, y, 26, 16).build())
            } else {
                null
            }

            val row = SlotRow(i, freqBox, mode, modeButton, activeButton)
            rows.add(row)

            addRenderableWidget(Button.builder(Component.literal("Set")) { sendTune(row) }.bounds(x + 176, y, 34, 16).build())

            if (police) {
                val band = RadioBand.fromFrequencyKhz(DispatchClientState.frequencyKhz)
                val button = Button.builder(Component.literal("경찰 채널").withStyle(ChatFormatting.GOLD)) { tunePolice(row) }
                    .bounds(x + 146, y + 19, 64, 14)
                    .build()
                button.active = band != null && radio.bandGroup.supports(band)
                addRenderableWidget(button)
            }
        }
    }

    /** Band-appropriate seek step: a coarser jump than [RadioBand.tuningToleranceKhz] alone so a handful of presses
     * meaningfully moves across the dial instead of nudging by less than a channel's worth. */
    private fun seekStepKhz(band: RadioBand): Double = band.tuningToleranceKhz * SEEK_STEP_TOLERANCE_MULTIPLIER

    private fun seekRow(index: Int, direction: Int) {
        val row = rows.first { it.index == index }
        val current = row.freqBox.value.toDoubleOrNull() ?: return
        val band = RadioBand.fromFrequencyKhz(current) ?: return
        val next = (current + direction * seekStepKhz(band)).coerceIn(band.minKhz, band.maxKhz)
        row.freqBox.value = trimFrequency(next)
        sendTune(row)
    }

    /** Tunes [row] to the police channel in a mode the radio can use there (sideband on HF, else narrow FM, then AM). */
    private fun tunePolice(row: SlotRow) {
        val modes = radio.bandGroup.modes
        val sideband = if (DispatchClientState.frequencyKhz < 10_000.0) RadioMode.LSB else RadioMode.USB
        val mode = listOf(sideband, RadioMode.USB, RadioMode.LSB, RadioMode.NBFM, RadioMode.AM).firstOrNull { it in modes } ?: modes.first()
        row.mode = mode
        row.modeButton.message = Component.literal(mode.name)
        row.freqBox.value = trimFrequency(DispatchClientState.frequencyKhz)
        sendTune(row)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        val left = panelLeft
        val top = panelTop

        // chassis: dark body, accent edge, header strip
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF2E3A52.toInt())
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0101319.toInt())
        graphics.fill(left, top, left + panelWidth, top + headerHeight - 2, 0xFF171C27.toInt())
        graphics.fill(left, top + headerHeight - 2, left + panelWidth, top + headerHeight - 1, 0xFF3A6BD8.toInt())

        graphics.drawString(font, Component.translatable(radio.bandGroup.translationKey), left + 22, top + 11, 0xFFFFFF, false)
        // power LED
        val led = if (data.powered) 0xFF3DDC84.toInt() else 0xFF5A1F1F.toInt()
        graphics.fill(left + 9, top + 11, left + 17, top + 19, 0xFF000000.toInt())
        graphics.fill(left + 10, top + 12, left + 16, top + 18, led)

        val policeFreq = DispatchClientState.frequencyKhz
        for (row in rows) {
            val y = rowTop(row.index)
            val active = radio.canTransmit && data.activeSlot == row.index
            graphics.fill(left + 6, y + 1, left + panelWidth - 6, y + rowHeight - 3, if (active) 0xFF1D2433.toInt() else 0xFF161A22.toInt())
            graphics.fill(left + 6, y + 1, left + 9, y + rowHeight - 3, if (active) 0xFFFFB347.toInt() else 0xFF2A3040.toInt())
            graphics.drawString(font, "${row.index + 1}", left + 10, y + 8, if (active) 0xFFFFB347.toInt() else 0x7F8696, false)

            val khz = row.freqBox.value.toDoubleOrNull()
            val band = khz?.let { RadioBand.fromFrequencyKhz(it) }
            val readout = if (khz == null) "--" else formatFrequency(khz)
            graphics.drawString(font, readout, left + 14, y + 23, if (band != null) 0x9CD3FF else 0x8A8F99, false)
            if (band != null) {
                graphics.drawString(font, band.name, left + 14 + font.width(readout) + 6, y + 23, 0x6E7686, false)
            }
            if (khz != null && band != null && abs(khz - policeFreq) <= band.tuningToleranceKhz) {
                val tag = if (DispatchClientState.police) "★ 경찰" else "★"
                graphics.drawString(font, tag, left + 14 + font.width(readout) + 6 + font.width(band.name) + 6, y + 23, 0xFFC93C, false)
            }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Enter in a frequency box tunes that slot, so a frequency can be typed by hand and confirmed from the keyboard
        if (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
            val row = rows.firstOrNull { it.freqBox.isFocused }
            if (row != null) {
                sendTune(row)
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false

    private fun trimFrequency(khz: Double): String = if (khz == khz.toLong().toDouble()) khz.toLong().toString() else khz.toString()

    private fun formatFrequency(khz: Double): String =
        if (khz >= 1000.0) String.format("%.3f MHz", khz / 1000.0) else String.format("%.0f kHz", khz)

    private fun powerLabel(): Component =
        Component.literal(if (data.powered) "ON" else "OFF").withStyle(if (data.powered) ChatFormatting.GREEN else ChatFormatting.RED)

    private fun activeLabel(index: Int): Component =
        if (data.activeSlot == index) Component.literal("TX").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD) else Component.literal("TX").withStyle(ChatFormatting.GRAY)

    private fun togglePower() {
        data = data.copy(powered = !data.powered)
        PacketDistributor.sendToServer(RadioSetPoweredPayload(data.powered))
        powerButton.message = powerLabel()
    }

    private fun sendActive(index: Int) {
        data = data.copy(activeSlot = index)
        PacketDistributor.sendToServer(RadioSetActivePayload(index))
        rows.forEach { it.activeButton?.message = activeLabel(it.index) }
    }

    private fun sendTune(row: SlotRow) {
        val frequency = row.freqBox.value.toDoubleOrNull() ?: return
        PacketDistributor.sendToServer(RadioTunePayload(row.index, frequency, row.mode.name))
    }

    companion object {
        private const val SEEK_STEP_TOLERANCE_MULTIPLIER = 4.0
        private const val KEY_ENTER = 257
        private const val KEY_KP_ENTER = 335
    }
}

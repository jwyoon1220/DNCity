// AGENT-DONE(claude): police-dispatch
package io.github.jwyoon1220.dncity.dispatch

import net.neoforged.fml.loading.FMLPaths
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Records a line of text with the machine's own speech synthesizer (no network, no bundled voice): Windows SAPI through
 * PowerShell (a Korean voice if one is installed), `espeak-ng` on Linux, `say` on macOS. The WAV is cached under
 * `config/dncity/dispatch/<hash>.wav`, so a repeated line is only synthesized once. Blocking: call it off the server thread.
 * Returns null (and logs once) when the machine has no synthesizer; the dispatch is then text only.
 */
object DispatchTts {
    private val LOGGER = LoggerFactory.getLogger("dncity/dispatch-tts")
    private const val TARGET_RATE = 8000
    private const val TIMEOUT_SECONDS = 25L

    /** SAPI speaking rate, -10..10 (0 = normal, 4 is about 1.5x): dispatchers talk fast. Part of the cache key. */
    private const val SPEECH_RATE = 4
    private var warned = false

    private val dir: File by lazy { FMLPaths.CONFIGDIR.get().resolve("dncity").resolve("dispatch").toFile().also { it.mkdirs() } }

    /** [text] as mono 16-bit PCM at 8 kHz (codec2's rate), or null. */
    fun synthesize(text: String): ShortArray? {
        val wav = File(dir, hash(text) + ".wav")
        if (!wav.isFile || wav.length() < 100) {
            if (!run(text, wav)) return null
        }
        return runCatching { readWav8k(wav.readBytes()) }
            .onFailure { LOGGER.warn("unreadable TTS output {}: {}", wav, it.toString()); wav.delete() }
            .getOrNull()
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(("rate$SPEECH_RATE:" + text).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun run(text: String, out: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val pb = when {
            os.contains("win") -> ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Speech; \$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "try { \$s.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, [System.Speech.Synthesis.VoiceAge]::NotSet, 0, " +
                    "[System.Globalization.CultureInfo]::GetCultureInfo('ko-KR')) } catch { }; " +
                    "\$s.Rate = $SPEECH_RATE; \$s.SetOutputToWaveFile(\$env:DNCITY_TTS_OUT); \$s.Speak(\$env:DNCITY_TTS_TEXT); \$s.Dispose()",
            ).also {
                it.environment()["DNCITY_TTS_OUT"] = out.absolutePath
                it.environment()["DNCITY_TTS_TEXT"] = text
            }
            os.contains("mac") -> ProcessBuilder("say", "-r", "${150 + SPEECH_RATE * 20}", "-o", out.absolutePath, "--file-format=WAVE", "--data-format=LEI16@22050", text)
            else -> ProcessBuilder("espeak-ng", "-v", "ko", "-s", "${165 + SPEECH_RATE * 15}", "-w", out.absolutePath, text)
        }
        return try {
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.inputStream.readAllBytes()
            if (!proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                LOGGER.warn("TTS timed out")
                false
            } else if (proc.exitValue() != 0 || !out.isFile) {
                warnOnce("TTS exited with ${proc.exitValue()}")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            warnOnce("no speech synthesizer available: $e")
            false
        }
    }

    private fun warnOnce(msg: String) {
        if (!warned) {
            warned = true
            LOGGER.warn("Dispatch voice disabled ({}); calls are shown as text only", msg)
        }
    }

    /** RIFF/WAVE PCM16 (any rate, mono or stereo) -> mono 8 kHz, box-filter decimated (a crude low-pass, fine for codec2 input). */
    internal fun readWav8k(bytes: ByteArray): ShortArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size > 44 && buf.getInt(0) == 0x46464952 && buf.getInt(8) == 0x45564157) { "not a WAV" }
        var pos = 12
        var channels = 1
        var rate = 22050
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = buf.getInt(pos)
            val len = buf.getInt(pos + 4)
            if (id == 0x20746d66) { // "fmt "
                channels = buf.getShort(pos + 10).toInt()
                rate = buf.getInt(pos + 12)
                bits = buf.getShort(pos + 22).toInt()
            } else if (id == 0x61746164) { // "data"
                dataStart = pos + 8
                dataLen = if (len < 0 || dataStart + len > bytes.size) bytes.size - dataStart else len
                break
            }
            pos += 8 + len + (len and 1)
        }
        require(dataStart >= 0 && bits == 16 && channels in 1..2) { "unsupported WAV ($bits bit, $channels ch)" }
        val frames = dataLen / (2 * channels)
        val mono = FloatArray(frames) { i ->
            var sum = 0
            for (c in 0 until channels) sum += buf.getShort(dataStart + (i * channels + c) * 2)
            sum.toFloat() / channels
        }
        val ratio = rate.toDouble() / TARGET_RATE
        val outLen = (frames / ratio).toInt()
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val from = (i * ratio).toInt()
            val to = minOf(frames, maxOf(from + 1, ((i + 1) * ratio).toInt()))
            var acc = 0f
            for (j in from until to) acc += mono[j]
            out[i] = (acc / (to - from)).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}

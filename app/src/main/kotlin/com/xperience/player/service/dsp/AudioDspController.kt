package com.xperience.player.service.dsp

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.PlaybackParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

enum class EqPreset(
    val displayName: String,
    val bandGainsDb: List<Float>,
    val defaultBassDb: Float,
    val defaultTrebleDb: Float
) {
    FLAT(
        displayName = "Flat",
        bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f),
        defaultBassDb = 0f,
        defaultTrebleDb = 0f
    ),
    NIGHTCORE_PUNCH(
        displayName = "Nightcore Punch",
        bandGainsDb = listOf(5.5f, 3.0f, -0.5f, 2.0f, 3.0f),
        defaultBassDb = 6.0f,
        defaultTrebleDb = 2.0f
    ),
    BASS_BOOST(
        displayName = "Bass Boost",
        bandGainsDb = listOf(6.5f, 4.5f, 0.5f, 0.0f, 0.5f),
        defaultBassDb = 6.5f,
        defaultTrebleDb = 0.5f
    ),
    VOCAL_CLARITY(
        displayName = "Vocal Clarity",
        bandGainsDb = listOf(-1.5f, 0.5f, 3.5f, 4.0f, 2.0f),
        defaultBassDb = -1.0f,
        defaultTrebleDb = 2.5f
    ),
    ELECTRONIC(
        displayName = "Electronic / EDM",
        bandGainsDb = listOf(5.0f, 2.5f, -1.5f, 2.5f, 4.5f),
        defaultBassDb = 5.0f,
        defaultTrebleDb = 3.5f
    ),
    ROCK(
        displayName = "Rock",
        bandGainsDb = listOf(4.5f, 2.0f, -1.0f, 3.0f, 4.0f),
        defaultBassDb = 4.0f,
        defaultTrebleDb = 3.0f
    ),
    ACOUSTIC(
        displayName = "Acoustic",
        bandGainsDb = listOf(2.5f, 2.0f, 1.0f, 2.5f, 2.0f),
        defaultBassDb = 2.0f,
        defaultTrebleDb = 2.0f
    ),
    CUSTOM(
        displayName = "Custom",
        bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f),
        defaultBassDb = 0f,
        defaultTrebleDb = 0f
    )
}

data class AudioDspState(
    val isNightcoreEnabled: Boolean = false,
    val nightcoreSpeed: Float = NightcoreDspProcessor.DEFAULT_NIGHTCORE_SPEED,
    val nightcoreLowShelfFreqHz: Float = NightcoreDspProcessor.NIGHTCORE_LOW_SHELF_FREQ_HZ,
    val nightcoreLowShelfGainDb: Float = NightcoreDspProcessor.NIGHTCORE_LOW_SHELF_GAIN_DB,
    val nightcorePeakingFreqHz: Float = NightcoreDspProcessor.NIGHTCORE_PEAKING_FREQ_HZ,
    val nightcorePeakingGainDb: Float = NightcoreDspProcessor.NIGHTCORE_PEAKING_GAIN_DB,
    val playbackSpeed: Float = 1.0f,
    val pitchSemitones: Float = 0.0f,
    val couplePitchToSpeed: Boolean = false,
    val isEqualizerEnabled: Boolean = true,
    val selectedPreset: EqPreset = EqPreset.FLAT,
    val bandGainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoostDb: Float = 0f,
    val trebleBoostDb: Float = 0f,
    val isCompressorEnabled: Boolean = true
) {
    /**
     * Effective playback speed applied to the audio engine.
     * When Nightcore is ON, speed is locked to [nightcoreSpeed] (1.20x - 1.25x, default 1.22x).
     */
    val effectiveSpeed: Float
        get() = if (isNightcoreEnabled) {
            nightcoreSpeed.coerceIn(
                NightcoreDspProcessor.MIN_NIGHTCORE_SPEED,
                NightcoreDspProcessor.MAX_NIGHTCORE_SPEED
            )
        } else {
            playbackSpeed.coerceIn(0.50f, 2.00f)
        }

    /**
     * Effective pitch multiplier applied to the audio engine.
     * When Nightcore is ON, pitch is coupled directly to speed (preservesPitch = false)
     * so pitch scales naturally with playback rate (+3.44 semitones at 1.22x).
     */
    val effectivePitch: Float
        get() = when {
            isNightcoreEnabled -> effectiveSpeed
            couplePitchToSpeed -> effectiveSpeed
            else -> 2.0f.pow(pitchSemitones.coerceIn(-12f, 12f) / 12.0f)
        }

    /**
     * Effective semitone shift displayed in the UI.
     */
    val effectiveSemitones: Float
        get() = (12.0f * (ln(effectivePitch.coerceAtLeast(0.25f)) / ln(2.0f)))

    /**
     * True if any DSP effect (Nightcore, custom EQ, speed, or pitch) is currently modifying audio.
     */
    val isAnyEffectActive: Boolean
        get() = isNightcoreEnabled ||
            abs(effectiveSpeed - 1.0f) > 0.01f ||
            abs(effectivePitch - 1.0f) > 0.01f ||
            (isEqualizerEnabled && (
                abs(bassBoostDb) > 0.1f ||
                    abs(trebleBoostDb) > 0.1f ||
                    bandGainsDb.any { abs(it) > 0.1f }
                ))
}

/**
 * Modular controller orchestrating [NightcoreDspProcessor] (real-time Biquad EQ & Dynamics Compressor)
 * and ExoPlayer's [PlaybackParameters] (coupled or independent Speed & Pitch with 150ms exponential
 * anti-click transitions).
 */
class AudioDspController private constructor(
    context: Context
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val dspProcessor: NightcoreDspProcessor = NightcoreDspProcessor()

    private val _dspState = MutableStateFlow(loadPersistedState())
    val dspState: StateFlow<AudioDspState> = _dspState.asStateFlow()

    private var playbackParametersSink: ((PlaybackParameters) -> Unit)? = null
    private var activeAppliedSpeed: Float = 1.0f
    private var activeAppliedPitch: Float = 1.0f
    private var rateRampJob: Job? = null

    init {
        syncProcessorWithState(_dspState.value)
        activeAppliedSpeed = _dspState.value.effectiveSpeed
        activeAppliedPitch = _dspState.value.effectivePitch
    }

    /**
     * Binds ExoPlayer's [PlaybackParameters] setter so [AudioDspController] can drive
     * 150ms exponential speed/pitch ramps cleanly.
     */
    fun bindPlaybackParametersSink(sink: (PlaybackParameters) -> Unit) {
        playbackParametersSink = sink
        val state = _dspState.value
        activeAppliedSpeed = state.effectiveSpeed
        activeAppliedPitch = state.effectivePitch
        sink(PlaybackParameters(activeAppliedSpeed, activeAppliedPitch))
    }

    /**
     * 1-Click binary toggle for Nightcore Mode.
     * Activates coupled speed & pitch resampling (1.22x, ~+3.44 semitones) + 3-stage
     * series EQ/dynamics pipeline (Low-Shelf 90Hz +6dB Q=0.7, Peaking 3.5kHz +2dB,
     * Dynamics Compressor -6dB 4:1 3ms/150ms) with a 150ms exponential ramp.
     */
    fun toggleNightcore() {
        setNightcoreEnabled(!_dspState.value.isNightcoreEnabled)
    }

    fun setNightcoreEnabled(enabled: Boolean) {
        _dspState.update { current ->
            current.copy(isNightcoreEnabled = enabled)
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        animatePlaybackParametersExponentially(
            targetSpeed = updated.effectiveSpeed,
            targetPitch = updated.effectivePitch,
            durationMs = 150L
        )
        persistState(updated)
    }

    fun setNightcoreSpeed(speed: Float) {
        val clamped = speed.coerceIn(
            NightcoreDspProcessor.MIN_NIGHTCORE_SPEED,
            NightcoreDspProcessor.MAX_NIGHTCORE_SPEED
        )
        _dspState.update { it.copy(nightcoreSpeed = clamped) }
        val updated = _dspState.value
        if (updated.isNightcoreEnabled) {
            animatePlaybackParametersExponentially(
                targetSpeed = updated.effectiveSpeed,
                targetPitch = updated.effectivePitch,
                durationMs = 90L
            )
        }
        persistState(updated)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = (kotlin.math.round(speed.coerceIn(0.50f, 2.00f) * 100f) / 100f)
        _dspState.update { current ->
            // If user manually changes independent speed while Nightcore is on, exit Nightcore cleanly
            current.copy(
                isNightcoreEnabled = false,
                playbackSpeed = clamped
            )
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        animatePlaybackParametersExponentially(
            targetSpeed = updated.effectiveSpeed,
            targetPitch = updated.effectivePitch,
            durationMs = 120L
        )
        persistState(updated)
    }

    fun setPitchSemitones(semitones: Float) {
        val clamped = (kotlin.math.round(semitones.coerceIn(-12f, 12f) * 10f) / 10f)
        _dspState.update { current ->
            current.copy(
                isNightcoreEnabled = false,
                couplePitchToSpeed = false,
                pitchSemitones = clamped
            )
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        animatePlaybackParametersExponentially(
            targetSpeed = updated.effectiveSpeed,
            targetPitch = updated.effectivePitch,
            durationMs = 120L
        )
        persistState(updated)
    }

    fun setCouplePitchToSpeed(coupled: Boolean) {
        _dspState.update { current ->
            current.copy(couplePitchToSpeed = coupled)
        }
        val updated = _dspState.value
        animatePlaybackParametersExponentially(
            targetSpeed = updated.effectiveSpeed,
            targetPitch = updated.effectivePitch,
            durationMs = 150L
        )
        persistState(updated)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _dspState.update { it.copy(isEqualizerEnabled = enabled) }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        persistState(updated)
    }

    fun selectPreset(preset: EqPreset) {
        _dspState.update { current ->
            if (preset == EqPreset.CUSTOM) {
                current.copy(selectedPreset = EqPreset.CUSTOM, isEqualizerEnabled = true)
            } else {
                current.copy(
                    selectedPreset = preset,
                    isEqualizerEnabled = true,
                    bandGainsDb = preset.bandGainsDb,
                    bassBoostDb = preset.defaultBassDb,
                    trebleBoostDb = preset.defaultTrebleDb
                )
            }
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        persistState(updated)
    }

    fun setBandGainDb(bandIndex: Int, gainDb: Float) {
        val clamped = (kotlin.math.round(gainDb.coerceIn(-12f, 12f) * 2f) / 2f)
        _dspState.update { current ->
            val mutableBands = current.bandGainsDb.toMutableList()
            if (bandIndex in mutableBands.indices) {
                mutableBands[bandIndex] = clamped
            }
            current.copy(
                isEqualizerEnabled = true,
                selectedPreset = EqPreset.CUSTOM,
                bandGainsDb = mutableBands
            )
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        persistState(updated)
    }

    fun setBassBoostDb(bassDb: Float) {
        val clamped = (kotlin.math.round(bassDb.coerceIn(-12f, 12f) * 2f) / 2f)
        _dspState.update { current ->
            current.copy(
                isEqualizerEnabled = true,
                selectedPreset = EqPreset.CUSTOM,
                bassBoostDb = clamped
            )
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        persistState(updated)
    }

    fun setTrebleBoostDb(trebleDb: Float) {
        val clamped = (kotlin.math.round(trebleDb.coerceIn(-12f, 12f) * 2f) / 2f)
        _dspState.update { current ->
            current.copy(
                isEqualizerEnabled = true,
                selectedPreset = EqPreset.CUSTOM,
                trebleBoostDb = clamped
            )
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        persistState(updated)
    }

    fun setCompressorEnabled(enabled: Boolean) {
        _dspState.update { it.copy(isCompressorEnabled = enabled) }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        persistState(updated)
    }

    fun resetAllEffects() {
        _dspState.update {
            AudioDspState()
        }
        val updated = _dspState.value
        syncProcessorWithState(updated)
        animatePlaybackParametersExponentially(
            targetSpeed = 1.0f,
            targetPitch = 1.0f,
            durationMs = 150L
        )
        persistState(updated)
    }

    /**
     * Flushes and resets biquad filter delay lines and dynamics compressor state
     * when transitioning between tracks.
     */
    fun onTrackTransition() {
        dspProcessor.resetFilterStatesForTrackTransition()
    }

    fun release() {
        rateRampJob?.cancel()
        rateRampJob = null
        dspProcessor.resetFilterStatesForTrackTransition()
    }

    private fun syncProcessorWithState(state: AudioDspState) {
        dspProcessor.setNightcoreEnabled(
            enabled = state.isNightcoreEnabled,
            lowShelfFreqHz = state.nightcoreLowShelfFreqHz,
            lowShelfGainDb = state.nightcoreLowShelfGainDb,
            peakingFreqHz = state.nightcorePeakingFreqHz,
            peakingGainDb = state.nightcorePeakingGainDb
        )
        dspProcessor.setEqualizerEnabled(state.isEqualizerEnabled)
        dspProcessor.setAllBandGainsDb(state.bandGainsDb.toFloatArray())
        dspProcessor.setBassAndTrebleDb(
            bassDb = state.bassBoostDb,
            trebleDb = state.trebleBoostDb
        )
        dspProcessor.setCompressorEnabled(state.isCompressorEnabled)
    }

    /**
     * Smoothly ramps ExoPlayer [PlaybackParameters] (speed & pitch) over [durationMs] (default 150ms)
     * following an exponential curve so changes sound like a physical turntable/vinyl transition
     * with zero clicks or pops.
     */
    private fun animatePlaybackParametersExponentially(
        targetSpeed: Float,
        targetPitch: Float,
        durationMs: Long = 150L
    ) {
        val sink = playbackParametersSink ?: return
        rateRampJob?.cancel()

        val startSpeed = activeAppliedSpeed
        val startPitch = activeAppliedPitch
        if (abs(targetSpeed - startSpeed) < 0.003f && abs(targetPitch - startPitch) < 0.003f) {
            activeAppliedSpeed = targetSpeed
            activeAppliedPitch = targetPitch
            sink(PlaybackParameters(targetSpeed, targetPitch))
            return
        }

        rateRampJob = scope.launch {
            val steps = 10
            val stepDelayMs = (durationMs / steps).coerceAtLeast(12L)
            for (step in 1..steps) {
                val t = step.toFloat() / steps.toFloat()
                // Normalized exponential approach: (1 - e^(-4.2 * t)) / (1 - e^(-4.2))
                val normExp = ((1.0f - exp(-4.2f * t)) / (1.0f - exp(-4.2f))).coerceIn(0f, 1f)
                val nextSpeed = (startSpeed + (targetSpeed - startSpeed) * normExp).coerceIn(0.25f, 3.0f)
                val nextPitch = (startPitch + (targetPitch - startPitch) * normExp).coerceIn(0.25f, 3.0f)
                activeAppliedSpeed = nextSpeed
                activeAppliedPitch = nextPitch
                sink(PlaybackParameters(nextSpeed, nextPitch))
                if (step < steps) {
                    delay(stepDelayMs)
                }
            }
            activeAppliedSpeed = targetSpeed
            activeAppliedPitch = targetPitch
            sink(PlaybackParameters(targetSpeed, targetPitch))
        }
    }

    private fun loadPersistedState(): AudioDspState {
        return runCatching {
            val isNightcore = prefs.getBoolean(KEY_NIGHTCORE_ENABLED, false)
            val ncSpeed = prefs.getFloat(KEY_NIGHTCORE_SPEED, NightcoreDspProcessor.DEFAULT_NIGHTCORE_SPEED)
                .coerceIn(NightcoreDspProcessor.MIN_NIGHTCORE_SPEED, NightcoreDspProcessor.MAX_NIGHTCORE_SPEED)
            val speed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f).coerceIn(0.50f, 2.00f)
            val pitchSt = prefs.getFloat(KEY_PITCH_SEMITONES, 0.0f).coerceIn(-12f, 12f)
            val coupled = prefs.getBoolean(KEY_COUPLE_PITCH, false)
            val eqEnabled = prefs.getBoolean(KEY_EQ_ENABLED, true)
            val presetName = prefs.getString(KEY_EQ_PRESET, EqPreset.FLAT.name) ?: EqPreset.FLAT.name
            val preset = runCatching { EqPreset.valueOf(presetName) }.getOrDefault(EqPreset.FLAT)
            val bass = prefs.getFloat(KEY_BASS_DB, 0f).coerceIn(-12f, 12f)
            val treble = prefs.getFloat(KEY_TREBLE_DB, 0f).coerceIn(-12f, 12f)
            val comp = prefs.getBoolean(KEY_COMPRESSOR_ENABLED, true)
            val bands = List(NightcoreDspProcessor.EQ_BAND_FREQUENCIES_HZ.size) { idx ->
                prefs.getFloat("band_$idx", 0f).coerceIn(-12f, 12f)
            }
            AudioDspState(
                isNightcoreEnabled = isNightcore,
                nightcoreSpeed = ncSpeed,
                playbackSpeed = speed,
                pitchSemitones = pitchSt,
                couplePitchToSpeed = coupled,
                isEqualizerEnabled = eqEnabled,
                selectedPreset = preset,
                bandGainsDb = bands,
                bassBoostDb = bass,
                trebleBoostDb = treble,
                isCompressorEnabled = comp
            )
        }.getOrDefault(AudioDspState())
    }

    private fun persistState(state: AudioDspState) {
        runCatching {
            prefs.edit().apply {
                putBoolean(KEY_NIGHTCORE_ENABLED, state.isNightcoreEnabled)
                putFloat(KEY_NIGHTCORE_SPEED, state.nightcoreSpeed)
                putFloat(KEY_PLAYBACK_SPEED, state.playbackSpeed)
                putFloat(KEY_PITCH_SEMITONES, state.pitchSemitones)
                putBoolean(KEY_COUPLE_PITCH, state.couplePitchToSpeed)
                putBoolean(KEY_EQ_ENABLED, state.isEqualizerEnabled)
                putString(KEY_EQ_PRESET, state.selectedPreset.name)
                putFloat(KEY_BASS_DB, state.bassBoostDb)
                putFloat(KEY_TREBLE_DB, state.trebleBoostDb)
                putBoolean(KEY_COMPRESSOR_ENABLED, state.isCompressorEnabled)
                state.bandGainsDb.forEachIndexed { idx, gain ->
                    putFloat("band_$idx", gain)
                }
                apply()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "xperience_audio_dsp_prefs"
        private const val KEY_NIGHTCORE_ENABLED = "nightcore_enabled"
        private const val KEY_NIGHTCORE_SPEED = "nightcore_speed"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_PITCH_SEMITONES = "pitch_semitones"
        private const val KEY_COUPLE_PITCH = "couple_pitch_to_speed"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_BASS_DB = "bass_db"
        private const val KEY_TREBLE_DB = "treble_db"
        private const val KEY_COMPRESSOR_ENABLED = "compressor_enabled"

        @Volatile
        private var instance: AudioDspController? = null

        fun getInstance(context: Context): AudioDspController {
            return instance ?: synchronized(this) {
                instance ?: AudioDspController(context.applicationContext).also { instance = it }
            }
        }
    }
}

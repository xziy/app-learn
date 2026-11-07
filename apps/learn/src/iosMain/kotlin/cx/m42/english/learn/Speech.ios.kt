package cx.m42.english.learn

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.darwin.NSObject

internal actual fun platformSpeech(): Speech = IosSpeech()

/** The system's own synthesizer: on every iPhone, and it works without a network. */
@OptIn(ExperimentalForeignApi::class)
private class IosSpeech : Speech {
    // Held here: the synthesizer keeps its delegate weakly.
    private val ended = UtteranceEnd()

    private val synthesizer by lazy {
        // Playback, so the sentence is heard with the ringer switch on silent: it is the point of the card.
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, error = null)
            setActive(true, error = null)
        }
        AVSpeechSynthesizer().also { it.delegate = ended }
    }

    override val available: Boolean = true

    override suspend fun speak(text: String) {
        stop()
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
        suspendCancellableCoroutine { sentence ->
            ended.await(utterance) { if (sentence.isActive) sentence.resume(Unit) }
            synthesizer.speakUtterance(utterance)
        }
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}

/** Tells when one utterance is over. The cancelling of the one before it comes late, so it is told apart. */
@OptIn(ExperimentalForeignApi::class)
private class UtteranceEnd : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    private var awaited: AVSpeechUtterance? = null
    private var onEnd: (() -> Unit)? = null

    fun await(utterance: AVSpeechUtterance, onEnd: () -> Unit) {
        awaited = utterance
        this.onEnd = onEnd
    }

    private fun over(utterance: AVSpeechUtterance) {
        if (utterance !== awaited) return
        val end = onEnd
        awaited = null
        onEnd = null
        end?.invoke()
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) =
        over(didFinishSpeechUtterance)

    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) =
        over(didCancelSpeechUtterance)
}

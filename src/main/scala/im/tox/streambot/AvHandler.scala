package im.tox.streambot

import java.nio.ShortBuffer
import java.util

import im.tox.tox4j.av.ToxAv
import im.tox.tox4j.av.callbacks.ToxAvEventListener
import im.tox.tox4j.av.data._
import im.tox.tox4j.av.enums.ToxavFriendCallState
import im.tox.tox4j.core.data.{ ToxFriendMessage, ToxFriendNumber }
import im.tox.tox4j.core.enums.ToxMessageType
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgproc._
import org.bytedeco.javacv.{ FFmpegFrameGrabber, OpenCVFrameConverter }

import scala.annotation.tailrec

final class AvHandler extends ToxAvEventListener[State] {
  private val SendAudio = false
  private val SendVideo = true

  override def call(friendNumber: ToxFriendNumber, audioEnabled: Boolean, videoEnabled: Boolean)(state: State): State = {
    println(s"Incoming call from $friendNumber")
    state.action { (core, av) =>
      println("Answering call")
      av.answer(
        friendNumber,
        if (SendAudio) BitRate.fromInt(64).get else BitRate.Disabled,
        if (SendVideo) BitRate.fromInt(1000 * 1000).get else BitRate.Disabled
      )

      val media = "BigBuckBunny.mp4"
      core.friendSendMessage(friendNumber, ToxMessageType.NORMAL, 0,
        ToxFriendMessage.fromString(s"Sending media from file: $media").toOption.get)

      val grabber = new FFmpegFrameGrabber(media)
      grabber.start()

      sendFrames(friendNumber, av, grabber)
    }
  }

  /**
   * Sends audio/video frames from the grabber until it runs out of frames.
   *
   * This function blocks the main thread, so no more commands can be issued
   * once it is running. There can also only be a single friend receiving
   * media at a time.
   *
   * @param friendNumber The friend to send the next audio/video frame to.
   * @param av Tox AV object.
   * @param grabber The current audio/video decoder.
   */
  @tailrec
  private def sendFrames(friendNumber: ToxFriendNumber, av: ToxAv, grabber: FFmpegFrameGrabber): Unit = {
    val frame = grabber.grab()
    if (frame != null) {
      if (frame.image != null) {
        val bgr = new OpenCVFrameConverter.ToMat().convert(frame)
        val yuv = new Mat(bgr.size, CV_8UC3)

        cvtColor(bgr, yuv, COLOR_BGR2YUV_I420)

        val width = frame.imageWidth
        val height = frame.imageHeight
        val y = Array.ofDim[Byte](width * height)
        val u = Array.ofDim[Byte](width * height / 4)
        val v = Array.ofDim[Byte](width * height / 4)

        assert(y.length + u.length + v.length == yuv.rows * yuv.cols)
        val data = yuv.arrayData().limit(yuv.arraySize())
          .get(y).position(y.length)
          .get(u).position(y.length + u.length)
          .get(v).position(y.length + u.length + v.length)
        assert(data.position() == yuv.arraySize())

        av.videoSendFrame(friendNumber, frame.imageWidth, frame.imageHeight, y, u, v)
      }

      if (SendAudio && frame.samples != null) {
        val samplingRate = SamplingRate.Rate48k //.unsafeFromInt(grabber.getSampleRate)
        val sampleCount = SampleCount(AudioLength.Length20, samplingRate)
        val channels = AudioChannels.fromInt(grabber.getAudioChannels).get

        val samples = frame.samples(0).asInstanceOf[ShortBuffer]

        val pcm = Array.ofDim[Short](sampleCount.value * channels.value)
        samples.get(pcm)

        println(s"av.audioSendFrame($friendNumber, $pcm, $sampleCount, $channels, $samplingRate)")
        av.audioSendFrame(friendNumber, pcm, sampleCount, channels, samplingRate)
      }

      sendFrames(friendNumber, av, grabber)
    }
  }

  override def callState(friendNumber: ToxFriendNumber, callState: util.EnumSet[ToxavFriendCallState])(state: State): State = {
    println(s"Call state for $friendNumber: $callState")
    state
  }
}

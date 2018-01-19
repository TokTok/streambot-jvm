package im.tox.streambot

import java.io.File
import java.nio.ShortBuffer
import java.util

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
  private val FrameLength = 1000 / 24

  override def call(friendNumber: ToxFriendNumber, audioEnabled: Boolean, videoEnabled: Boolean)(state: State): State = {
    println(s"Incoming call from $friendNumber")
    state.action { (core, av) =>
      println("Answering call")
      av.answer(friendNumber, BitRate.fromInt(64).get, BitRate.fromInt(1000 * 1000).get)

      val media = new File(sys.env("HOME") + "/Downloads/BigBuckBunny.mp4")
      if (!media.exists()) {
        core.friendSendMessage(friendNumber, ToxMessageType.NORMAL, 0,
          ToxFriendMessage.fromString("No media available at this time").toOption.get)
      } else {
        core.friendSendMessage(friendNumber, ToxMessageType.NORMAL, 0,
          ToxFriendMessage.fromString(s"Sending media from file: ${media.getName}").toOption.get)

        val grabber = new FFmpegFrameGrabber(media.getPath)
        grabber.start()

        val samplingRate = SamplingRate.Rate48k //.unsafeFromInt(grabber.getSampleRate)
        val sampleCount = SampleCount(AudioLength.Length60, samplingRate)
        val channels = AudioChannels.fromInt(grabber.getAudioChannels).get

        sendFrames(grabber, MediaSink(av, friendNumber, samplingRate, sampleCount, channels))

        core.friendSendMessage(friendNumber, ToxMessageType.NORMAL, 0,
          ToxFriendMessage.fromString("That's all, folks!").toOption.get)
      }
    }
  }

  private def timed[T](block: => T): (T, Long) = {
    val start = System.currentTimeMillis()
    val v = block
    val diff = System.currentTimeMillis() - start
    (v, diff)
  }

  /**
   * Sends audio/video frames from the grabber until it runs out of frames.
   *
   * This function blocks the main thread, so no more commands can be issued
   * once it is running. There can also only be a single friend receiving
   * media at a time.
   *
   * @param grabber The current audio/video decoder.
   */
  @tailrec
  private def sendFrames(grabber: FFmpegFrameGrabber, sink: MediaSink): Unit = {
    val start = System.currentTimeMillis()
    val (frame, decodeTime) = timed { grabber.grab() }

    if (frame != null) {

      if (frame.samples != null) {
        val samples = frame.samples(0).asInstanceOf[ShortBuffer]
        val pcm = Array.ofDim[Short](samples.capacity())
        samples.get(pcm)
      }

      if (frame.image != null) {
        val ((y, u, v), convertTime) = timed {
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

          (y, u, v)
        }

        val ((), sendTime) = timed {
          sink.av.videoSendFrame(sink.friendNumber, frame.imageWidth, frame.imageHeight, y, u, v)
        }

        val end = System.currentTimeMillis()
        if (end - start <= FrameLength) {
          Thread.sleep(FrameLength - (end - start))
        } else {
          println("Warning: video recoding took too long: " +
            s"$decodeTime (dec) + $convertTime (cvt) + $sendTime (enc) ~= ${end - start} > $FrameLength")
        }
      }

      sendFrames(grabber, sink)
    }
  }

  override def callState(friendNumber: ToxFriendNumber, callState: util.EnumSet[ToxavFriendCallState])(state: State): State = {
    println(s"Call state for $friendNumber: $callState")
    state
  }
}

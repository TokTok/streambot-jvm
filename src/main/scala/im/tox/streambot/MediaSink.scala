package im.tox.streambot

import im.tox.tox4j.av.ToxAv
import im.tox.tox4j.av.data.{ AudioChannels, SampleCount, SamplingRate }
import im.tox.tox4j.core.data.ToxFriendNumber
import org.bytedeco.javacv.FFmpegFrameGrabber

final case class MediaSink(
    av: ToxAv,
    friendNumber: ToxFriendNumber,
    samplingRate: SamplingRate,
    sampleCount: SampleCount,
    channels: AudioChannels
)

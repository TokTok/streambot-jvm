package im.tox.streambot

import im.tox.core.error.CoreError
import im.tox.core.network.Port
import im.tox.core.typesafe.{ -\/, \/, \/- }
import im.tox.tox4j.core.data._
import im.tox.tox4j.core.options.{ SaveDataOptions, ToxOptions }
import im.tox.tox4j.impl.jni.{ ToxAvImpl, ToxCoreImpl }

import scala.annotation.tailrec
import scala.collection.immutable.Queue

object StreamBot {
  private val RunActionsInterval = 500

  private val core = new ToxCoreImpl(
    ToxOptions(
      saveData = SaveDataOptions.SecretKey(
        ToxSecretKey.unsafeFromValue(
          Array.ofDim(ToxSecretKey.Size)
        )
      )
    )
  )
  private val coreHandler = new CoreHandler()
  private val av = new ToxAvImpl(core)
  private val avHandler = new AvHandler()

  private var state = State()

  private val coreThread = new Thread(new Runnable {
    @tailrec
    private def loop(): Unit = {
      val start = System.currentTimeMillis()
      StreamBot.synchronized {
        state = core.iterate(coreHandler)(state)
      }
      val end = System.currentTimeMillis()
      val diff = end - start
      val interval = core.iterationInterval
      if (interval > diff) {
        Thread.sleep(interval - diff)
      } else {
        println(s"AV iteration took too much time: ${diff}ms (max ${interval}ms)")
      }
      if (state.runUntil > System.currentTimeMillis()) loop()
    }

    override def run(): Unit = loop()
  }, "ToxCore")

  private val avThread = new Thread(new Runnable {
    @tailrec
    private def loop(): Unit = {
      StreamBot.synchronized {
        state = av.iterate(avHandler)(state)
      }
      Thread.sleep(av.iterationInterval)
      if (state.runUntil > System.currentTimeMillis()) loop()
    }

    override def run(): Unit = loop()
  }, "ToxAv")

  private def runActions(): Unit = {
    val actions = StreamBot.synchronized {
      val current = state.actionQueue
      state = state.copy(actionQueue = Queue.empty)
      current
    }

    for (action <- actions) {
      try {
        action(core, av)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }

    Thread.sleep(RunActionsInterval)
    if (state.runUntil > System.currentTimeMillis()) runActions()
  }

  private def initialise(): \/[CoreError, Unit] = {
    val friendAddress = "7AA63E40745B73B223A3A62DAC26061727BD5A4E53EB1B75C4E37CF94113DA2AE8039766059B"
    val biribiriKey = "F404ABAA1C99A9D37D61AB54898F56793E1DEF8BD46B1038B9D822E8460FAB67"
    for {
      bootstrapPort <- \/.fromEither[im.tox.core.error.CoreError, Port](Port.fromInt(33445).toRight(null))
      bootstrapKey <- ToxPublicKey.fromHexString(biribiriKey)
      address <- ToxFriendAddress.fromHexString(friendAddress)
      requestMessage <- ToxFriendRequestMessage.fromString("Hello!")
      name <- ToxNickname.fromString("StreamBot")
    } yield {
      core.bootstrap("biribiri.org", bootstrapPort, bootstrapKey)
      core.addFriend(address, requestMessage)
      core.setName(name)
    }
  }

  def main(args: Array[String]): Unit = {
    initialise() match {
      case -\/(error) =>
        System.err.println(s"Error: $error")
      case \/-(_) =>
        coreThread.start()
        avThread.start()

        runActions()

        avThread.join()
        coreThread.join()
    }

    av.close()
    core.close()
  }
}

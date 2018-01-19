package im.tox.streambot

import im.tox.tox4j.ToxEventListener
import im.tox.tox4j.core.data.{ ToxFriendMessage, ToxFriendNumber, ToxFriendRequestMessage, ToxPublicKey }
import im.tox.tox4j.core.enums.{ ToxConnection, ToxMessageType }

final class CoreHandler extends ToxEventListener[State] {
  override def friendMessage(friendNumber: ToxFriendNumber, messageType: ToxMessageType, timeDelta: Int, message: ToxFriendMessage)(state: State): State = {
    if (new String(message.value) == "quit") {
      state.copy(runUntil = System.currentTimeMillis() + 2000).action { (core, av) =>
        core.friendSendMessage(friendNumber, ToxMessageType.NORMAL, 0, ToxFriendMessage.fromString("Goodbye").toOption.get)
      }
    } else {
      state
    }
  }

  override def friendRequest(publicKey: ToxPublicKey, timeDelta: Int, message: ToxFriendRequestMessage)(state: State): State = {
    state.action { (core, av) =>
      println(s"Adding friend ${publicKey.toHexString}: ${message.toString}")
      core.addFriendNorequest(publicKey)
    }
  }

  override def selfConnectionStatus(connectionStatus: ToxConnection)(state: State): State = {
    println(s"Our connection: $connectionStatus")
    state
  }

  override def friendConnectionStatus(friendNumber: ToxFriendNumber, connectionStatus: ToxConnection)(state: State): State = {
    println(s"Friend $friendNumber connection: $connectionStatus")
    state
  }
}

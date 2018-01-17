package im.tox.streambot

import im.tox.tox4j.av.ToxAv
import im.tox.tox4j.core.ToxCore

import scala.collection.immutable.Queue

case class State(
    runUntil: Long = Long.MaxValue,
    actionQueue: Queue[(ToxCore, ToxAv) => Unit] = Queue.empty
) {
  def action(function: (ToxCore, ToxAv) => Unit): State =
    copy(actionQueue = actionQueue.enqueue(function))
}

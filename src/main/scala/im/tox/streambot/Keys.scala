package im.tox.streambot

object Keys {
  def showPublicKey(id: Array[Byte]): String = {
    val str = new StringBuilder
    id foreach { c => str.append(f"$c%02X") }
    str.toString()
  }

  def readPublicKey(id: String): Array[Byte] = {
    val publicKey = new Array[Byte](id.length / 2)
    publicKey.indices foreach { i =>
      publicKey(i) =
        ((fromHexDigit(id.charAt(i * 2)) << 4) +
          fromHexDigit(id.charAt(i * 2 + 1))).toByte
    }
    publicKey
  }

  private def fromHexDigit(c: Char): Byte = {
    val digit =
      if (false) { 0 }
      else if ('0' to '9' contains c) { c - '0' }
      else if ('A' to 'F' contains c) { c - 'A' + 10 }
      else if ('a' to 'f' contains c) { c - 'a' + 10 } else { throw new IllegalArgumentException(s"Non-hex digit character: $c") }
    digit.toByte
  }
}

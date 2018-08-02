package streams

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import akka.util.ByteString
import java.security.SecureRandom
import javax.crypto._
import javax.crypto.spec.{SecretKeySpec, IvParameterSpec}

/**
  * Based on Gist https://gist.github.com/TimothyKlim/ec5889aa23400529fd5e
  */
object Crypto {

  private val aesKeySize = 128

  private val rand = new SecureRandom()

  def generateAesKey(): SecretKeySpec = {
    val gen = KeyGenerator.getInstance("AES")
    gen.init(aesKeySize)
    val key = gen.generateKey()
    val aesKey = key.getEncoded
    aesKeySpec(aesKey)
  }

  def generateIv(): Array[Byte] = rand.generateSeed(16)

  def encryptAes(keySpec: SecretKeySpec, ivBytes: Array[Byte])
                (implicit mat: Materializer): Flow[ByteString, ByteString, _] = {
    val cipher = aesCipher(Cipher.ENCRYPT_MODE, keySpec, ivBytes)
    Flow.fromGraph(new AesStage(cipher))
  }

  def decryptAes(keySpec: SecretKeySpec, ivBytes: Array[Byte])
                (implicit mat: Materializer): Flow[ByteString, ByteString, _] = {
    val cipher = aesCipher(Cipher.DECRYPT_MODE, keySpec, ivBytes)
    Flow.fromGraph(new AesStage(cipher))
  }

  private def aesKeySpec(key: Array[Byte]) = new SecretKeySpec(key, "AES")

  private def aesCipher(mode: Int, keySpec: SecretKeySpec, ivBytes: Array[Byte]) = {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val ivSpec = new IvParameterSpec(ivBytes)
    cipher.init(mode, keySpec, ivSpec)
    cipher
  }

}

private[this] class AesStage(cipher: Cipher) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in: Inlet[ByteString] = Inlet[ByteString]("in")
  val out: Outlet[ByteString] = Outlet[ByteString]("out")

  override val shape: FlowShape[ByteString, ByteString] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val bs = grab(in)
          if (bs.isEmpty) push(out, bs)
          else push(out, ByteString(cipher.update(bs.toArray)))
        }

        override def onUpstreamFinish(): Unit = {
          val bs = ByteString(cipher.doFinal())
          if (bs.nonEmpty) emit(out, bs)
          complete(out)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
}
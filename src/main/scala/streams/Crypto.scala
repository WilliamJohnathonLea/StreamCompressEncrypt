package streams

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import akka.util.ByteString
import java.security.{MessageDigest, SecureRandom}

import javax.crypto._
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

/**
  * Based on Gist https://gist.github.com/TimothyKlim/ec5889aa23400529fd5e
  */
object Crypto {

  private val aesKeySize = 128

  private val rand = new SecureRandom()

  def generateAesKey(): Array[Byte] = {
    val gen = KeyGenerator.getInstance("AES")
    gen.init(aesKeySize)
    val key = gen.generateKey()
    key.getEncoded
  }

  def generateIv(): Array[Byte] = rand.generateSeed(16)

  def encryptAes(keyBytes: Array[Byte], ivBytes: Array[Byte])
                (implicit mat: Materializer): Flow[ByteString, ByteString, _] = {
    val cipher = aesCipher(Cipher.ENCRYPT_MODE, keyBytes, ivBytes)
    Flow.fromGraph(new AesStage(cipher))
  }

  def decryptAes(keyBytes: Array[Byte], ivBytes: Array[Byte])
                (implicit mat: Materializer): Flow[ByteString, ByteString, _] = {
    val cipher = aesCipher(Cipher.DECRYPT_MODE, keyBytes, ivBytes)
    Flow.fromGraph(new AesStage(cipher))
  }

  def sha256(implicit mat: Materializer): Flow[ByteString, ByteString, _] = {
    val digest = MessageDigest.getInstance("SHA-256")
    Flow.fromGraph(new Sha256Stage(digest))
  }

  private def aesCipher(mode: Int, keyBytes: Array[Byte], ivBytes: Array[Byte]): Cipher = {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val keySpec = new SecretKeySpec(keyBytes, "AES")
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

private[this] class Sha256Stage(messageDigest: MessageDigest) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in: Inlet[ByteString] = Inlet[ByteString]("in")
  val out: Outlet[ByteString] = Outlet[ByteString]("out")

  override val shape: FlowShape[ByteString, ByteString] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val bs = grab(in)
          if (bs.nonEmpty) messageDigest.update(bs.asByteBuffer)
          push(out, ByteString.empty)
        }

        override def onUpstreamFinish(): Unit = {
          val hash = messageDigest.digest().map { b =>
            val hex = Integer.toHexString(0xff & b)
            if(hex.length == 1) "0" + hex
            else hex
          }.mkString("")
          val bs = ByteString(hash)
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

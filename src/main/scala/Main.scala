import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Compression, Source}
import akka.util.ByteString
import streams.Crypto

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main {

  def main(args: Array[String]): Unit = {

    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()

    val src = Source(List(
      ByteString("Hello, world!"),
      ByteString(" This is a"),
      ByteString(" quick test for encrypting"),
      ByteString(" a file.")
    ))

    val key = Crypto.generateAesKey()
    val iv = Crypto.generateIv()

    val result = src.via(Compression.gzip)
      .via(Crypto.encryptAes(key, iv))
      .via(Crypto.decryptAes(key, iv))
      .via(Compression.gunzip())
      .runFold("")(_ + _.utf8String)

    println(Await.result(result, Duration.Inf))

    sys.terminate()
  }

}

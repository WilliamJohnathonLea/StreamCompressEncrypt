import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Compression, GraphDSL, RunnableGraph, Sink, Source}
import akka.util.ByteString
import streams.Crypto

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main {

  def main(args: Array[String]): Unit = {

    implicit val sys: ActorSystem = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()

    def src = Source(List(
      ByteString("Hello, world!"),
      ByteString(" This is a"),
      ByteString(" quick test for encrypting"),
      ByteString(" a file.")
    ))

    val stringSink = Sink.fold[String, ByteString]("")(_ + _.utf8String)

    val key = Crypto.generateAesKey()
    val iv = Crypto.generateIv()

    val graph = RunnableGraph.fromGraph(GraphDSL.create(stringSink, stringSink, stringSink)((_,_,_)) { implicit b =>
      (textSink, sha256Sink, md5Sink) =>
        import GraphDSL.Implicits._

        val broadcast = b.add(Broadcast[ByteString](3))

        src ~> Compression.gzip ~> Crypto.encryptAes(key, iv) ~> broadcast

        broadcast ~> Crypto.decryptAes(key, iv) ~> Compression.gunzip() ~> textSink
        broadcast ~> Crypto.sha256 ~> sha256Sink
        broadcast ~> Crypto.md5 ~> md5Sink

        ClosedShape
    })

    val (enc, sha256, md5) = graph.run()


    println("GZIP -> Encrypt -> Decrypt -> GUNZIP")
    println(Await.result(enc, Duration.Inf))
    println("SHA-256")
    println(Await.result(sha256, Duration.Inf))
    println("MD5")
    println(Await.result(md5, Duration.Inf))

    sys.terminate()
  }

}

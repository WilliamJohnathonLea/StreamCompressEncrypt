import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Compression, GraphDSL, RunnableGraph, Sink, Source}
import akka.util.ByteString
import streams.Crypto

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main {

  def main(args: Array[String]): Unit = {

    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()

    def src = Source(List(
      ByteString("Hello, world!"),
      ByteString(" This is a"),
      ByteString(" quick test for encrypting"),
      ByteString(" a file.")
    ))

    val stringSink = Sink.fold[String, ByteString]("")(_ + _.utf8String)

    val key = Crypto.generateAesKey()
    val iv = Crypto.generateIv()

    val graph = RunnableGraph.fromGraph(GraphDSL.create(stringSink, stringSink)((_,_)) { implicit b => (sink1, sink2) =>
      import GraphDSL.Implicits._

      val broadcast = b.add(Broadcast[ByteString](2))

      src ~> Compression.gzip ~> Crypto.encryptAes(key, iv) ~> broadcast

      broadcast ~> Crypto.decryptAes(key, iv) ~> Compression.gunzip() ~> sink1
      broadcast ~> Crypto.sha256 ~> sink2

      ClosedShape
    })

    val (enc, hash) = graph.run()


    println("GZIP -> Encrypt -> Decrypt -> GUNZIP")
    println(Await.result(enc, Duration.Inf))
    println("SHA-256")
    println(Await.result(hash, Duration.Inf))

    sys.terminate()
  }

}

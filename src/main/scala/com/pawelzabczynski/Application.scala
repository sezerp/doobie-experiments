package com.pawelzabczynski

import doobie._
import doobie.implicits._
import cats.effect.{ExitCode, IO}
import cats.implicits._
import doobie._
import doobie.implicits._
import fs2.Stream
import fs2.Stream.{bracket, bracketCase, eval}

import java.sql.{PreparedStatement, ResultSet}
import doobie.util.stream.repeatEvalChunks
import cats.effect.unsafe.implicits.global

import java.util.UUID

object Application extends App {

  val xa = Transactor.fromDriverManager[IO](
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql:zio_template",
    user = "postgres",
    password = "unsafe_password",
    logHandler = None
  )

  type Row = Map[String, Any]

  // This escapes to raw JDBC for efficiency.
  @SuppressWarnings(
    Array(
      "org.wartremover.warts.Var",
      "org.wartremover.warts.While",
      "org.wartremover.warts.NonUnitStatements"
    )
  )
  def getNextChunkGeneric(chunkSize: Int): ResultSetIO[Seq[Row]] =
    FRS.raw { rs =>
      val md = rs.getMetaData
      val ks = (1 to md.getColumnCount).map(md.getColumnLabel).toList
      var n  = chunkSize
      val b  = Vector.newBuilder[Row]
      while (n > 0 && rs.next) {
        val mb = Map.newBuilder[String, Any]
        val types = (1 to md.getColumnCount).map(rs.getMetaData.getColumnClassName)
        types.foreach(println)
        ks.foreach(k => mb += (k -> rs.getObject(k)))
        b += mb.result()
        n -= 1
      }
      b.result()
    }

  def liftProcessGeneric(
      chunkSize: Int,
      create: ConnectionIO[PreparedStatement],
      prep: PreparedStatementIO[Unit],
      exec: PreparedStatementIO[ResultSet]
  ): Stream[ConnectionIO, Row] = {

    def prepared(
        ps: PreparedStatement
    ): Stream[ConnectionIO, PreparedStatement] =
      eval[ConnectionIO, PreparedStatement] {
        val fs = FPS.setFetchSize(chunkSize)
        FC.embed(ps, fs *> prep).map(_ => ps)
      }

    def unrolled(rs: ResultSet): Stream[ConnectionIO, Row] =
      repeatEvalChunks(FC.embed(rs, getNextChunkGeneric(chunkSize)))

    val preparedStatement: Stream[ConnectionIO, PreparedStatement] =
      bracket(create)(FC.embed(_, FPS.close)).flatMap(prepared)

    def results(ps: PreparedStatement): Stream[ConnectionIO, Row] =
      bracket(FC.embed(ps, exec))(FC.embed(_, FRS.close)).flatMap(unrolled)

    preparedStatement.flatMap(results)

  }

  def processGeneric(
      sql: String,
      prep: PreparedStatementIO[Unit],
      chunkSize: Int
  ): Stream[ConnectionIO, Row] =
    liftProcessGeneric(
      chunkSize,
      FC.prepareStatement(sql),
      prep,
      FPS.executeQuery
    )

  val x = sql"SELECT count(*) FROM users"
    .query[Int]
    .unique
    .transact(xa)
    .unsafeRunSync()

  println(x)

  val statement2 = sql"SELECT * FROM users".query.sql

  val r = processGeneric(statement2, ().pure[PreparedStatementIO], 1024)
    .transact(xa)
    .evalMap{m =>
      m.foreach {
        case (k, v) =>
          println(s"$k -> $v")
          v match {
            case e: String => println(s"$e is a String")
            case e: UUID => println(s"$e is a UUID")
            case e: java.sql.Timestamp=> println(s"$e is a Timestamp")
            case e: Int => println(s"$e is a Int")
            case e: Int => println(s"$e is a Long")
            case e => println(s"$e is a other type")
          }
      }
      IO(Console.println(m))
    }
    .compile
    .drain

//    .as(ExitCode.Success)
//    .unsafeRunSync()

  println(r)
}

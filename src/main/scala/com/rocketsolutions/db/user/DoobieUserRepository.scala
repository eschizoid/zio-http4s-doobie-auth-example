package com.rocketsolutions.db.user

import com.rocketsolutions.config.DBConfig
import com.rocketsolutions.db.user.UserRepository.Service
import com.rocketsolutions.db.{UserError, UserNotFound}
import com.rocketsolutions.main.AppTask
import com.rocketsolutions.model.User
import doobie.implicits._
import doobie.util.transactor.Transactor
import scalaz.zio._
import scalaz.zio.interop.catz._

trait DoobieUserRepository extends UserRepository {

  protected def transactor: Transactor[Task]

  override val userPersistence: Service[Any] = new Service[Any] {

    override def createTable: UIO[Unit] =
      sql"""
           | DROP TABLE IF EXISTS Users;
           | CREATE TABLE Users (id int PRIMARY KEY, name varchar, password varchar);
           """.stripMargin
        .update
        .run
        .transact(transactor)
        .orDie
        .unit *> insertDefaultUser

    def insertDefaultUser: UIO[Unit] =
      IO.foldLeft[Any, Nothing, Unit, User](UserRepository.defaultUser)(Unit)((_,u: User) => insertUser(u))

    override def testDatabase: UIO[Unit] =
      sql"""SELECT 1"""
        .query[Int]
        .unique
        .transact(transactor)
        .orDie
        .unit

    override def listAllUsers: UIO[List[User]] =
      sql"""SELECT id, name, password FROM Users"""
        .query[User]
        .to[List]
        .transact(transactor)
        .orDie

    override def insertUser(u: User): UIO[Unit] =
      sql"""INSERT INTO Users (id, name, password) VALUES (${u.id}, ${u.name}, ${u.password})"""
        .update
        .run
        .transact(transactor)
        .orDie
        .unit


    override def authUser(name: String, password: String): IO[UserError, User] =
      sql"""SELECT id, name, password FROM Users u where LOWER(u.name) = ${name.toLowerCase} AND LOWER(u.password) = ${password.toLowerCase}"""
        .query[User]
        .option
        .transact(transactor)
        .orDie
        .flatMap {
          case Some(name) => ZIO.succeed(name)
          case None => ZIO.fail(UserNotFound)
        }

  }
}

object DoobieUserRepository {

  def xa(db: DBConfig) = Transactor.fromDriverManager[Task](
    "org.postgresql.Driver", db.url, db.username, db.password
  )

}

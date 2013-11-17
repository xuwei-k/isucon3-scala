package com.github.m0t0k1ch1.isucon

import com.github.m0t0k1ch1.isucon.model._
import com.github.m0t0k1ch1.isucon.schema._

import org.scalatra._

import scala.slick.driver.MySQLDriver.simple._
import Database.threadLocalSession

import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._

import java.io.File
import org.apache.commons.io.FileUtils

import org.apache.commons.codec.digest.DigestUtils

case class Isucon(db: Database, dataDir: String) extends ScalatraServlet with IsuconRoutes

trait IsuconRoutes extends IsuconStack with JacksonJsonSupport
{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val db:      Database
  val dataDir: String

  def uriFor(path: String): String = {
    val scheme = request.getScheme
    val host = Option(request.getHeader("X-FORWARDED-HOST")) match {
      case Some(v) => v
      case None    => request.getHeader("HOST")
    }
    s"${scheme}://${host}${path}"
  }

  def isValidUserName(name: String): Boolean = {
    val regex = """[0-9a-zA-Z_]{2,16}""".r
    name match {
      case regex() => true
      case _       => false
    }
  }

  before() {
    contentType = formats("json")
  }

  get("/db/create-tables") {
    db withSession {
      (Users.ddl ++ Entries.ddl ++ FollowMaps.ddl).create
    }
  }

  get("/") {
    val dir    = new File("./src/main/webapp").getAbsolutePath()
    val file   = s"${dir}/index.html"
    val source = FileUtils.readFileToString(new File(file))
    contentType = "text/html"
    source
  }

  post("/signup") {
    db withSession {
      val name = params("name")
      if (!isValidUserName(name)) halt(400)

      val api_key = DigestUtils.sha256Hex(java.util.UUID.randomUUID.toString)

      val id   = Users.autoInc.insert(name, api_key, "default")
      val user = Query(Users).filter(_.id === id).firstOption.get

      User(user.id, user.name, uriFor("/icon/" + user.icon), user.api_key)
    }
  }
}

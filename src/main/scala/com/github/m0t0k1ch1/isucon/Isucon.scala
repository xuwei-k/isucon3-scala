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

case class Isucon(db: Database, dataDir: String) extends ScalatraServlet with IsuconRoutes

trait IsuconRoutes extends IsuconStack with JacksonJsonSupport
{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val db: Database
  val dataDir: String

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
}

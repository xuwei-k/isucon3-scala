package com.github.m0t0k1ch1.isucon

import com.github.m0t0k1ch1.isucon.model._
import com.github.m0t0k1ch1.isucon.schema._

import org.scalatra._
import org.scalatra.json._
import org.scalatra.servlet.{FileUploadSupport, MultipartConfig, SizeConstraintExceededException}

import org.slf4j.LoggerFactory

import scala.slick.driver.MySQLDriver.simple._
import Database.threadLocalSession

import java.io.File
import org.apache.commons.io.FileUtils
import org.apache.commons.codec.digest.DigestUtils
import org.json4s.{DefaultFormats, Formats}
import scala.sys.process.Process

case class Isucon(db: Database, dataDir: String) extends ScalatraServlet with IsuconRoutes

trait IsuconRoutes extends IsuconStack with JacksonJsonSupport with FileUploadSupport
{
  protected implicit val jsonFormats: Formats = DefaultFormats

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3*1024*1024)))

  val logger = LoggerFactory.getLogger(getClass)

  val db:      Database
  val dataDir: String

  val timeout:  Int = 30
  val interval: Int = 2

  val iconS: Int = 32
  val iconM: Int = 64
  val iconL: Int = 128
  val imageS: Option[Int] = Option(128)
  val imageM: Option[Int] = Option(256)
  val imageL: Option[Int] = None

  def convert(orig: String, ext: String, w: Int, h: Int): Array[Byte] = {
    val file        = File.createTempFile("ISUCON", "")
    val fileName    = file.getPath
    val newFileName = s"${fileName}.${ext}"
    Process(s"convert -geometry ${w}x${h} ${orig} ${newFileName}") !
    val newFile = FileUtils.readFileToByteArray(new File(newFileName))
    new File(fileName).delete
    new File(newFileName).delete
    newFile
  }

  def cropSquare(orig: String, ext: String): String = {
    val sizes = size(orig)
    val cropSizes = sizes match {
      case (w, h) if w > h => (h, ((w - h) / 2).floor.toInt, 0)
      case (w, h) if w < h => (w, 0, ((h - w) / 2).floor.toInt)
      case (w, h)          => (w, 0, 0)
    }
    val pixels = cropSizes._1
    val cropX  = cropSizes._2
    val cropY  = cropSizes._3

    val file     = File.createTempFile("ISUCON", "")
    val fileName = file.getPath
    new File(fileName).delete

    val newFileName = s"${fileName}.${ext}"
    Process(s"convert -crop ${pixels}x${pixels}+${cropX}+${cropY} ${orig} ${newFileName}") !;
    newFileName
  }

  def size(fileName: String): (Int, Int) = {
    val identity   = Process(s"identify ${fileName}") !!
    val identities = identity.split(" +")
    val sizes      = identities(2).split("x")
    (sizes(0).toInt, sizes(1).toInt)
  }

  def uriFor(path: String): String = {
    val scheme = request.getScheme
    val host = Option(request.getHeader("X-FORWARDED-HOST")) match {
      case Some(v) => v
      case None    => request.getHeader("HOST")
    }
    s"${scheme}://${host}${path}"
  }

  def isValidUserName(name: String): Boolean = {
    val regex = """^[0-9a-zA-Z_]{2,16}$""".r
    name match {
      case regex() => true
      case _       => false
    }
  }

  def isJpgOrPng(contentType: String): Boolean = {
    val regex = """^image/jpe?g|image/png$""".r
    contentType match {
      case regex() => true
      case _       => false
    }
  }

  def isJpg(contentType: String): Boolean = {
    val regex = """^image/jpe?g$""".r
    contentType match {
      case regex() => true
      case _       => false
    }
  }

  def getUser: Option[User] = {
    db withSession {
      val apiKey = Option(request.getHeader("X-API-KEY")) match {
        case Some(v) => Option(v)
        case None    => cookies.get("api_key")
      }
      val user = apiKey match {
        case Some(v) => Query(Users).filter(_.apiKey === v).firstOption
        case None    => None
      }
      user
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
    val source = FileUtils.readFileToString(new File(file), "utf-8")
    contentType = "text/html"
    source
  }

  post("/signup") {
    db withSession {
      val name = params("name")
      if (!isValidUserName(name)) halt(400)

      val apiKey = DigestUtils.sha256Hex(java.util.UUID.randomUUID.toString)

      val userId = Users.autoInc.insert(name, apiKey, "default")
      val user   = Query(Users).filter(_.id === userId).firstOption.get

      case class Result (id: Int, name:String, icon: String, api_key: String)
      new Result(user.id.get, user.name, uriFor("/icon/" + user.icon), user.apiKey)
    }
  }

  get("/me") {
    db withSession {
      val userContainer = getUser
      if (userContainer.isEmpty) halt(400)

      val user = userContainer.get
      case class Result (id: Int, name: String, icon: String)
      new Result(user.id.get, user.name, uriFor("/icon/" + user.icon))
    }
  }

  get("/icon/:icon") {
    db withSession {
      val icon = params("icon")
      val size = params.get("size") match {
        case Some(v) => v
        case None    => "s"
      }
      if (!new File(s"${dataDir}/icon/${icon}.png").exists) halt(404)

      val w = size match {
        case "s" => iconS
        case "m" => iconM
        case "l" => iconL
        case _   => iconS
      }
      val h = w

      val data = convert(s"${dataDir}/icon/${icon}.png", "png", w, h)

      contentType = "image/png"
      data
    }
  }

  post("/icon") {
    db withSession {
      val userContainer = getUser
      if (userContainer.isEmpty) halt(400)

      val user = userContainer.get

      val uploadContainer = Option(fileParams("image"))
      if (uploadContainer.isEmpty) halt(400)

      val upload = uploadContainer.get
      if (!isJpgOrPng(upload.getContentType.get)) halt(400)

      val file = File.createTempFile("ISUCON", "")
      upload.write(file)
      val fileName = cropSquare(file.getPath, "png")

      val icon = DigestUtils.sha256Hex(java.util.UUID.randomUUID.toString)
      if (!new File(fileName).renameTo(new File(s"${dataDir}/icon/${icon}.png"))) halt(500)

      val userIcon = for {
        u <- Users
        if u.id === user.id.get
      } yield u.icon
      userIcon.update(icon)

      case class Result(icon: String)
      new Result(uriFor(s"/icon/${icon}"))
    }
  }
}

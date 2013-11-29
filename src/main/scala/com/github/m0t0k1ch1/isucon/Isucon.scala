package com.github.m0t0k1ch1.isucon

import com.github.m0t0k1ch1.isucon.model._
import com.github.m0t0k1ch1.isucon.schema._

import org.scalatra._
import org.scalatra.json._
import org.scalatra.servlet.{FileItem, FileUploadSupport, MultipartConfig, SizeConstraintExceededException}

import org.slf4j.LoggerFactory

import scala.slick.driver.MySQLDriver.simple._
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import Database.threadLocalSession
import Q.interpolation

import java.io.File
import java.sql.Timestamp
import org.apache.commons.io.FileUtils
import org.apache.commons.codec.digest.DigestUtils
import org.json4s.{DefaultFormats, Formats}
import scala.sys.process.Process
import scala.util.control.Breaks

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
  val imageS: Option[Int] = Some(128)
  val imageM: Option[Int] = Some(256)
  val imageL: Option[Int] = None

  def now: Timestamp = {
    new Timestamp(System.currentTimeMillis)
  }

  def toInt(string: String): Option[Int] = {
    try {
      Some(string.toInt)
    } catch {
      case e: Exception => None
    }
  }

  def size(fileName: String): (Int, Int) = {
    val identity   = Process(s"identify ${fileName}") !!
    val identities = identity.split(" +")
    val sizes      = identities(2).split("x")
    (sizes(0).toInt, sizes(1).toInt)
  }

  def convert(orig: String, ext: String, w: Int, h: Int): Array[Byte] = {
    val file        = File.createTempFile("ISUCON", "")
    val newFileName = file.getPath + ".${ext}"

    Process(s"convert -geometry ${w}x${h} ${orig} ${newFileName}") !

    val newFile = new File(newFileName)
    if (!newFile.exists) halt(500)
    val source = FileUtils.readFileToByteArray(newFile)

    file.delete
    newFile.delete

    source
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

    val file        = File.createTempFile("ISUCON", "")
    val newFileName = file.getPath + s".${ext}"

    Process(s"convert -crop ${pixels}x${pixels}+${cropX}+${cropY} ${orig} ${newFileName}") !

    file.delete

    newFileName
  }

  def cropAndConvert(orig: String, ext: String, w: Int, h: Int): Array[Byte] = {
    val newFileName = cropSquare(orig, ext)
    val source      = convert(newFileName, ext, w, h)
    new File(newFileName).delete
    source
  }

  def fileSource(fileName: String): Array[Byte] = {
    val file = new File(fileName)
    if (!file.exists) halt(500)
    FileUtils.readFileToByteArray(file)
  }

  def uriFor(path: String): String = {
    val host = Option(request.getHeader("X-FORWARDED-HOST")) match {
      case Some(v) => v
      case None    => request.getHeader("HOST")
    }
    request.getScheme + s"://${host}${path}"
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

  def isFollowing(user: Int, target: Int): Boolean = {
    Query(FollowMaps).filter(_.user === user).filter(_.target === target).firstOption match {
      case Some(v) => true
      case None    => false
    }
  }

  def canViewEntry(entry: Entry, userContainer: Option[User]): Boolean = {
    entry.publishLevel match {
      case 0 if userContainer.isDefined && entry.user == userContainer.get.id            => true
      case 1 if userContainer.isDefined && entry.user == userContainer.get.id            => true
      case 1 if userContainer.isDefined && isFollowing(userContainer.get.id, entry.user) => true
      case 2 => true
      case _ => false
    }
  }

  def getUserContainerById(id: Int): Option[User] = {
    db withSession {
      Query(Users).filter(_.id === id).firstOption
    }
  }

  def updateUserIcon(id: Int, icon: String): Int = {
    val userIcon = for {
      u <- Users
      if u.id === id
    } yield u.icon
    userIcon.update(icon)
  }

  def getEntryContainerById(id: Int): Option[Entry] = {
    db withSession {
      Query(Entries).filter(_.id === id).firstOption
    }
  }

  def getEntryContainerByImage(image: String): Option[Entry] = {
    db withSession {
      Query(Entries).filter(_.image === image).firstOption
    }
  }

  def deleteEntryById(id: Int): Int = {
    db withSession {
      Query(Entries).filter(_.id === id).delete
    }
  }

  def deleteFollowMapByUserAndTarget(user: Int, target: Int): Int = {
    db withSession {
      Query(FollowMaps).filter(_.user === user).filter(_.target === target).delete
    }
  }

  def getFollowings(user: Int): List[User] = {
    db withSession {
      implicit val getUserResult = GetResult(r => User(r.<<, r.<<, r.<<, r.<<))
      sql"SELECT users.* FROM follow_map JOIN users ON (follow_map.target = users.id) WHERE follow_map.user = ${user} ORDER BY follow_map.created_at DESC".as[User].list
    }
  }

  def getTimeline(userId: Int, latestEntryContainer: Option[String]): List[Entry] = {
    val end = new Timestamp(now.getTime + timeout * 1000)

    var entries: List[Entry] = Nil

    val loop = new Breaks
    loop.breakable {
      while (now.before(end)) {
        entries = latestEntryContainer match {
          case Some(v) => getLatestEntriesAgain(userId, v.toInt)
          case None    => getLatestEntriesFirstTime(userId)
        }
        if (entries.nonEmpty) loop.break
        Process("sleep ${interval}") !
      }
    }

    entries
  }

  def getLatestEntriesFirstTime(userId: Int): List[Entry] = {
    db withSession {
      implicit val getEntryResult = GetResult(r => Entry(r.<<, r.<<, r.<<, r.<<, r.<<))
      sql"SELECT * FROM entries WHERE (user = ${userId} OR publish_level = 2 OR (publish_level = 1 AND user IN (SELECT target FROM follow_map WHERE user = ${userId}))) ORDER BY id DESC LIMIT 30".as[Entry].list
    }
  }

  def getLatestEntriesAgain(userId: Int, latestEntry: Int): List[Entry] = {
    db withSession {
      implicit val getEntryResult = GetResult(r => Entry(r.<<, r.<<, r.<<, r.<<, r.<<))
      sql"SELECT * FROM (SELECT * FROM entries WHERE (user = ${userId} OR publish_level = 2 OR (publish_level = 1 AND user IN (SELECT target FROM follow_map WHERE user = ${userId}))) AND id > ${latestEntry} ORDER BY id LIMIT 30) AS e ORDER BY e.id DESC".as[Entry].list
    }
  }

  def getUpload(name: String): FileItem = {
    val uploadContainer = Option(fileParams(name))
    if (uploadContainer.isEmpty) halt(400)
    uploadContainer.get
  }

  def getUserContainer: Option[User] = {
    db withSession {
      val apiKey = Option(request.getHeader("X-API-KEY")) match {
        case Some(v) => Some(v)
        case None    => cookies.get("api_key")
      }
      val userContainer = apiKey match {
        case Some(v) => Query(Users).filter(_.apiKey === v).firstOption
        case None    => None
      }
      userContainer
    }
  }

  def getUser: User = {
    val userContainer = getUserContainer
    if (userContainer.isEmpty) halt(400)
    userContainer.get
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
    val source = FileUtils.readFileToString(new File(s"${dir}/index.html"), "utf-8")

    contentType = "text/html"
    source
  }

  post("/signup") {
    db withSession {
      val nameContainer = params.get("name")
      if (nameContainer.isEmpty || !isValidUserName(nameContainer.get)) halt(400)

      val apiKey = DigestUtils.sha256Hex(java.util.UUID.randomUUID.toString)

      val userId = Users.autoInc.insert(nameContainer.get, apiKey, "default")
      val user   = getUserContainerById(userId).get

      case class Result (id: Int, name:String, icon: String, api_key: String)
      new Result(user.id, user.name, uriFor("/icon/" + user.icon), user.apiKey)
    }
  }

  get("/me") {
    db withSession {
      val user = getUser

      case class Result (id: Int, name: String, icon: String)
      new Result(user.id, user.name, uriFor("/icon/" + user.icon))
    }
  }

  get("/icon/:icon") {
    db withSession {
      val icon     = params("icon")
      val fileName = s"${dataDir}/icon/${icon}.png"
      if (!new File(fileName).exists) halt(404)

      val size = params.get("size") match {
        case Some(v) => v
        case None    => "s"
      }
      val w = size match {
        case "s" => iconS
        case "m" => iconM
        case "l" => iconL
        case _   => iconS
      }
      val h = w

      val source = convert(fileName, "png", w, h)

      contentType = "image/png"
      source
    }
  }

  post("/icon") {
    db withSession {
      val user = getUser

      val upload     = getUpload("image")
      val contentType = upload.getContentType
      if (contentType.isEmpty) halt(400)
      if (!isJpgOrPng(contentType.get)) halt(400)

      val file = File.createTempFile("ISUCON", "")
      upload.write(file)
      val fileName = cropSquare(file.getPath, "png")
      file.delete

      val icon = DigestUtils.sha256Hex(java.util.UUID.randomUUID.toString)
      if (!new File(fileName).renameTo(new File(s"${dataDir}/icon/${icon}.png"))) halt(500)

      updateUserIcon(user.id, icon)

      case class Result(icon: String)
      new Result(uriFor(s"/icon/${icon}"))
    }
  }

  post("/entry") {
    db withSession {
      val user = getUser

      val upload      = getUpload("image")
      val contentType = upload.getContentType
      if (contentType.isEmpty) halt(400)
      if (!isJpg(upload.getContentType.get)) halt(400)

      val file = File.createTempFile("ISUCON", "")
      upload.write(file)

      val image = DigestUtils.sha256Hex(java.util.UUID.randomUUID.toString)
      if (!file.renameTo(new File(s"${dataDir}/image/${image}.jpg"))) halt(500)

      val publishLevelContainer = toInt(params("publish_level"))
      if (publishLevelContainer.isEmpty) halt(400)
      val publishLevel = publishLevelContainer.get

      val entryId = Entries.autoInc.insert(user.id, image, publishLevel, now)
      val entry   = getEntryContainerById(entryId).get

      case class ResultUser(id: Int, name: String, icon: String)
      case class ResultEntry(id: Int, image: String, publish_level: Int, user: ResultUser)
      new ResultEntry(
        entryId, uriFor("/image/" + entry.image), publishLevel,
        new ResultUser(user.id, user.name, uriFor("/icon/" + user.icon))
      )
    }
  }

  post("/entry/:id") {
    db withSession {
      val user = getUser

      if (params("__method") != "DELETE") halt(400)

      val entryIdContainer = toInt(params("id"))
      if (entryIdContainer.isEmpty) halt(404)
      val entryId = entryIdContainer.get

      val entryContainer = getEntryContainerById(entryId)
      if (entryContainer.isEmpty) halt(404)
      val entry = entryContainer.get
      if (entry.user != user.id) halt(400)

      deleteEntryById(entryId)

      case class Result(ok: Boolean)
      new Result(true)
    }
  }

  get("/image/:image") {
    db withSession {
      val userContainer = getUserContainer

      val image = params("image")
      val size = params.get("size") match {
        case Some(v) => v
        case None    => "l"
      }

      val entryContainer = getEntryContainerByImage(image)
      if (entryContainer.isEmpty) halt(404)
      val entry = entryContainer.get
      if (!canViewEntry(entry, userContainer)) halt(404)

      val w = size match {
        case "s" => imageS
        case "m" => imageM
        case "l" => imageL
        case _   => imageL
      }
      val h = w

      val fileName = s"${dataDir}/image/${image}.jpg"
      val source = w match {
        case Some(v) => cropAndConvert(fileName, "jpg", w.get, h.get)
        case None    => fileSource(fileName)
      }

      contentType = "image/jpeg"
      source
    }
  }

  get("/follow") {
    val user = getUser

    case class ResultUser(id: Int, name: String, icon: String)
    case class Result(users: List[ResultUser])

    val resultUsers = for (
      user <- getFollowings(user.id)
    ) yield ResultUser(user.id, user.name, uriFor("/icon/" + user.icon))
    new Result(resultUsers)
  }

  post("/follow") {
    db withSession {
      val user = getUser

      val targetsContainer = multiParams.get("target")
      if (targetsContainer.isDefined) {
        for (target <- targetsContainer.get) {
          if (toInt(target).isDefined) {
            FollowMaps.insert(FollowMap(user.id, target.toInt, now))
          }
        }
      }

      case class ResultUser(id: Int, name: String, icon: String)
      case class Result(users: List[ResultUser])

      val resultUsers = for (
        user <- getFollowings(user.id)
      ) yield ResultUser(user.id, user.name, uriFor("/icon/" + user.icon))
      new Result(resultUsers)
    }
  }

  post("/unfollow") {
    db withSession {
      val user = getUser

      val targetsContainer = multiParams.get("target")
      if (targetsContainer.isDefined) {
        for (target <- targetsContainer.get) {
          if (toInt(target).isDefined) {
            deleteFollowMapByUserAndTarget(user.id, target.toInt)
          }
        }
      }

      case class ResultUser(id: Int, name: String, icon: String)
      case class Result(users: List[ResultUser])

      val resultUsers = for (
        user <- getFollowings(user.id)
      ) yield ResultUser(user.id, user.name, uriFor("/icon/" + user.icon))
      new Result(resultUsers)
    }
  }

  get("/timeline") {
    db withSession {
      val user = getUser

      val latestEntryContainer = params.get("latest_entry")
      if (latestEntryContainer.nonEmpty && toInt(latestEntryContainer.get).isEmpty) halt(404)

      val entries = getTimeline(user.id, latestEntryContainer)

      val latestEntry = entries match {
        case v if v.nonEmpty                                  => entries.head.id
        case v if v.isEmpty && latestEntryContainer.isDefined => latestEntryContainer.get.toInt
        case _                                                => 0
      }

      case class ResultUser(id: Int, name: String, icon: String)
      case class ResultEntry(id: Int, image: String, publish_level: Int, user: ResultUser)
      case class Result(latest_entry: Int, entries: List[ResultEntry])

      response.setHeader("Cache-Control", "no-cache")
      val resultEntries = for (
        entry <- entries;
        user = getUserContainerById(entry.user).get
      ) yield ResultEntry(
        entry.id,
        uriFor("/image/" + entry.image),
        entry.publishLevel,
        ResultUser(
          user.id,
          user.name,
          uriFor("/icon/" + user.icon)
        )
      )
      new Result(latestEntry, resultEntries)
    }
  }
}

package com.github.m0t0k1ch1

import org.scalatra._

import java.util.Properties
import org.slf4j.LoggerFactory

import com.mchange.v2.c3p0.ComboPooledDataSource
import scala.slick.driver.MySQLDriver.simple._
import Database.threadLocalSession

import scala.util.parsing.json.JSON
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._

import java.sql.Timestamp

import java.io.File
import org.apache.commons.io.FileUtils

case class User(
  id:      Int,
  name:    String,
  api_key: String,
  icon:    String
)
object Users extends Table[User]("users")
{
  def id      = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name    = column[String]("name")
  def api_key = column[String]("api_key")
  def icon    = column[String]("icon", O.Default("default"))

  def * = id ~ name ~ api_key ~ icon <> (User, User.unapply _)
  def idx = index("users_api_key", api_key, unique = true)
}

case class Entry(
  id:            Int,
  user:          Int,
  image:         String,
  publish_level: Int,
  created_at:    Timestamp
)
object Entries extends Table[Entry]("entries")
{
  def id            = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def user          = column[Int]("user")
  def image         = column[String]("image")
  def publish_level = column[Int]("publish_level", O.Default(0))
  def created_at    = column[Timestamp]("created_at", O.DBType("datetime"))

  def * = id ~ user ~ image ~ publish_level ~ created_at <> (Entry, Entry.unapply _)
  def idx = index("entries_user", user)
}

case class FollowMap(
  user:       Int,
  target:     Int,
  created_at: Timestamp
)
object FollowMaps extends Table[FollowMap]("follow_map")
{
  def user       = column[Int]("user")
  def target     = column[Int]("target")
  def created_at = column[Timestamp]("created_at", O.DBType("datetime"))

  def * = user ~ target ~ created_at <> (FollowMap, FollowMap.unapply _)
  def pk = primaryKey("follow_map_pk", (user, target))
}

trait SlickSupport extends ScalatraServlet
{
  val logger = LoggerFactory.getLogger(getClass)

  val cpds = {
    val props = new Properties
    props.load(getClass.getResourceAsStream("/c3p0.properties"))
    val cpds = new ComboPooledDataSource
    cpds.setProperties(props)
    logger.info("Created c3p0 connection pool")
    cpds
  }

  def closeDbConnection() {
    logger.info("Closing c3po connection pool")
    cpds.close
  }

  val env = {
    val isuconEnv = Option(System.getenv("ISUCON_ENV")).getOrElse("local")
    logger.info("ISUCON_ENV: " + isuconEnv)
    isuconEnv
  }

  val (dbConfig, dataDir) = {
    val dir       = new File("./src/main/resources").getAbsolutePath()
    val file      = dir + "/" + env + ".json"
    val source    = FileUtils.readFileToString(new File(file))
    val appConfig = JSON.parseFull(source).get.asInstanceOf[Map[String, Any]]
    (
      appConfig.get("database").get.asInstanceOf[Map[String, Any]],
      appConfig.apply("data_dir")
    )
  }

  val db = Database.forURL(
    "jdbc:mysql://" + dbConfig.apply("host") +
      ":" + dbConfig.apply("port").asInstanceOf[Double].toInt +
      "/" + dbConfig.apply("dbname"),
    dbConfig.apply("username").asInstanceOf[String],
    dbConfig.apply("password").asInstanceOf[String]
  )

  override def destroy() {
    super.destroy()
    closeDbConnection
  }
}

class Isucon extends ScalatraServlet with SlickSupport with JacksonJsonSupport
{
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/db/create-tables") {
    db withSession {
      (Users.ddl ++ Entries.ddl ++ FollowMaps.ddl).create
    }
  }

  get("/") {
    contentType = "text/html"
  }
}

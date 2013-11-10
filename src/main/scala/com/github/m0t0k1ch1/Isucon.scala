package com.github.m0t0k1ch1

import org.scalatra._

import java.util.Properties
import org.slf4j.LoggerFactory

import com.mchange.v2.c3p0.ComboPooledDataSource
import scala.slick.driver.MySQLDriver.simple._
import Database.threadLocalSession

import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._

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
  created_at:    java.sql.Timestamp
)
object Entries extends Table[Entry]("entries")
{
  def id            = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def user          = column[Int]("user")
  def image         = column[String]("image")
  def publish_level = column[Int]("publish_level", O.Default(0))
  def created_at    = column[java.sql.Timestamp]("created_at", O.DBType("datetime"))

  def * = id ~ user ~ image ~ publish_level ~ created_at <> (Entry, Entry.unapply _)
  def idx = index("entries_user", user)
}

case class FollowMap(
  user:       Int,
  target:     Int,
  created_at: java.sql.Timestamp
)
object FollowMaps extends Table[FollowMap]("follow_map")
{
  def user       = column[Int]("user")
  def target     = column[Int]("target")
  def created_at = column[java.sql.Timestamp]("created_at", O.DBType("datetime"))

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

  val db = Database.forDataSource(cpds)

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
  }
}

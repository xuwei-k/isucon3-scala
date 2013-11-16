package com.github.m0t0k1ch1.isucon.schema

import com.github.m0t0k1ch1.isucon.model.User
import scala.slick.driver.MySQLDriver.simple._

object Users extends Table[User]("users")
{
  def id      = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name    = column[String]("name")
  def api_key = column[String]("api_key")
  def icon    = column[String]("icon", O.Default("default"))

  def * = id.? ~ name ~ api_key ~ icon <> (User, User.unapply _)
  def autoInc = name ~ api_key ~ icon returning id
  def idx = index("users_api_key", api_key, unique = true)
}

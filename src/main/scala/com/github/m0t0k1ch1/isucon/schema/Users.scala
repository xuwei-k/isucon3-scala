package com.github.m0t0k1ch1.isucon.schema

import com.github.m0t0k1ch1.isucon.model.User
import scala.slick.driver.MySQLDriver.simple._

object Users extends Table[User]("users")
{
  def id     = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name   = column[String]("name")
  def apiKey = column[String]("api_key")
  def icon   = column[String]("icon", O.Default("default"))

  def * = id ~ name ~ apiKey ~ icon <> (User, User.unapply _)
  def autoInc = name ~ apiKey ~ icon returning id
  def idx = index("users_api_key", apiKey, unique = true)
}

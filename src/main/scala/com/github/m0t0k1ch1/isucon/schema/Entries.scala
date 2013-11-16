package com.github.m0t0k1ch1.isucon.schema

import com.github.m0t0k1ch1.isucon.model.Entry
import scala.slick.driver.MySQLDriver.simple._
import java.sql.Timestamp

object Entries extends Table[Entry]("entries")
{
  def id            = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def user          = column[Int]("user")
  def image         = column[String]("image")
  def publish_level = column[Int]("publish_level", O.Default(0))
  def created_at    = column[Timestamp]("created_at", O.DBType("datetime"))

  def * = id.? ~ user ~ image ~ publish_level ~ created_at <> (Entry, Entry.unapply _)
  def autoInc = user ~ image ~ publish_level ~ created_at returning id
  def idx = index("entries_user", user)
}

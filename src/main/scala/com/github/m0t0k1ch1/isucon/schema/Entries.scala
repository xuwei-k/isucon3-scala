package com.github.m0t0k1ch1.isucon.schema

import com.github.m0t0k1ch1.isucon.model.Entry
import scala.slick.driver.MySQLDriver.simple._
import java.sql.Timestamp

object Entries extends Table[Entry]("entries")
{
  def id           = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def user         = column[Int]("user")
  def image        = column[String]("image")
  def publishLevel = column[Int]("publish_level", O.Default(0))
  def createdAt    = column[Timestamp]("created_at", O.DBType("datetime"))

  def * = id.? ~ user ~ image ~ publishLevel ~ createdAt <> (Entry, Entry.unapply _)
  def autoInc = user ~ image ~ publishLevel ~ createdAt returning id
  def idx = index("entries_user", user)
}

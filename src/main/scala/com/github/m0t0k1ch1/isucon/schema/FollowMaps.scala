package com.github.m0t0k1ch1.isucon.schema

import com.github.m0t0k1ch1.isucon.model.FollowMap
import scala.slick.driver.MySQLDriver.simple._
import java.sql.Timestamp

object FollowMaps extends Table[FollowMap]("follow_map")
{
  def user       = column[Int]("user")
  def target     = column[Int]("target")
  def created_at = column[Timestamp]("created_at", O.DBType("datetime"))

  def * = user ~ target ~ created_at <> (FollowMap, FollowMap.unapply _)
  def pk = primaryKey("follow_map_pk", (user, target))
}

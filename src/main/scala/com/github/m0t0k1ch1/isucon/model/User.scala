package com.github.m0t0k1ch1.isucon.model

case class User(
  id:      Option[Int],
  name:    String,
  api_key: String,
  icon:    String
)

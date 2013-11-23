package com.github.m0t0k1ch1.isucon.model

import java.sql.Timestamp

case class Entry(
  id:           Int,
  user:         Int,
  image:        String,
  publishLevel: Int,
  createdAt:    Timestamp
)

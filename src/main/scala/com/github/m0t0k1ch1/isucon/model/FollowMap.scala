package com.github.m0t0k1ch1.isucon.model

import java.sql.Timestamp

case class FollowMap(
  user:      Int,
  target:    Int,
  createdAt: Timestamp
)

package com.lavacrafter.maptimelinetool.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "point_tags",
    indices = [Index(value = ["tagId"])],
    primaryKeys = ["pointId", "tagId"]
)
data class PointTagCrossRef(
    val pointId: Long,
    val tagId: Long
)

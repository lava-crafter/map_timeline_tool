package com.lavacrafter.maptimelinetool.ui

/** Both countdown and submission use the same gate, including restored confirmation/camera state. */
fun canAdvanceAutoSave(dialogOpen: Boolean, eventTimeMs: Long?, paused: Boolean,
    writeState: PointWriteState, cameraPending: Boolean): Boolean =
    dialogOpen && eventTimeMs != null && !paused && writeState == PointWriteState.Idle && !cameraPending

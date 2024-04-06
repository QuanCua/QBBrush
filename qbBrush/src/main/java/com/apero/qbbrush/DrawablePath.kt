package com.apero.qbbrush

import android.graphics.Path

class DrawablePath(var path: Path, var isClearPath: Boolean, var size: Float) {
    companion object {
        fun newInstance(): DrawablePath {
            return DrawablePath(Path(), false, 1f)
        }
    }
}
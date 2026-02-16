package com.tribixbite.stoatally.internals.extensions

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.supportSwipeReply(
    // Use Final pass so children (e.g. horizontalScroll in code blocks)
    // process events first and consume horizontal drags (upstream #14).
    pass: PointerEventPass = PointerEventPass.Final,
    onDown: (pointer: PointerInputChange) -> Unit,
    onMove: (changes: List<PointerInputChange>) -> Unit,
    onUp: () -> Unit,
) = this.then(
    Modifier.pointerInput(pass) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = pass, requireUnconsumed = false)
            onDown(down)
            do {
                val event: PointerEvent = awaitPointerEvent(
                    pass = pass
                )

                // Skip changes already consumed by child scrollables (e.g. code blocks)
                val unconsumed = event.changes.filter { !it.isConsumed }
                if (unconsumed.isNotEmpty()) {
                    onMove(unconsumed)
                }

            } while (event.changes.any { it.pressed })
            onUp()
        }
    }
)
package com.jerry.mimochat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun AnimatedPixelAvatar(
    character: CharacterCard,
    motion: AvatarMotion,
    modifier: Modifier = Modifier
) {
    var frame by remember(motion) { mutableIntStateOf(0) }
    LaunchedEffect(motion) {
        while (true) {
            delay(if (motion == AvatarMotion.TALK) 145 else 190)
            frame = (frame + 1) % 120
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(character.primaryColour).copy(alpha = .12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPixelCharacter(character, motion, frame)
        }
    }
}

private fun DrawScope.drawPixelCharacter(character: CharacterCard, motion: AvatarMotion, frame: Int) {
    val cell = min(size.width / 16f, size.height / 20f)
    val originX = (size.width - cell * 16f) / 2f
    val originY = (size.height - cell * 20f) / 2f
    val outline = Color(0xFF17151F)
    val outfit = Color(character.primaryColour)
    val skin = Color(character.skinColour)
    val accent = Color(character.accentColour)

    fun px(x: Int, y: Int, colour: Color, w: Int = 1, h: Int = 1) {
        drawRect(
            color = colour,
            topLeft = Offset(originX + x * cell, originY + y * cell),
            size = Size(w * cell + .35f, h * cell + .35f)
        )
    }

    val phase = frame % 6
    val bob = when (motion) {
        AvatarMotion.IDLE, AvatarMotion.HAPPY, AvatarMotion.SLEEPY -> if (phase < 3) 0 else 1
        AvatarMotion.TALK -> if (phase % 2 == 0) 0 else 1
        else -> 0
    }
    val jump = if (motion == AvatarMotion.JUMP) intArrayOf(0, -2, -4, -4, -2, 0)[phase] else 0
    val yShift = bob + jump
    val walkPhase = motion == AvatarMotion.WALK && phase >= 3

    // Soft pixel shadow.
    if (motion != AvatarMotion.JUMP || phase == 0 || phase == 5) {
        px(5, 18, outline.copy(alpha = .16f), 6, 1)
    } else {
        px(6, 18, outline.copy(alpha = .10f), 4, 1)
    }

    // Legs: alternating two-frame walk, or tucked during jump.
    val legY = 14 + yShift
    when {
        motion == AvatarMotion.JUMP && phase in 1..4 -> {
            px(5, legY, outline, 3, 3); px(6, legY, outfit, 1, 2)
            px(9, legY, outline, 3, 3); px(10, legY, outfit, 1, 2)
        }
        walkPhase -> {
            px(5, legY, outline, 3, 4); px(6, legY, outfit, 1, 3)
            px(9, legY, outline, 2, 3); px(10, legY, outfit, 1, 2)
            px(10, legY + 2, outline, 3, 2); px(11, legY + 2, outfit)
        }
        else -> {
            px(5, legY, outline, 3, 4); px(6, legY, outfit, 1, 3)
            px(9, legY, outline, 3, 4); px(10, legY, outfit, 1, 3)
        }
    }

    // Torso.
    px(4, 9 + yShift, outline, 8, 6)
    px(5, 10 + yShift, outfit, 6, 4)
    px(7, 10 + yShift, accent, 2, 1)

    // Arms.
    if (motion == AvatarMotion.WAVE) {
        px(2, 10 + yShift, outline, 3, 4); px(3, 11 + yShift, outfit, 1, 2)
        val waveY = if (phase % 2 == 0) 5 else 6
        px(11, waveY + yShift, outline, 3, 6)
        px(12, waveY + 1 + yShift, skin, 1, 2)
        px(13, waveY - 1 + yShift, skin, 1, 2)
    } else if (motion == AvatarMotion.WALK) {
        val leftY = if (walkPhase) 11 else 9
        val rightY = if (walkPhase) 9 else 11
        px(2, leftY + yShift, outline, 3, 5); px(3, leftY + 1 + yShift, outfit, 1, 3)
        px(11, rightY + yShift, outline, 3, 5); px(12, rightY + 1 + yShift, outfit, 1, 3)
    } else if (motion == AvatarMotion.HAPPY) {
        px(2, 7 + yShift, outline, 3, 5); px(3, 8 + yShift, outfit, 1, 3)
        px(11, 7 + yShift, outline, 3, 5); px(12, 8 + yShift, outfit, 1, 3)
    } else {
        px(2, 10 + yShift, outline, 3, 5); px(3, 11 + yShift, outfit, 1, 3)
        px(11, 10 + yShift, outline, 3, 5); px(12, 11 + yShift, outfit, 1, 3)
    }

    // Head: 8x7 with clipped pixel corners.
    px(5, 2 + yShift, outline, 6, 1)
    px(4, 3 + yShift, outline, 8, 5)
    px(5, 8 + yShift, outline, 6, 1)
    px(5, 3 + yShift, skin, 6, 5)
    px(4, 4 + yShift, skin, 1, 3)
    px(11, 4 + yShift, skin, 1, 3)

    // Accessory layer.
    when (character.accessory) {
        AvatarAccessory.NONE -> Unit
        AvatarAccessory.CAP -> {
            px(5, 1 + yShift, accent, 6, 1); px(4, 2 + yShift, accent, 8, 1); px(11, 3 + yShift, accent, 2, 1)
        }
        AvatarAccessory.CAT_EARS -> {
            px(5, 0 + yShift, outline, 2, 3); px(6, 1 + yShift, accent)
            px(9, 0 + yShift, outline, 2, 3); px(9, 1 + yShift, accent)
        }
        AvatarAccessory.ANTENNA -> {
            px(8, 0 + yShift, outline, 1, 2); px(7, 0 + yShift, accent, 3, 1)
        }
        AvatarAccessory.GLASSES -> {
            px(5, 5 + yShift, outline, 3, 2); px(9, 5 + yShift, outline, 3, 2); px(8, 5 + yShift, outline)
            px(6, 5 + yShift, skin); px(10, 5 + yShift, skin)
        }
    }

    // Face states. A periodic closed-eye frame gives the idle animation a blink.
    val blink = motion == AvatarMotion.IDLE && frame % 24 == 0
    when {
        motion == AvatarMotion.SLEEPY || blink -> {
            px(6, 5 + yShift, outline, 2, 1); px(9, 5 + yShift, outline, 2, 1)
            px(7, 7 + yShift, outline, 2, 1)
        }
        motion == AvatarMotion.HAPPY -> {
            px(6, 5 + yShift, outline); px(10, 5 + yShift, outline)
            px(7, 7 + yShift, outline, 3, 1); px(6, 6 + yShift, outline); px(10, 6 + yShift, outline)
        }
        motion == AvatarMotion.SAD -> {
            px(6, 5 + yShift, outline); px(10, 5 + yShift, outline)
            px(6, 4 + yShift, outline); px(10, 4 + yShift, outline)
            px(7, 7 + yShift, outline); px(8, 6 + yShift, outline, 2, 1); px(10, 7 + yShift, outline)
        }
        motion == AvatarMotion.CONFUSED -> {
            px(6, 5 + yShift, outline); px(10, 6 + yShift, outline)
            px(6, 4 + yShift, outline, 2, 1); px(9, 4 + yShift, outline, 2, 1)
            px(8, 7 + yShift, outline, 2, 1)
        }
        motion == AvatarMotion.TALK && phase % 2 == 0 -> {
            px(6, 5 + yShift, outline); px(10, 5 + yShift, outline)
            px(8, 7 + yShift, outline, 2, 1)
        }
        else -> {
            px(6, 5 + yShift, outline); px(10, 5 + yShift, outline)
            px(8, 7 + yShift, outline)
        }
    }
}

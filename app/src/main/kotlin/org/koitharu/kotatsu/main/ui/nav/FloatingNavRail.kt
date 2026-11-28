package org.koitharu.kotatsu.main.ui.nav

import android.graphics.PorterDuff
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.koitharu.kotatsu.R

private val RailSmoothLiquidEasing = CubicBezierEasing(0.32f, 0f, 0.14f, 1f)
private val RailShape = RoundedCornerShape(50)

/**
 * Vertical floating pill navigation rail — the side-mounted twin of [FloatingNavBar].
 *
 * Matches the phone floating bar's exact materials, proportions, and fluid pill physics:
 * - 64dp-wide capsule container (`RoundedCornerShape(50)`) with `colorSurfaceContainer` fill and 1dp rim
 * - 48dp icon-only slots (no cluttered stacked labels; long-press tooltips show names)
 * - 48dp circular/capsule active pill with droplet stretch during tab transitions
 * - Standalone 56dp squircle Continue Reading FAB spaced 8dp below the pill
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingNavRail(
	items: List<FloatingNavBarItem>,
	selectedId: Int,
	colors: FloatingNavBarColors,
	onItemSelected: (Int) -> Unit,
	onItemReselected: (Int) -> Unit,
	modifier: Modifier = Modifier,
	showContinue: Boolean = false,
	onContinueClick: () -> Unit = {},
	onContinueLongClick: () -> Unit = {},
) {
	if (items.isEmpty()) return

	val barColor = Color(colors.container)
	val pillColor by animateColorAsState(
		targetValue = Color(colors.selectedContainer),
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
		label = "railPillContainer",
	)
	val selectedContentColor by animateColorAsState(
		targetValue = Color(colors.selectedContent),
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
		label = "railSelectedContent",
	)
	val unselectedContentColor by animateColorAsState(
		targetValue = Color(colors.unselectedContent),
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
		label = "railUnselectedContent",
	)

	val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
	var flightStartSlot by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
	var flightTargetSlot by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
	val flightProgress = remember { Animatable(1f) }
	var hasInitialized by remember { mutableStateOf(false) }

	LaunchedEffect(selectedIndex, items.size) {
		val target = selectedIndex.toFloat()
		if (!hasInitialized) {
			hasInitialized = true
			flightStartSlot = target
			flightTargetSlot = target
			flightProgress.snapTo(1f)
			return@LaunchedEffect
		}
		val current = lerp(flightStartSlot, flightTargetSlot, flightProgress.value)
		if (abs(current - target) > 0.001f) {
			val dist = abs(target - current)
			val durationMs = (350 + (dist - 1f).coerceAtLeast(0f) * 16f).roundToInt().coerceIn(350, 400)
			flightStartSlot = current
			flightTargetSlot = target
			flightProgress.snapTo(0f)
			flightProgress.animateTo(
				targetValue = 1f,
				animationSpec = tween(durationMillis = durationMs, easing = RailSmoothLiquidEasing),
			)
			flightStartSlot = target
		}
	}

	var columnCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
	val itemCoords = remember(items.size) { arrayOfNulls<LayoutCoordinates>(items.size) }
	var layoutTick by remember { mutableIntStateOf(0) }

	Column(
		modifier = modifier.wrapContentHeight(),
		verticalArrangement = Arrangement.spacedBy(8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Surface(
			shape = RailShape,
			color = Color.Transparent,
			modifier = Modifier.wrapContentHeight(),
		) {
			Box(modifier = Modifier.wrapContentHeight()) {
				Spacer(
					modifier = Modifier
						.matchParentSize()
						.background(color = barColor, shape = RailShape),
				)
				Column(
					modifier = Modifier
						.widthIn(min = 64.dp)
						.onPlaced { coords ->
							if (columnCoords !== coords) {
								columnCoords = coords
								layoutTick++
							}
						}
						.drawBehind {
							layoutTick
							val cc = columnCoords
							if (cc == null || !cc.isAttached || items.isEmpty()) return@drawBehind

							val p = flightProgress.value.coerceIn(0f, 1f)
							val liveSlot = lerp(flightStartSlot, flightTargetSlot, p)
							val lastIdx = items.lastIndex
							val clamped = liveSlot.coerceIn(0f, lastIdx.toFloat())
							val i0 = clamped.toInt().coerceIn(0, lastIdx)
							val i1 = (i0 + 1).coerceAtMost(lastIdx)
							val frac = (clamped - i0).coerceIn(0f, 1f)

							val c0 = itemCoords.getOrNull(i0)?.takeIf { it.isAttached } ?: return@drawBehind
							val c1 = itemCoords.getOrNull(i1)?.takeIf { it.isAttached } ?: c0
							val p0 = cc.localPositionOf(c0, Offset.Zero)
							val p1 = cc.localPositionOf(c1, Offset.Zero)

							val baseLeft = lerp(p0.x, p1.x, frac)
							val baseWidth = lerp(c0.size.width.toFloat(), c1.size.width.toFloat(), frac)
							val baseHeight = lerp(c0.size.height.toFloat(), c1.size.height.toFloat(), frac)
							val baseCenterY = lerp(
								p0.y + c0.size.height * 0.5f,
								p1.y + c1.size.height * 0.5f,
								frac,
							)

							val isFlying = p < 0.999f
							val stretchY: Float
							val squishX: Float
							if (isFlying) {
								val dist = abs(flightTargetSlot - flightStartSlot)
								val mid = sin(p * PI.toFloat())
								val extraTiles = (dist - 1f).coerceAtLeast(0f)
								stretchY = mid * (6f + extraTiles * 6f).coerceAtMost(24f).dp.toPx()
								squishX = mid * (1.5f + extraTiles * 0.7f).coerceAtMost(3.5f).dp.toPx()
							} else {
								stretchY = 0f
								squishX = 0f
							}

							val pillWidth = (baseWidth - squishX).coerceAtLeast(1f)
							val pillHeight = (baseHeight + stretchY).coerceAtLeast(1f)
							val pillLeft = baseLeft + squishX * 0.5f
							val pillTop = (baseCenterY - pillHeight * 0.5f).coerceIn(
								3.dp.toPx(),
								(size.height - pillHeight - 3.dp.toPx()).coerceAtLeast(3.dp.toPx()),
							)
							val radius = min(pillWidth, pillHeight) * 0.5f

							drawRoundRect(
								color = pillColor,
								topLeft = Offset(pillLeft, pillTop),
								size = Size(pillWidth, pillHeight),
								cornerRadius = CornerRadius(radius, radius),
							)
						}
						.padding(horizontal = 8.dp, vertical = 8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					items.forEachIndexed { index, item ->
						key(item.id) {
							val isSelected = item.id == selectedId
							val title = stringResource(item.titleRes)
							val tooltipState = rememberTooltipState()
							val contentColor by animateColorAsState(
								targetValue = if (isSelected) selectedContentColor else unselectedContentColor,
								animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
								label = "railItemTint",
							)

							TooltipBox(
								positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
									spacingBetweenTooltipAndAnchor = 12.dp,
								),
								tooltip = {
									PlainTooltip(containerColor = MaterialTheme.colorScheme.inverseSurface) {
										Text(text = title, color = MaterialTheme.colorScheme.inverseOnSurface)
									}
								},
								state = tooltipState,
								enableUserInput = true,
							) {
								Box(
									modifier = Modifier
										.size(48.dp)
										.onPlaced { coords ->
											if (index < itemCoords.size && itemCoords[index] !== coords) {
												itemCoords[index] = coords
												layoutTick++
											}
										}
										.clickable(
											interactionSource = remember { MutableInteractionSource() },
											indication = null,
											onClick = {
												if (isSelected) onItemReselected(item.id)
												else onItemSelected(item.id)
											},
										)
										.semantics {
											this.selected = isSelected
											role = Role.Tab
											contentDescription = title
										},
									contentAlignment = Alignment.Center,
								) {
									BadgedBox(
										badge = {
											if (item.badgeCount > 0) {
												Badge { Text(text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString()) }
											} else if (item.badgeCount < 0) {
												Badge()
											}
										},
									) {
										RailNavIcon(
											resId = item.icon,
											selected = isSelected,
											tint = { contentColor },
											modifier = Modifier.size(24.dp),
										)
									}
								}
							}
						}
					}
				}
			}
		}

		AnimatedVisibility(
			visible = showContinue,
			enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
				expandVertically(expandFrom = Alignment.Top),
			exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
				shrinkVertically(shrinkTowards = Alignment.Top),
		) {
			RailContinueButton(
				colors = colors,
				onClick = onContinueClick,
				onLongClick = onContinueLongClick,
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RailContinueButton(
	colors: FloatingNavBarColors,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
) {
	val container by animateColorAsState(
		targetValue = Color(colors.selectedContainer),
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
		label = "continueRailContainer",
	)
	val content by animateColorAsState(
		targetValue = Color(colors.selectedContent),
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
		label = "continueRailContent",
	)
	val label = stringResource(R.string.continue_reading)
	val tooltipState = rememberTooltipState()

	TooltipBox(
		positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
			spacingBetweenTooltipAndAnchor = 12.dp,
		),
		tooltip = { PlainTooltip { Text(label) } },
		state = tooltipState,
	) {
		Surface(
			shape = RoundedCornerShape(dimensionResource(R.dimen.corner_large)),
			color = container,
			contentColor = content,
			modifier = Modifier
				.requiredSize(56.dp)
				.semantics { contentDescription = label },
		) {
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier.combinedClickable(
					onClick = onClick,
					onLongClick = onLongClick,
				),
			) {
				Icon(
					painter = painterResource(R.drawable.ic_read),
					contentDescription = null,
					tint = content,
					modifier = Modifier.size(24.dp),
				)
			}
		}
	}
}

private val RAIL_STATE_CHECKED = intArrayOf(android.R.attr.state_checked)
private val RAIL_STATE_UNCHECKED = intArrayOf(-android.R.attr.state_checked)

@Composable
private fun RailNavIcon(
	@DrawableRes resId: Int,
	selected: Boolean,
	tint: () -> Color,
	modifier: Modifier = Modifier,
) {
	val lastTint = remember { intArrayOf(0) }
	AndroidView(
		modifier = modifier,
		factory = { ctx ->
			ImageView(ctx).apply {
				scaleType = ImageView.ScaleType.FIT_CENTER
				setImageResource(resId)
				setImageState(IntArray(0), false)
				jumpDrawablesToCurrentState()
			}
		},
		update = { iv ->
			val targetResId = when {
				resId == R.drawable.ic_explore_selector && selected -> R.drawable.ic_explore_checked
				resId == R.drawable.ic_explore_selector -> R.drawable.ic_explore_normal
				else -> resId
			}
			val resourceChanged = iv.tag != targetResId
			if (resourceChanged) {
				iv.setImageResource(targetResId)
				iv.tag = targetResId
			}
			val targetState = if (selected) RAIL_STATE_CHECKED else RAIL_STATE_UNCHECKED
			if (resourceChanged || iv.isSelected != selected) {
				iv.isSelected = selected
				iv.isActivated = selected
				iv.setImageState(targetState, false)
			}
			val tintColor = tint().toArgb()
			if (resourceChanged || lastTint[0] != tintColor || iv.colorFilter == null) {
				lastTint[0] = tintColor
				iv.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
			}
		},
	)
}

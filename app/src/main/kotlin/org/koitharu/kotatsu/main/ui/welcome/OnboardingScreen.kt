package org.koitharu.kotatsu.main.ui.welcome

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.filter.ui.model.FilterProperty
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.settings.compose.ColorSchemePickerRow
import java.util.Locale

private const val PAGE_COUNT = 4
private const val SYNC_PAGE = 2
private val CARD_SHAPE = RoundedCornerShape(24.dp)

data class OnboardingPermissions(
	val hasInstall: Boolean,
	val hasNotifications: Boolean,
	val hasBattery: Boolean,
)

data class OnboardingActions(
	val onThemeChange: (Int) -> Unit,
	val onColorSchemeChange: (String) -> Unit,
	val onAmoledChange: (Boolean) -> Unit,
	val onSelectDestination: () -> Unit,
	val onPermissionInstall: () -> Unit,
	val onPermissionNotifications: () -> Unit,
	val onPermissionBattery: () -> Unit,
	val onSignInGoogle: () -> Unit,
	val onRestoreKuroha: () -> Unit,
	val onRestoreMihon: () -> Unit,
	val onLocaleToggle: (Locale, Boolean) -> Unit,
	val onTypeToggle: (ContentType, Boolean) -> Unit,
	val onSkip: () -> Unit,
	val onFinish: () -> Unit,
)

@Composable
fun OnboardingScreen(
	selectedTheme: Int,
	selectedColorScheme: ColorScheme,
	isAmoledEnabled: Boolean,
	storageSummary: String?,
	signedInEmail: String?,
	isLoading: Boolean,
	permissions: OnboardingPermissions,
	syncStepDone: Int,
	locales: FilterProperty<Locale>,
	types: FilterProperty<ContentType>,
	actions: OnboardingActions,
) {
	val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
	val scope = rememberCoroutineScope()

	LaunchedEffect(syncStepDone) {
		if (syncStepDone > 0 && pagerState.currentPage == SYNC_PAGE) {
			pagerState.animateScrollToPage(SYNC_PAGE + 1)
		}
	}

	val isLastPage by remember { derivedStateOf { pagerState.currentPage == PAGE_COUNT - 1 } }
	val backEnabled by remember { derivedStateOf { pagerState.currentPage > 0 } }

	var backProgress by remember { mutableFloatStateOf(0f) }
	var isBackFromLeftEdge by remember { mutableStateOf(true) }
	val backPreview by animateFloatAsState(
		targetValue = backProgress,
		animationSpec = spring(stiffness = Spring.StiffnessHigh),
		label = "backPreview",
	)

	PredictiveBackHandler(enabled = backEnabled) { events ->
		try {
			events.collect { event ->
				isBackFromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
				backProgress = event.progress
			}
			pagerState.animateScrollToPage(pagerState.currentPage - 1)
		} finally {
			backProgress = 0f
		}
	}

	Surface(
		modifier = Modifier.fillMaxSize(),
		color = MaterialTheme.colorScheme.surface,
	) {
		val backModifier = if (backPreview > 0f) {
			Modifier.graphicsLayer {
				val p = backPreview
				scaleX = 1f - 0.08f * p
				scaleY = scaleX
				translationX = (if (isBackFromLeftEdge) 1f else -1f) * 8.dp.toPx() * p
				transformOrigin = TransformOrigin(if (isBackFromLeftEdge) 1f else 0f, 0.5f)
				shape = RoundedCornerShape(28.dp * p)
				clip = true
			}
		} else {
			Modifier
		}
		Box(
			modifier = Modifier
				.fillMaxSize()
				.then(backModifier),
		) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.windowInsetsPadding(WindowInsets.statusBars)
					.windowInsetsPadding(WindowInsets.navigationBars),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 20.dp, vertical = 12.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.secondaryContainer,
					) {
						Text(
							text = stringResource(R.string.app_name),
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSecondaryContainer,
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
						)
					}
					TextButton(onClick = actions.onSkip) {
						Text(
							text = stringResource(R.string.onboarding_skip),
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold,
						)
					}
				}

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(4.dp),
				) {
					if (isLoading) {
						LinearProgressIndicator(modifier = Modifier.fillMaxSize())
					}
				}

				HorizontalPager(
					state = pagerState,
					beyondViewportPageCount = PAGE_COUNT - 1,
					key = { it },
					modifier = Modifier
						.weight(1f)
						.fillMaxWidth(),
				) { pageIndex ->
					val heroIconRes = when (pageIndex) {
						0 -> R.drawable.ic_explore_checked
						1 -> R.drawable.ic_folder_file
						2 -> R.drawable.ic_cloud_sync
						else -> R.drawable.ic_language
					}
					val titleRes = when (pageIndex) {
						0 -> R.string.onboarding_welcome_title
						1 -> R.string.onboarding_storage_title
						2 -> R.string.onboarding_login_title
						else -> R.string.onboarding_sources_title
					}
					val subtitleRes = when (pageIndex) {
						0 -> R.string.onboarding_welcome_subtitle
						1 -> R.string.onboarding_storage_subtitle
						2 -> R.string.onboarding_login_subtitle
						else -> R.string.onboarding_sources_subtitle
					}

					Column(
						modifier = Modifier
							.fillMaxSize()
							.verticalScroll(rememberScrollState())
							.padding(horizontal = 20.dp, vertical = 12.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						Surface(
							shape = RoundedCornerShape(30.dp),
							color = MaterialTheme.colorScheme.primaryContainer,
							modifier = Modifier.size(84.dp),
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									painter = painterResource(heroIconRes),
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onPrimaryContainer,
									modifier = Modifier.size(40.dp),
								)
							}
						}
						Spacer(Modifier.height(16.dp))
						Text(
							text = stringResource(titleRes),
							style = MaterialTheme.typography.headlineMedium,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.Center,
						)
						Spacer(Modifier.height(6.dp))
						Text(
							text = stringResource(subtitleRes),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
						)
						Spacer(Modifier.height(20.dp))

						when (pageIndex) {
							0 -> AppearanceSlide(
								selectedTheme = selectedTheme,
								selectedColorScheme = selectedColorScheme,
								isAmoledEnabled = isAmoledEnabled,
								actions = actions,
							)
							1 -> StoragePermissionsSlide(
								storageSummary = storageSummary,
								permissions = permissions,
								actions = actions,
							)
							2 -> LoginSyncSlide(
								signedInEmail = signedInEmail,
								actions = actions,
							)
							else -> SourcesSlide(
								locales = locales,
								types = types,
								actions = actions,
							)
						}
					}
				}

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 20.dp, vertical = 16.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (backEnabled) {
						OutlinedButton(
							onClick = {
								scope.launch {
									pagerState.animateScrollToPage(pagerState.currentPage - 1)
								}
							},
						) {
							Icon(
								painter = painterResource(R.drawable.ic_arrow_back),
								contentDescription = null,
								modifier = Modifier.size(18.dp),
							)
						}
					} else {
						Spacer(Modifier.width(56.dp))
					}

					Row(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						repeat(PAGE_COUNT) { index ->
							val selected = pagerState.currentPage == index
							val width by animateDpAsState(
								targetValue = if (selected) 26.dp else 8.dp,
								animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
								label = "dotWidth",
							)
							val color by animateColorAsState(
								targetValue = if (selected) {
									MaterialTheme.colorScheme.primary
								} else {
									MaterialTheme.colorScheme.surfaceVariant
								},
								label = "dotColor",
							)
							Box(
								modifier = Modifier
									.height(8.dp)
									.width(width)
									.clip(CircleShape)
									.background(color),
							)
						}
					}

					Button(
						onClick = {
							if (isLastPage) {
								actions.onFinish()
							} else {
								scope.launch {
									pagerState.animateScrollToPage(pagerState.currentPage + 1)
								}
							}
						},
					) {
						Text(
							text = stringResource(
								if (isLastPage) R.string.onboarding_start_reading else R.string.onboarding_next,
							),
						)
						if (!isLastPage) {
							Spacer(Modifier.width(6.dp))
							Icon(
								painter = painterResource(R.drawable.ic_arrow_forward),
								contentDescription = null,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun AppearanceSlide(
	selectedTheme: Int,
	selectedColorScheme: ColorScheme,
	isAmoledEnabled: Boolean,
	actions: OnboardingActions,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Surface(
			shape = CARD_SHAPE,
			color = MaterialTheme.colorScheme.surfaceContainerLow,
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					text = stringResource(R.string.theme),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
				)
				val options = listOf(
					AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.onboarding_theme_system,
					AppCompatDelegate.MODE_NIGHT_NO to R.string.onboarding_theme_light,
					AppCompatDelegate.MODE_NIGHT_YES to R.string.onboarding_theme_dark,
				)
				SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
					options.forEachIndexed { index, (mode, labelRes) ->
						SegmentedButton(
							selected = selectedTheme == mode,
							onClick = { actions.onThemeChange(mode) },
							shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
						) {
							Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
						}
					}
				}
			}
		}

		ColorSchemePickerRow(
			title = stringResource(R.string.color_scheme),
			selectedValue = selectedColorScheme.name,
			onValueChange = actions.onColorSchemeChange,
			shape = CARD_SHAPE,
		)

		Surface(
			shape = CARD_SHAPE,
			color = MaterialTheme.colorScheme.surfaceContainerLow,
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 14.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = stringResource(R.string.amoled_theme),
					style = MaterialTheme.typography.titleMedium,
				)
				Switch(
					checked = isAmoledEnabled,
					onCheckedChange = actions.onAmoledChange,
				)
			}
		}
	}
}

@Composable
private fun StoragePermissionsSlide(
	storageSummary: String?,
	permissions: OnboardingPermissions,
	actions: OnboardingActions,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Surface(
			shape = CARD_SHAPE,
			color = MaterialTheme.colorScheme.surfaceContainerLow,
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				modifier = Modifier.padding(16.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Icon(
					painter = painterResource(R.drawable.ic_folder_file),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
				)
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.manga_save_location),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold,
					)
					Text(
						text = storageSummary ?: stringResource(R.string.system_default),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}
				FilledTonalButton(onClick = actions.onSelectDestination) {
					Text(stringResource(R.string.onboarding_change_folder))
				}
			}
		}

		PermissionCard(
			iconRes = R.drawable.ic_notification,
			title = stringResource(R.string.onboarding_perm_notifications),
			granted = permissions.hasNotifications,
			onClick = actions.onPermissionNotifications,
		)
		PermissionCard(
			iconRes = R.drawable.ic_extension_settings,
			title = stringResource(R.string.onboarding_perm_install),
			granted = permissions.hasInstall,
			onClick = actions.onPermissionInstall,
		)
		PermissionCard(
			iconRes = R.drawable.ic_battery_charging,
			title = stringResource(R.string.onboarding_perm_battery),
			granted = permissions.hasBattery,
			onClick = actions.onPermissionBattery,
		)
	}
}

@Composable
private fun PermissionCard(
	@DrawableRes iconRes: Int,
	title: String,
	granted: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		shape = CARD_SHAPE,
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Icon(
				painter = painterResource(iconRes),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
			)
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				modifier = Modifier.weight(1f),
			)
			if (granted) {
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.secondaryContainer,
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(
							painter = painterResource(R.drawable.ic_check),
							contentDescription = null,
							modifier = Modifier.size(16.dp),
							tint = MaterialTheme.colorScheme.onSecondaryContainer,
						)
						Text(
							text = stringResource(R.string.onboarding_perm_granted),
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSecondaryContainer,
						)
					}
				}
			} else {
				FilledTonalButton(onClick = onClick) {
					Text(stringResource(R.string.onboarding_perm_grant))
				}
			}
		}
	}
}

@Composable
private fun LoginSyncSlide(
	signedInEmail: String?,
	actions: OnboardingActions,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Surface(
			shape = CARD_SHAPE,
			color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(
				modifier = Modifier.padding(18.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Icon(
						painter = painterResource(R.drawable.ic_cloud_sync),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
					)
					Text(
						text = stringResource(R.string.sync),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
					)
				}
				if (!signedInEmail.isNullOrBlank()) {
					Text(
						text = stringResource(R.string.onboarding_google_connected, signedInEmail),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurface,
					)
				} else {
					Button(
						onClick = actions.onSignInGoogle,
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(stringResource(R.string.onboarding_google_sign_in))
					}
				}
			}
		}

		Surface(
			shape = CARD_SHAPE,
			color = MaterialTheme.colorScheme.surfaceContainerLow,
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(
				modifier = Modifier.padding(18.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Icon(
						painter = painterResource(R.drawable.ic_backup_restore),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
					)
					Text(
						text = stringResource(R.string.backup_restore),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold,
					)
				}
				FilledTonalButton(
					onClick = actions.onRestoreKuroha,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(stringResource(R.string.onboarding_restore_kuroha))
				}
				OutlinedButton(
					onClick = actions.onRestoreMihon,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(stringResource(R.string.onboarding_restore_mihon))
				}
			}
		}

		HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
		Text(
			text = stringResource(R.string.onboarding_continue_guest),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth(),
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesSlide(
	locales: FilterProperty<Locale>,
	types: FilterProperty<ContentType>,
	actions: OnboardingActions,
) {
	val context = LocalContext.current
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Surface(
			shape = CARD_SHAPE,
			color = MaterialTheme.colorScheme.surfaceContainerLow,
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(
					text = stringResource(R.string.content_types),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					types.availableItems.forEach { type ->
						val checked = type in types.selectedItems
						FilterChip(
							selected = checked,
							onClick = { actions.onTypeToggle(type, !checked) },
							label = { Text(stringResource(type.titleResId)) },
						)
					}
				}
			}
		}

		Surface(
			shape = CARD_SHAPE,
			color = MaterialTheme.colorScheme.surfaceContainerLow,
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(
					text = stringResource(R.string.languages),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					var showAllLocales by remember { mutableStateOf(false) }
					val displayedLocales = remember(locales.availableItems, locales.selectedItems, showAllLocales) {
						if (showAllLocales || locales.availableItems.size <= 14) {
							locales.availableItems
						} else {
							val prioritized = locales.availableItems.filter { it in locales.selectedItems }
							(prioritized + locales.availableItems).distinct().take(14)
						}
					}
					displayedLocales.forEach { locale ->
						val checked = locale in locales.selectedItems
						FilterChip(
							selected = checked,
							onClick = { actions.onLocaleToggle(locale, !checked) },
							label = { Text(locale.getDisplayName(context)) },
						)
					}
					if (!showAllLocales && locales.availableItems.size > 14) {
						FilterChip(
							selected = false,
							onClick = { showAllLocales = true },
							label = { Text("+${locales.availableItems.size - 14}") },
						)
					}
				}
			}
		}
	}
}

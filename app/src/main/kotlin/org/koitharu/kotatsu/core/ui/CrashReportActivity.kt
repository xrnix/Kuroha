package org.koitharu.kotatsu.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.acra.ReportField
import org.acra.dialog.CrashReportDialog
import org.acra.dialog.CrashReportDialogHelper
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.DialogCrashReportBinding

class CrashReportActivity : CrashReportDialog() {

	override fun buildAndShowDialog(savedInstanceState: Bundle?) {
		val binding = DialogCrashReportBinding.inflate(layoutInflater)
		val crashSummary = readCrashSummary()
		binding.textStacktrace.text = crashSummary
		binding.buttonToggleDetails.isVisible = crashSummary.isNotBlank()
		binding.buttonToggleDetails.setOnClickListener {
			val show = !binding.cardDetails.isVisible
			binding.cardDetails.isVisible = show
			binding.buttonToggleDetails.setText(
				if (show) R.string.hide_crash_details else R.string.show_crash_details,
			)
		}

		val alertDialog = MaterialAlertDialogBuilder(this)
			.setView(binding.root)
			.create()

		binding.buttonRestart.setOnClickListener {
			val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
			}
			complete(alertDialog, DialogInterface.BUTTON_NEGATIVE)
			if (launchIntent != null) {
				startActivity(launchIntent)
			}
		}
		binding.buttonCopy.setOnClickListener {
			val cm = getSystemService(ClipboardManager::class.java)
			cm?.setPrimaryClip(ClipData.newPlainText("Kuroha Crash Log", crashSummary))
			Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
		}
		binding.buttonClose.setOnClickListener {
			complete(alertDialog, DialogInterface.BUTTON_NEGATIVE)
		}
		alertDialog.setOnCancelListener { complete(alertDialog, DialogInterface.BUTTON_NEGATIVE) }
		alertDialog.setOnDismissListener { finish() }
		alertDialog.show()
	}

	private fun readCrashSummary(): String = runCatching {
		val data = CrashReportDialogHelper(this, intent).reportData
		val appVersion = data.getString(ReportField.APP_VERSION_NAME)
		val androidVersion = data.getString(ReportField.ANDROID_VERSION)
		val model = data.getString(ReportField.PHONE_MODEL)
		val stack = data.getString(ReportField.STACK_TRACE)
		buildString {
			if (!appVersion.isNullOrBlank()) appendLine("App: $appVersion")
			if (!androidVersion.isNullOrBlank() || !model.isNullOrBlank()) {
				appendLine("Device: ${listOfNotNull(model, androidVersion?.let { "Android $it" }).joinToString(" · ")}")
			}
			if (!stack.isNullOrBlank()) {
				if (isNotEmpty()) appendLine()
				append(stack.trim())
			}
		}.trim()
	}.getOrDefault("")

	private fun complete(alertDialog: AlertDialog, which: Int) {
		alertDialog.setOnDismissListener(null)
		alertDialog.dismiss()
		onClick(alertDialog, which)
	}
}

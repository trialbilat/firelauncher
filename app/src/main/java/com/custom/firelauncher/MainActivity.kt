package com.custom.firelauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

data class AppEntry(
    val label: String,
    val packageName: String,
    val launchIntent: Intent,
    val icon: Drawable
)

class MainActivity : Activity() {

    private lateinit var grid: GridView
    private lateinit var clock: TextView
    private val adapter = AppAdapter()
    private val ui = Handler(Looper.getMainLooper())
    private var lastSelected: View? = null

    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            ui.postDelayed(this, 15_000)
        }
    }

    // Refresh the grid when apps are installed, updated or removed.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clock = findViewById(R.id.clock)
        grid = findViewById(R.id.grid)
        grid.adapter = adapter

        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            launch(adapter.getItem(position))
        }

        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                lastSelected?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                lastSelected = view
                view?.animate()?.scaleX(1.06f)?.scaleY(1.06f)?.setDuration(120)?.start()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                lastSelected?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                lastSelected = null
            }
        }

        grid.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        loadApps()
        ui.post(clockTick)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
        ui.removeCallbacks(clockTick)
    }

    // Pressing Home while already on the launcher jumps back to the top.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            grid.setSelection(0)
            grid.smoothScrollToPosition(0)
        }
    }

    // A launcher should never exit on Back.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // intentionally empty
    }

    // Menu button (three lines on the Fire TV remote) opens App info for the highlighted app,
    // which is where you can uninstall or force stop it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val position = grid.selectedItemPosition
            if (position >= 0 && position < adapter.count) {
                openAppInfo(adapter.getItem(position))
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadApps() {
        Thread {
            val apps = queryApps()
            runOnUiThread { adapter.submit(apps) }
        }.start()
    }

    private fun queryApps(): List<AppEntry> {
        val pm = packageManager
        val seen = HashSet<String>()
        val result = ArrayList<AppEntry>()

        // Leanback (TV) apps first, then regular launcher apps that TV apps may be missing from.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue

                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, info.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

                result.add(
                    AppEntry(
                        label = info.loadLabel(pm).toString(),
                        packageName = pkg,
                        launchIntent = launch,
                        icon = info.loadIcon(pm)
                    )
                )
            }
        }

        result.sortBy { it.label.lowercase() }
        return result
    }

    private fun launch(app: AppEntry) {
        try {
            startActivity(app.launchIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(app: AppEntry) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "App info isn't available for ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        private var items: List<AppEntry> = emptyList()

        fun submit(newItems: List<AppEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): AppEntry = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            val app = items[position]
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.label).text = app.label
            view.scaleX = 1f
            view.scaleY = 1f
            return view
        }
    }
}

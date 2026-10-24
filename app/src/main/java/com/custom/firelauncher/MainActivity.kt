package com.custom.firelauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppEntry(
    val label: String,
    val packageName: String,
    val launchIntent: Intent,
    val icon: Drawable,
    val banner: Drawable?
)

class MainActivity : Activity() {

    private val wallpapers = listOf(
        "Crimson" to R.drawable.wp_crimson,
        "Ocean" to R.drawable.wp_ocean,
        "Aurora" to R.drawable.wp_aurora,
        "Sunset" to R.drawable.wp_sunset,
        "Midnight" to R.drawable.wp_midnight,
        "Forest" to R.drawable.wp_forest
    )

    // Pinned to the bottom bar by default if installed (you can change this with the Menu button).
    private val preferredPackages = listOf(
        "com.netflix.ninja",
        "com.amazon.firebat",
        "com.amazon.avod",
        "com.amazon.firetv.youtube",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
        "com.plexapp.android",
        "org.xbmc.kodi"
    )

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("launcher", Context.MODE_PRIVATE)
    }
    private val ui = Handler(Looper.getMainLooper())
    private val adapter = AppAdapter()
    private var apps: List<AppEntry> = emptyList()
    private val dockFocusListener = View.OnFocusChangeListener { v, hasFocus ->
        val scale = if (hasFocus) 1.1f else 1f
        v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }
    private var lastSelected: View? = null

    private var weather: Weather? = null
    private var weatherError: String? = null
    private var weatherFetchedAt = 0L
    private var weatherJob = 0

    private lateinit var wallpaperView: ImageView
    private lateinit var mainContent: ViewGroup
    private lateinit var grid: GridView
    private lateinit var gridTitle: TextView
    private lateinit var allAppsPanel: View
    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var clockSuffix: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherChip: View
    private lateinit var chipIcon: WeatherIconView
    private lateinit var chipTemp: TextView
    private lateinit var dock: LinearLayout
    private lateinit var weatherPanel: View
    private lateinit var wxPlace: TextView
    private lateinit var wxIcon: WeatherIconView
    private lateinit var wxTemp: TextView
    private lateinit var wxCondition: TextView
    private lateinit var wxDetail: TextView
    private lateinit var wxStats1: LinearLayout
    private lateinit var wxStats2: LinearLayout
    private lateinit var wxForecast: LinearLayout
    private lateinit var wxChange: View
    private lateinit var wxClose: View

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            ui.postDelayed(this, 15_000)
        }
    }

    // Refresh the app list when apps are installed, updated or removed.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wallpaperView = findViewById(R.id.wallpaper)
        mainContent = findViewById(R.id.mainContent)
        grid = findViewById(R.id.grid)
        gridTitle = findViewById(R.id.gridTitle)
        allAppsPanel = findViewById(R.id.allAppsPanel)
        greeting = findViewById(R.id.greeting)
        clock = findViewById(R.id.clock)
        clockSuffix = findViewById(R.id.clockSuffix)
        dateText = findViewById(R.id.dateText)
        weatherChip = findViewById(R.id.weatherChip)
        chipIcon = findViewById(R.id.chipIcon)
        chipTemp = findViewById(R.id.chipTemp)
        dock = findViewById(R.id.dock)
        weatherPanel = findViewById(R.id.weatherPanel)
        wxPlace = findViewById(R.id.wxPlace)
        wxIcon = findViewById(R.id.wxIcon)
        wxTemp = findViewById(R.id.wxTemp)
        wxCondition = findViewById(R.id.wxCondition)
        wxDetail = findViewById(R.id.wxDetail)
        wxStats1 = findViewById(R.id.wxStats1)
        wxStats2 = findViewById(R.id.wxStats2)
        wxForecast = findViewById(R.id.wxForecast)
        wxChange = findViewById(R.id.wxChange)
        wxClose = findViewById(R.id.wxClose)

        // Creates the app's own folder so a custom wallpaper can be pushed into it (see README).
        getExternalFilesDir(null)

        grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            launch(adapter.getItem(position))
            hideAllApps()
        }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                scaleCard(lastSelected, 1f)
                lastSelected = view
                if (grid.hasFocus()) scaleCard(view, 1.04f)
                if (position in 0 until adapter.count) {
                    gridTitle.text = adapter.getItem(position).label
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                scaleCard(lastSelected, 1f)
                lastSelected = null
            }
        }
        grid.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            scaleCard(lastSelected, if (hasFocus) 1.04f else 1f)
        }

        weatherChip.setOnClickListener { showWeather() }
        findViewById<View>(R.id.pillWallpaper).setOnClickListener { showWallpaperPicker() }
        findViewById<View>(R.id.pillSettings).setOnClickListener { openSettings() }
        wxChange.setOnClickListener { promptCity() }
        wxClose.setOnClickListener { hideWeather() }

        applyWallpaper()
        updateClock()
        updateWeatherChip()
        focusDock()
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
        refreshWeather(false)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
        ui.removeCallbacks(clockTick)
    }

    // Pressing Home while already on the launcher closes overlays and jumps back to the top.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            if (weatherPanel.visibility == View.VISIBLE) hideWeather()
            if (allAppsPanel.visibility == View.VISIBLE) hideAllApps()
            focusDock()
        }
    }

    // A launcher should never exit on Back; it just closes the weather panel.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (weatherPanel.visibility == View.VISIBLE) hideWeather()
        else if (allAppsPanel.visibility == View.VISIBLE) hideAllApps()
    }

    // Menu button (three lines on the Fire TV remote): pin / unpin / app info for the highlighted app.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val weatherOpen = weatherPanel.visibility == View.VISIBLE
        val appsOpen = allAppsPanel.visibility == View.VISIBLE
        if (keyCode == KeyEvent.KEYCODE_MENU && !weatherOpen) {
            focusedApp()?.let { showAppMenu(it) }
            return true
        }
        // Down on the favourites bar reveals every app.
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !weatherOpen && !appsOpen &&
            currentFocus?.parent === dock
        ) {
            showAllApps()
            return true
        }
        // Up from the top row of the grid goes back home.
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && appsOpen &&
            grid.selectedItemPosition in 0 until grid.numColumns
        ) {
            hideAllApps()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun focusedApp(): AppEntry? {
        val focused = currentFocus
        if (focused === grid) {
            val pos = grid.selectedItemPosition
            return if (pos in 0 until adapter.count) adapter.getItem(pos) else null
        }
        val tag = focused?.tag
        return if (tag is AppEntry) tag else null
    }

    // ---------------------------------------------------------------- clock

    private fun updateClock() {
        val now = Date()
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        val locale = Locale.getDefault()
        clock.text = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", locale).format(now)
        clockSuffix.text = if (is24) "" else SimpleDateFormat("a", locale).format(now).uppercase()
        dateText.text = SimpleDateFormat("EEEE d MMMM", locale).format(now).uppercase()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.text = when (hour) {
            in 5..11 -> "GOOD MORNING"
            in 12..17 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
    }

    // ---------------------------------------------------------------- apps

    private fun loadApps() {
        Thread {
            val result = queryApps()
            runOnUiThread {
                apps = result
                adapter.submit(result)
                rebuildDock()
            }
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
                        icon = info.loadIcon(pm),
                        banner = info.activityInfo.loadBanner(pm)
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

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            val settingsApp = apps.firstOrNull { it.label.equals("Settings", ignoreCase = true) }
            if (settingsApp != null) {
                launch(settingsApp)
            } else {
                Toast.makeText(this, "Couldn't open Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scaleCard(item: View?, scale: Float) {
        item?.findViewById<View>(R.id.card)?.animate()?.scaleX(scale)?.scaleY(scale)?.setDuration(120)?.start()
    }

    // ---------------------------------------------------------------- bottom bar

    private fun pinnedPackages(): List<String> {
        val saved = prefs.getString("pins", null)
        if (saved != null) {
            return if (saved.isEmpty()) emptyList() else saved.split(",")
        }
        val installed = apps.map { it.packageName }.toSet()
        val defaults = preferredPackages.filter { it in installed }.toMutableList()
        for (app in apps) {
            if (defaults.size >= 6) break
            if (app.packageName !in defaults) defaults.add(app.packageName)
        }
        return defaults
    }

    private fun rebuildDock() {
        val hadFocus = dock.hasFocus()
        dock.removeAllViews()
        val byPackage = apps.associateBy { it.packageName }
        val inflater = LayoutInflater.from(this)
        for (pkg in pinnedPackages()) {
            val app = byPackage[pkg] ?: continue
            val item = inflater.inflate(R.layout.item_dock, dock, false)
            val icon = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon
            item.findViewById<ImageView>(R.id.dockIcon).setImageDrawable(icon)
            item.findViewById<TextView>(R.id.dockLabel).text = app.label
            item.tag = app
            item.setOnClickListener { launch(app) }
            item.onFocusChangeListener = dockFocusListener
            dock.addView(item)
        }
        // Always-present "All apps" tile, so the grid is reachable by clicking too.
        val all = inflater.inflate(R.layout.item_dock, dock, false)
        all.findViewById<ImageView>(R.id.dockIcon).setImageResource(R.drawable.ic_apps)
        all.findViewById<TextView>(R.id.dockLabel).text = getString(R.string.all_apps)
        all.tag = "ALL"
        all.setOnClickListener { showAllApps() }
        all.onFocusChangeListener = dockFocusListener
        dock.addView(all)

        if (hadFocus || (currentFocus == null && allAppsPanel.visibility != View.VISIBLE)) focusDock()
    }

    private fun focusDock() {
        if (dock.childCount > 0) dock.getChildAt(0).requestFocus()
    }

    private fun showAllApps() {
        mainContent.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        allAppsPanel.alpha = 0f
        allAppsPanel.translationY = dp(60).toFloat()
        allAppsPanel.visibility = View.VISIBLE
        allAppsPanel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        grid.setSelection(0)
        grid.requestFocus()
    }

    private fun hideAllApps() {
        if (allAppsPanel.visibility != View.VISIBLE) return
        allAppsPanel.animate().cancel()
        allAppsPanel.visibility = View.GONE
        mainContent.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        // Land back on the "All apps" tile, where they came from.
        dock.getChildAt(dock.childCount - 1)?.requestFocus()
    }

    private fun showAppMenu(app: AppEntry) {
        val pinned = pinnedPackages().contains(app.packageName)
        val options = arrayOf(
            if (pinned) "Remove from bottom bar" else "Add to bottom bar",
            "App info / uninstall"
        )
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                if (which == 0) togglePin(app) else openAppInfo(app)
            }
            .show()
    }

    private fun togglePin(app: AppEntry) {
        val pins = pinnedPackages().toMutableList()
        if (!pins.remove(app.packageName)) {
            if (pins.size >= 6) {
                Toast.makeText(this, "The bottom bar holds up to 6 apps", Toast.LENGTH_SHORT).show()
                return
            }
            pins.add(app.packageName)
        }
        prefs.edit().putString("pins", pins.joinToString(",")).apply()
        rebuildDock()
    }

    // ---------------------------------------------------------------- wallpaper

    private fun customWallpaperFile(): File? =
        getExternalFilesDir(null)?.let { File(it, "wallpaper.jpg") }

    private fun applyWallpaper() {
        val choice = prefs.getInt("wallpaper", 0)
        val custom = customWallpaperFile()
        if (choice == -1 && custom != null && custom.exists()) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(custom.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(custom.path, opts)
            if (bitmap != null) {
                wallpaperView.setImageBitmap(bitmap)
                return
            }
        }
        wallpaperView.setImageResource(wallpapers[choice.coerceIn(0, wallpapers.size - 1)].second)
    }

    private fun showWallpaperPicker() {
        val names = ArrayList<String>(wallpapers.map { it.first })
        val custom = customWallpaperFile()
        val hasCustom = custom != null && custom.exists()
        if (hasCustom) names.add("Custom image")
        val saved = prefs.getInt("wallpaper", 0)
        val current = if (saved == -1) names.size - 1 else saved.coerceIn(0, wallpapers.size - 1)

        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Wallpaper")
            .setSingleChoiceItems(names.toTypedArray(), current) { dialog, which ->
                val value = if (hasCustom && which == names.size - 1) -1 else which
                prefs.edit().putInt("wallpaper", value).apply()
                applyWallpaper()
                dialog.dismiss()
            }
            .show()
    }

    // ---------------------------------------------------------------- weather

    private fun savedPlace(): Place? {
        val name = prefs.getString("city_name", null) ?: return null
        val lat = prefs.getString("city_lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("city_lon", null)?.toDoubleOrNull() ?: return null
        return Place(name, lat, lon)
    }

    private fun refreshWeather(force: Boolean) {
        val fresh = weather != null &&
            SystemClock.elapsedRealtime() - weatherFetchedAt < 15 * 60 * 1000L
        if (fresh && !force) return

        val job = ++weatherJob
        val saved = savedPlace()
        Thread {
            var fetched: Weather? = null
            var problem: String? = null
            try {
                val place = saved ?: WeatherApi.locateByIp()
                if (place == null) {
                    problem = "Couldn't work out where you are. Use Change city to pick one."
                } else {
                    fetched = WeatherApi.fetch(place)
                }
            } catch (e: Exception) {
                problem = "Couldn't load the weather. Check the TV's internet connection."
            }
            val result = fetched
            val message = problem
            runOnUiThread {
                if (job != weatherJob) return@runOnUiThread
                if (result != null) {
                    weather = result
                    weatherError = null
                    weatherFetchedAt = SystemClock.elapsedRealtime()
                } else if (weather == null) {
                    weatherError = message
                }
                updateWeatherChip()
                if (weatherPanel.visibility == View.VISIBLE) renderWeatherPanel()
            }
        }.start()
    }

    private fun updateWeatherChip() {
        val w = weather
        if (w == null) {
            chipIcon.kind = WxKind.CLOUD
            chipTemp.text = "--°"
        } else {
            chipIcon.kind = w.kind
            chipTemp.text = "${w.temp}°"
        }
    }

    private fun showWeather() {
        renderWeatherPanel()
        mainContent.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        weatherPanel.visibility = View.VISIBLE
        wxChange.requestFocus()
        refreshWeather(weather == null)
    }

    private fun hideWeather() {
        weatherPanel.visibility = View.GONE
        mainContent.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        weatherChip.requestFocus()
    }

    private fun renderWeatherPanel() {
        wxStats1.removeAllViews()
        wxStats2.removeAllViews()
        wxForecast.removeAllViews()

        val w = weather
        if (w == null) {
            wxPlace.text = "Weather"
            wxIcon.kind = WxKind.CLOUD
            wxTemp.text = "--°"
            wxCondition.text = weatherError ?: "Loading…"
            wxDetail.text = ""
            return
        }

        wxPlace.text = w.place
        wxIcon.kind = w.kind
        wxTemp.text = "${w.temp}°"
        wxCondition.text = w.description
        wxDetail.text = "Feels like ${w.feelsLike}°   ·   High ${w.hi}°   Low ${w.lo}°"

        wxStats1.addView(statTile("Humidity", "${w.humidity}%"))
        wxStats1.addView(statTile("Wind", "${w.windMph} mph ${w.windDir}"))
        wxStats1.addView(statTile("Pressure", "${w.pressure} hPa"))
        wxStats2.addView(statTile("Chance of rain", "${w.rainChance}%"))
        wxStats2.addView(statTile("Sunrise", w.sunrise))
        wxStats2.addView(statTile("Sunset", w.sunset))

        for (day in w.days.take(5)) {
            wxForecast.addView(forecastItem(day))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statTile(label: String, value: String): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.tile_bg)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
        }
        tile.addView(TextView(this).apply {
            text = label.uppercase()
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 10.5f
            letterSpacing = 0.12f
        })
        tile.addView(TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, dp(2), 0, 0)
        })
        return tile
    }

    private fun forecastItem(day: DayForecast): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        column.addView(TextView(this).apply {
            text = day.label.uppercase()
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 12f
            letterSpacing = 0.1f
        })
        column.addView(WeatherIconView(this).apply {
            kind = day.kind
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
        })
        column.addView(TextView(this).apply {
            text = "${day.hi}° / ${day.lo}°"
            setTextColor(Color.WHITE)
            textSize = 14f
        })
        return column
    }

    private fun promptCity() {
        val input = EditText(this).apply {
            setSingleLine()
            hint = "e.g. London"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#88FFFFFF"))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Set your city")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) lookUpCity(query)
            }
            .setNeutralButton("Use my location") { _, _ ->
                prefs.edit().remove("city_name").remove("city_lat").remove("city_lon").apply()
                weather = null
                weatherError = null
                renderWeatherPanel()
                refreshWeather(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun lookUpCity(query: String) {
        Toast.makeText(this, "Looking up $query…", Toast.LENGTH_SHORT).show()
        Thread {
            var found: Place? = null
            try {
                found = WeatherApi.geocode(query)
            } catch (e: Exception) {
                // treated as "not found" below
            }
            val place = found
            runOnUiThread {
                if (place == null) {
                    Toast.makeText(this, "Couldn't find \"$query\"", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                prefs.edit()
                    .putString("city_name", place.name)
                    .putString("city_lat", place.lat.toString())
                    .putString("city_lon", place.lon.toString())
                    .apply()
                weather = null
                weatherError = null
                updateWeatherChip()
                renderWeatherPanel()
                refreshWeather(true)
            }
        }.start()
    }

    // ---------------------------------------------------------------- app grid adapter

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

            val banner = view.findViewById<ImageView>(R.id.banner)
            val fallback = view.findViewById<View>(R.id.fallback)
            if (app.banner != null) {
                banner.setImageDrawable(app.banner)
                banner.visibility = View.VISIBLE
                fallback.visibility = View.GONE
            } else {
                banner.setImageDrawable(null)
                banner.visibility = View.GONE
                fallback.visibility = View.VISIBLE
                view.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
                view.findViewById<TextView>(R.id.label).text = app.label
            }

            val gridView = parent as? GridView
            val selected = gridView != null && gridView.hasFocus() && position == gridView.selectedItemPosition
            val card = view.findViewById<View>(R.id.card)
            card.animate().cancel()
            card.scaleX = if (selected) 1.04f else 1f
            card.scaleY = if (selected) 1.04f else 1f
            if (selected) lastSelected = view
            return view
        }
    }
}

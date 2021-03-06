package com.omarea.vtools.activities

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.library.shell.GAppsUtilis
import com.omarea.model.Appinfo
import com.omarea.scene_mode.FreezeAppShortcutHelper
import com.omarea.scene_mode.LogoCacheManager
import com.omarea.scene_mode.SceneMode
import com.omarea.store.SceneConfigStore
import com.omarea.store.SpfConfig
import com.omarea.ui.FreezeAppAdapter
import com.omarea.ui.TabIconHelper
import com.omarea.utils.AppListHelper
import com.omarea.vtools.R
import kotlinx.android.synthetic.main.activity_freeze_apps.*

class ActivityFreezeApps : ActivityBase() {
    private lateinit var processBarDialog: ProgressBarDialog
    private var freezeApps = java.util.ArrayList<String>()
    private var handler: Handler = Handler(Looper.getMainLooper())
    private lateinit var config: SharedPreferences
    private var useSuspendMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_freeze_apps)
        setBackArrow()

        onViewCreated()

        // 使用壁纸高斯模糊作为窗口背景
        // val wallPaper = WallpaperManager.getInstance(this).getDrawable();
        // this.getWindow().setBackgroundDrawable(wallPaper);

        // this.getWindow().setBackgroundDrawable(BitmapDrawable(resources, rsBlur((wallPaper as BitmapDrawable).bitmap, 25)))
    }

    private fun rsBlur(source: Bitmap, radius: Int): Bitmap {
        val inputBmp = source
        val renderScript = RenderScript.create(this);

        // Allocate memory for Renderscript to work with
        //(2)
        val input = Allocation.createFromBitmap(renderScript, inputBmp);
        val output = Allocation.createTyped(renderScript, input.getType());
        //(3)
        // Load up an instance of the specific script that we want to use.
        val scriptIntrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        //(4)
        scriptIntrinsicBlur.setInput(input);
        //(5)
        // Set the blur radius
        scriptIntrinsicBlur.setRadius(radius.toFloat());
        //(6)
        // Start the ScriptIntrinisicBlur
        scriptIntrinsicBlur.forEach(output);
        //(7)
        // Copy the output to the blurred bitmap
        output.copyTo(inputBmp);
        //(8)
        renderScript.destroy();

        return inputBmp;
    }

    override fun onPause() {
        super.onPause()
        System.gc()
    }


    private fun onViewCreated() {
        val tabHost = freeze_tabhost
        tabHost.setup()
        val tabIconHelper = TabIconHelper(tabHost, this)
        tabIconHelper.newTabSpec("应用", getDrawable(R.drawable.tab_app)!!, R.id.tab_freeze_apps)
        tabIconHelper.newTabSpec("设置", getDrawable(R.drawable.tab_settings)!!, R.id.tab_freeze_settings)
        tabHost.setOnTabChangedListener { _ ->
            tabIconHelper.updateHighlight()
        }

        config = this.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        useSuspendMode = config.getBoolean(SpfConfig.GLOBAL_SPF_FREEZE_SUSPEND, Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        freeze_suspend_mode.run {
            isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            isChecked = useSuspendMode
            setOnClickListener {
                val checked = (it as CompoundButton).isChecked
                switchSuspendMode(checked)

                useSuspendMode = checked
                config.edit().putBoolean(SpfConfig.GLOBAL_SPF_FREEZE_SUSPEND, useSuspendMode).apply()

                freeze_any_unfreeze.isEnabled = checked
            }
        }

        freeze_any_unfreeze.run {
            isEnabled = useSuspendMode
            isChecked = config.getBoolean(SpfConfig.GLOBAL_SPF_FREEZE_XPOSED_OPEN, false)
            setOnClickListener {
                config.edit().putBoolean(SpfConfig.GLOBAL_SPF_FREEZE_XPOSED_OPEN, (it as CompoundButton).isChecked).apply()
            }
        }

        freeze_click_open.isChecked = config.getBoolean(SpfConfig.GLOBAL_SPF_FREEZE_CLICK_OPEN, false)
        freeze_click_open.setOnClickListener {
            config.edit().putBoolean(SpfConfig.GLOBAL_SPF_FREEZE_CLICK_OPEN, (it as CompoundButton).isChecked).apply()
        }

        freeze_shortcut_suggest.isChecked = config.getBoolean(SpfConfig.GLOBAL_SPF_FREEZE_ICON_NOTIFY, true)
        freeze_shortcut_suggest.setOnClickListener {
            config.edit().putBoolean(SpfConfig.GLOBAL_SPF_FREEZE_ICON_NOTIFY, (it as CompoundButton).isChecked).apply()
        }

        freeze_time_limit.run {
            progress = config.getInt(SpfConfig.GLOBAL_SPF_FREEZE_TIME_LIMIT, 2)
            freeze_time_limit_text.text = progress.toString()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    freeze_time_limit_text.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.run {
                        config.edit().putInt(SpfConfig.GLOBAL_SPF_FREEZE_TIME_LIMIT, seekBar.progress).apply()
                    }
                }
            })
        }

        freeze_item_limit.run {
            progress = config.getInt(SpfConfig.GLOBAL_SPF_FREEZE_ITEM_LIMIT, 5)
            freeze_item_limit_text.text =  progress.toString()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    freeze_item_limit_text.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.run {
                        config.edit().putInt(SpfConfig.GLOBAL_SPF_FREEZE_ITEM_LIMIT, seekBar.progress).apply()
                    }
                }
            })
        }

        freeze_screen_delay.run {
            progress = config.getInt(SpfConfig.GLOBAL_SPF_FREEZE_DELAY, 0)
            freeze_screen_delay_text.text =  progress.toString()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    freeze_screen_delay_text.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.run {
                        config.edit().putInt(SpfConfig.GLOBAL_SPF_FREEZE_DELAY, seekBar.progress).apply()
                    }
                }
            })
        }

        processBarDialog = ProgressBarDialog(context)

        processBarDialog.showDialog()

        // 点击应用图标
        freeze_apps.setOnItemClickListener { parent, itemView, position, _ ->
            try {
                val appInfo = (parent.adapter.getItem(position) as Appinfo)
                if (config.getBoolean(SpfConfig.GLOBAL_SPF_FREEZE_CLICK_OPEN, false)) {
                    startApp(appInfo)
                } else {
                    toggleEnable(appInfo)
                    (freeze_apps.adapter as FreezeAppAdapter).updateRow(position, freeze_apps, appInfo)
                }
            } catch (ex: Exception) {
            }
        }

        // 长按图标
        freeze_apps.setOnItemLongClickListener { parent, itemView, position, _ ->
            val item = (parent.adapter.getItem(position) as Appinfo)
            showOptions(item, position, itemView)
            true
        }

        // 菜单按钮
        freeze_menu.setOnClickListener {
            freezeOptionsDialog()
        }

        loadData()

        freeze_apps_search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (freeze_apps.adapter as Filterable).getFilter().filter(if (s == null) "" else s.toString())
            }
        })
    }

    private fun loadData() {
        Thread(Runnable {
            try {
                // 数据库
                val store = SceneConfigStore(context)
                // 数据库中记录的已添加的偏见应用
                freezeApps = store.freezeAppList
                val checkShortcuts = config.getBoolean(SpfConfig.GLOBAL_SPF_FREEZE_ICON_NOTIFY, true)
                // 已添加到桌面的快捷方式
                val pinnedShortcuts = if(checkShortcuts) FreezeAppShortcutHelper().getPinnedShortcuts(context) else arrayListOf()

                val lostedShortcuts = ArrayList<Appinfo>()
                val lostedShortcutsName = StringBuilder()

                // val allApp = AppListHelper(context).getAll()
                val appListHelper = AppListHelper(context)

                val freezeAppsInfo = ArrayList<Appinfo>()
                // 遍历偏见应用列表 获取应用详情
                for (it in freezeApps) {
                    val packageName = it
                    val result = appListHelper.getApp(packageName)
                    if (result != null) {
                        freezeAppsInfo.add(result)

                        // 检查是否添加了快捷方式，如果没有则记录下来
                        if (checkShortcuts && !pinnedShortcuts.contains(it)) {
                            lostedShortcuts.add(result)
                            lostedShortcutsName.append(result.appName).append("\n")
                        }
                    }
                }
                store.close()

                handler.post {
                    try {
                        freeze_apps.adapter = FreezeAppAdapter(this.applicationContext, freezeAppsInfo)
                        processBarDialog.hideDialog()

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // 即时是Oreo也可能出现获取不到已添加的快捷方式的情况，例如换了第三方桌面
                            // 如果发现有快捷方式丢失，提示是否重新添加
                            if (lostedShortcuts.size > 0) {
                                shortcutsLostDialog(lostedShortcutsName.toString(), lostedShortcuts)
                            }
                        }
                    } catch (ex: java.lang.Exception) {
                    }
                }
            } catch (ex: java.lang.Exception) {
            }
        }).start()
    }

    /**
     * 显示快捷方式丢失，提示添加
     */
    private fun shortcutsLostDialog(lostedShortcutsName: String, lostedShortcuts: ArrayList<Appinfo>) {
        if (!config.getBoolean(SpfConfig.GLOBAL_SPF_FREEZE_ICON_NOTIFY, true)) {
            return
        }

        DialogHelper.animDialog(AlertDialog.Builder(context)
                .setTitle(getString(R.string.freeze_shortcut_lost))
                .setMessage(getString(R.string.freeze_shortcut_lost_desc) + "\n\n$lostedShortcutsName")
                .setPositiveButton(R.string.btn_confirm) { _, _ ->
                    processBarDialog.showDialog(getString(R.string.please_wait))
                    CreateShortcutThread(lostedShortcuts, context, Runnable {
                        handler.post {
                            loadData()
                            processBarDialog.hideDialog()
                        }
                    }).start()
                }
                .setNegativeButton(R.string.btn_cancel) { _, _ ->
                }
                .setNeutralButton(R.string.btn_dontshow) { _, _ ->
                    config.edit().putBoolean(SpfConfig.GLOBAL_SPF_FREEZE_ICON_NOTIFY, false).apply()
                })
    }

    private fun showOptions(appInfo: Appinfo, position: Int, view: View) {
        DialogHelper.animDialog(AlertDialog.Builder(context)
                .setTitle(appInfo.appName)
                .setItems(
                        arrayOf(
                                getString(R.string.freeze_open),
                                getString(R.string.freeze_shortcut_rebuild),
                                getString(R.string.freeze_remove),
                                if (appInfo.suspended || !appInfo.enabled) getString(R.string.freeze_enable)  else getString(R.string.freeze_diasble),
                                getString(R.string.freeze_remove_uninstall))) { _, which ->

                    when (which) {
                        0 -> startApp(appInfo)
                        1 -> createShortcut(appInfo)
                        2 -> {
                            removeConfig(appInfo)
                            loadData()
                        }
                        3 -> {
                            toggleEnable(appInfo)
                            loadData()
                        }
                        4 -> {
                            DialogHelper.animDialog(
                                    AlertDialog.Builder(context).setTitle("确定要卸载【${appInfo.appName}】？").setNeutralButton(R.string.btn_cancel) { _, _ ->
                                    }.setPositiveButton(R.string.btn_confirm) { _, _ ->
                                        removeAndUninstall(appInfo)
                                        loadData()
                                    })
                        }
                    }
                }
                .setCancelable(true))
    }

    private fun removeConfig(appInfo: Appinfo) {
        if ((!appInfo.enabled) || appInfo.suspended) {
            enableApp(appInfo)
        }

        val packageName = appInfo.packageName.toString()
        val store = SceneConfigStore(context)
        val config = store.getAppConfig(packageName)
        config.freeze = false
        store.setAppConfig(config)
        store.close()

        SceneMode.getCurrentInstance()?.removeFreezeAppHistory(packageName)
        FreezeAppShortcutHelper().removeShortcut(context, packageName)
    }

    // TODO:替换公共方法
    fun getUserId(context: Context): Int {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        val userHandle = android.os.Process.myUserHandle()

        var value = 0
        try {
            value = um.getSerialNumberForUser(userHandle).toInt()
        } catch (ignored: Exception) {
        }

        return value
    }

    // 切换[图标置灰模式]
    private fun switchSuspendMode(enabled: Boolean) {
        processBarDialog.showDialog()
        if (enabled) {
        } else {
        }
        processBarDialog.showDialog()
        for (it in freezeApps) {
            enableApp(it)
        }
        processBarDialog.hideDialog()
        loadData()
    }

    private fun removeAndUninstall(appInfo: Appinfo) {
        removeConfig(appInfo)
        KeepShellPublic.doCmdSync("pm uninstall --user " + getUserId(context) + " " + appInfo.packageName)
    }

    private fun enableApp(appInfo: Appinfo) {
        enableApp(appInfo.packageName.toString())
    }

    private fun enableApp(packageName: String) {
        SceneMode.unfreezeApp(packageName)
    }

    private fun disableApp(appInfo: Appinfo) {
        disableApp(appInfo.packageName.toString())
    }

    private fun disableApp(packageName: String) {
        if (useSuspendMode) {
            SceneMode.suspendApp(packageName)
        } else {
            SceneMode.freezeApp(packageName)
        }
    }

    private fun toggleEnable(appInfo: Appinfo) {
        if (!appInfo.enabled || appInfo.suspended) {
            enableApp(appInfo)
            Toast.makeText(context, getString(R.string.freeze_enable_completed), Toast.LENGTH_SHORT).show()
            appInfo.enabled = true
            appInfo.suspended = false
        } else {
            disableApp(appInfo)
            Toast.makeText(context, getString(R.string.freeze_disable_completed), Toast.LENGTH_SHORT).show()
            appInfo.enabled = false
            appInfo.suspended = true
        }
    }

    private fun startApp(appInfo: Appinfo) {
        if (((!appInfo.enabled) || appInfo.suspended)) {
            enableApp(appInfo)
        }
        try {
            val intent = this.packageManager.getLaunchIntentForPackage(appInfo.packageName.toString())
            // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_TASK_ON_HOME)

            // i.setFlags((i.getFlags() & ~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) | Intent.FLAG_ACTIVITY_NEW_TASK);
            // i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // i.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            // i.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
            // i.setFlags(0x10200000);
            // Log.d("getAppSwitchIntent", "" + i.getFlags());
            intent?.run {
                setFlags(getFlags() and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED.inv() or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
                // setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // @参考 https://blog.csdn.net/weixin_34335458/article/details/88020972
                // setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // @参考 https://blog.csdn.net/weixin_34335458/article/details/88020972
                setPackage(null) // 加上这句代
            }

            if (intent != null) {
                this.startActivity(intent)
                SceneMode.getCurrentInstance()?.setFreezeAppStartTime(appInfo.packageName.toString())
            }
        } catch (ex: java.lang.Exception) {
        }

        // TODO: this.finish()
    }

    private fun createShortcut(appInfo: Appinfo) {
        if ((!appInfo.enabled) || appInfo.suspended) {
            enableApp(appInfo)
        }
        if (FreezeAppShortcutHelper().createShortcut(context, appInfo.packageName.toString())) {
            Toast.makeText(context, getString(R.string.freeze_shortcut_add_success), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, getString(R.string.freeze_shortcut_add_fail), Toast.LENGTH_SHORT).show()
        }
    }

    private class CreateShortcutThread(private var apps: ArrayList<Appinfo>, private var context: Context, private var onCompleted: Runnable) : Thread() {
        override fun run() {
            for (appInfo in apps) {
                if ((!appInfo.enabled) || appInfo.suspended) {
                    KeepShellPublic.doCmdSync("pm enable ${appInfo.packageName}")
                }
                sleep(3000)
                FreezeAppShortcutHelper().createShortcut(context, appInfo.packageName.toString())
            }
            onCompleted.run()
        }
    }

    private fun addFreezeAppDialog() {
        val allApp = AppListHelper(context).getBootableApps(false, true)
        val apps = allApp.filter { !freezeApps.contains(it.packageName) }
        val items = apps.map { it.appName.toString() }.toTypedArray()
        val states = items.map { false }.toBooleanArray()

        DialogHelper.animDialog(AlertDialog.Builder(context)
                .setTitle(getString(R.string.freeze_add))
                .setMultiChoiceItems(items, states) { dialog, which, isChecked ->
                    states[which] = isChecked
                }
                .setPositiveButton(R.string.btn_confirm) { _, _ ->
                    val selectedItems = ArrayList<String>()
                    for (index in states.indices) {
                        if (states[index]) {
                            selectedItems.add(apps[index].packageName.toString())
                        }
                    }
                    addFreezeApps(selectedItems)
                }
                .setNegativeButton(R.string.btn_cancel, { _, _ -> })
                .setCancelable(true))
    }

    private fun addFreezeApps(selectedItems: ArrayList<String>) {
        processBarDialog.showDialog(getString(R.string.please_wait))
        val next = Runnable {
            handler.post {
                try {
                    loadData()
                    processBarDialog.hideDialog()
                } catch (ex: java.lang.Exception) {
                }
            }
        }
        AddFreezeAppsThread(context, selectedItems, next).start()
    }

    private class AddFreezeAppsThread(private var context: Context, private var selectedItems: ArrayList<String>, private var onCompleted: Runnable) : Thread() {
        val packageManager: PackageManager = context.packageManager
        override fun run() {
            val store = SceneConfigStore(context)
            val iconManager = LogoCacheManager(context)

            for (it in selectedItems) {
                val config = store.getAppConfig(it)

                config.freeze = true
                if (store.setAppConfig(config)) {
                    val icon = getAppIcon(it)
                    if (icon != null) {
                        iconManager.saveIcon(icon, it)
                    }
                }
            }
            store.close()

            onCompleted.run()
        }

        private fun getAppIcon(packageName: String): Drawable? {
            return packageManager.getApplicationIcon(packageName)
        }
    }

    private fun freezeOptionsDialog() {
        val p = packageManager
        val startActivity = ComponentName(this.applicationContext, ActivityFreezeApps::class.java)
        val enabled = p.getComponentEnabledSetting(startActivity) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        DialogHelper.animDialog(AlertDialog.Builder(context)
                .setTitle(getString(R.string.freeze_apps_manage))
                .setItems(
                        arrayOf(
                                getString(R.string.freeze_add_app),
                                getString(R.string.freeze_shortcut_rebuild),
                                getString(R.string.freeze_enable_all),
                                getString(R.string.freeze_disable_all),
                                if (enabled) getString(R.string.freeze_hidden_entrance) else getString(R.string.freeze_show_entrance),
                                getString(R.string.freeze_clear_all))) { _, which ->

                    when (which) {
                        0 -> addFreezeAppDialog()
                        1 -> createShortcutAll()
                        2 -> {
                            processBarDialog.showDialog()
                            for (it in freezeApps) {
                                enableApp(it)
                            }
                            processBarDialog.hideDialog()
                            loadData()
                        }
                        3 -> {
                            processBarDialog.showDialog()
                            for (it in freezeApps) {
                                disableApp(it)
                            }
                            processBarDialog.hideDialog()
                            loadData()
                        }
                        4 -> {
                            try {
                                if (enabled) {
                                    p.setComponentEnabledSetting(startActivity, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                                } else {
                                    p.setComponentEnabledSetting(startActivity, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                                }
                                Toast.makeText(context, getString(R.string.freeze_entrance_changed), Toast.LENGTH_SHORT).show()
                            } catch (ex: java.lang.Exception) {
                            }
                        }
                        5 -> {
                            processBarDialog.showDialog()
                            RemoveAllThread(context, freezeApps, Runnable {
                                handler.post {
                                    loadData()
                                    processBarDialog.hideDialog()
                                    Toast.makeText(context, getString(R.string.freeze_shortcut_delete_desc), Toast.LENGTH_LONG).show()
                                }
                            }).start()
                        }
                    }
                })
    }

    private fun createShortcutAll() {
        DialogHelper.animDialog(AlertDialog.Builder(context)
                .setMessage(R.string.freeze_batch_add_wran).setPositiveButton(R.string.btn_confirm) { _, _ ->
                    processBarDialog.showDialog(getString(R.string.please_wait))
                    CreateShortcutAllThread(context, freezeApps, Runnable {
                        handler.post {
                            processBarDialog.hideDialog()
                            loadData()
                        }
                    }).start()
                }
                .setNeutralButton(R.string.btn_cancel) { _, _ ->
                })
    }

    private class CreateShortcutAllThread(private var context: Context, private var freezeApps: ArrayList<String>, private var onCompleted: Runnable) : Thread() {
        override fun run() {
            val shortcutHelper = FreezeAppShortcutHelper()
            for (it in freezeApps) {
                KeepShellPublic.doCmdSync("pm unhide $it\npm enable $it")
                sleep(3000)
                shortcutHelper.createShortcut(context, it)
            }
            onCompleted.run()
        }
    }

    private class RemoveAllThread(private var context: Context, private var freezeApps: ArrayList<String>, private var onCompleted: Runnable) : Thread() {
        override fun run() {

            val store = SceneConfigStore(context)
            val shortcutHelper = FreezeAppShortcutHelper()
            for (it in freezeApps) {
                if (it.equals("com.android.vending")) {
                    GAppsUtilis().enable(KeepShellPublic.getDefaultInstance());
                } else {
                    KeepShellPublic.doCmdSync("pm unsuspend ${it}\n pm unhide ${it}\n" + "pm enable ${it}")
                }
                val config = store.getAppConfig(it)
                config.freeze = false
                store.setAppConfig(config)
                shortcutHelper.removeShortcut(context, it)
                SceneMode.getCurrentInstance()?.removeFreezeAppHistory(it)
            }
            store.close()

            onCompleted.run()
        }
    }
}
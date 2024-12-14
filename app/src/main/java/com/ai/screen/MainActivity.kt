package com.ai.screen

import android.Manifest
import android.animation.ObjectAnimator
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Arrays


class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: StaggeredGridLayoutManager

    private val screenshotsAdapter by lazy {
        ScreenshotsAdapter { uri ->
            deleteScreenshot(uri)
        }
    }

    private var hasPermission = false
    private var pendingDeleteUri: Uri? = null
    private val REQUEST_DELETE = 1001

    // 时间戳用于判断是否从后台回前台
    private var lastPauseTime: Long = 0
    private val BACKGROUND_THRESHOLD = 100L

    private lateinit var fabDeleteRecent: FloatingActionButton

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 这里是创建动态快捷方式的代码
        createShortcut();



        recyclerView = findViewById(R.id.rvScreenshots)
        fabDeleteRecent = findViewById(R.id.fab_delete_recent)

        layoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
        layoutManager.reverseLayout = true
        recyclerView.layoutManager = layoutManager
        recyclerView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        recyclerView.adapter = screenshotsAdapter

        fabDeleteRecent.setOnClickListener {
            val currentList = screenshotsAdapter.currentList
            if (currentList.isNotEmpty()) {
                val recentUri = currentList.first()
                deleteScreenshot(recentUri)
            }
        }


        fabDeleteRecent.setOnLongClickListener {
            val intent = intent
            finish()
            startActivity(intent)
            true
        }

        checkPermissionsAndLoad()

        // 获取快捷方式传递的参数
        val param = intent.getStringExtra("param")
        if (param != null) {
            Log.d("MainActivity", "Received param: $param")
            val currentList = screenshotsAdapter.currentList
            if (currentList.isNotEmpty()) {
                val recentUri = currentList.first()
                deleteScreenshot(recentUri)
            }
        }


        //------------------------------------------------------------------------------------------------------------------------
//        val currentList = screenshotsAdapter.currentList
//        if (currentList.isNotEmpty()) {
//            val recentUri = currentList.first()
//            deleteScreenshot(recentUri)
//        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private fun createShortcut() {
        // 创建 Intent，用于启动 MainActivity 并带上参数
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myapp://parameter"))
        intent.setClass(this, MainActivity::class.java)


        // 设置快捷方式的参数（例如：参数 key = "param"）
        intent.putExtra("param", "233")


        // 创建快捷方式
        val shortcut = ShortcutInfo.Builder(this, "id_shortcut_1")
            .setShortLabel("快速删除")
            .setLongLabel("去删除最新截图")
            .setIcon(Icon.createWithResource(this, R.drawable.ai_button)) // 自定义图标
            .setIntent(intent)
            .build()

        // 获取 ShortcutManager 实例
        val shortcutManager = getSystemService(ShortcutManager::class.java)

        // 添加快捷方式
        shortcutManager?.addDynamicShortcuts(Arrays.asList(shortcut))
            ?: Toast.makeText(this, "ShortcutManager not available", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        lastPauseTime = System.currentTimeMillis()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
        if (hasPermission) {
            val currentTime = System.currentTimeMillis()
            // 如果超过阈值则认为从后台返回前台，重启Activity
            if (currentTime - lastPauseTime > BACKGROUND_THRESHOLD && lastPauseTime != 0L) {
                Toast.makeText(this, "已加载最新截图", Toast.LENGTH_SHORT).show()
                val intent = intent
                finish()
                startActivity(intent)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissionsAndLoad() {
        val permission = Manifest.permission.READ_MEDIA_IMAGES
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                hasPermission = true
                loadScreenshots()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                loadScreenshots()
            }
        }

//    private fun loadScreenshots() {
//        val screenshotUris = queryScreenshots()
//        screenshotsAdapter.submitList(screenshotUris) {
//            recyclerView.setHasFixedSize(true)
//            recyclerView.setItemViewCacheSize(screenshotUris.size)
//
//            if (screenshotsAdapter.itemCount > 0) {
//                recyclerView.scrollToPosition(screenshotsAdapter.itemCount - 1)
//            }
//        }
//    }
private fun loadScreenshots() {
    val screenshotUris = queryScreenshots()
    screenshotsAdapter.submitList(screenshotUris) {
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(screenshotUris.size)

        if (screenshotsAdapter.itemCount > 0) {
            recyclerView.scrollToPosition(screenshotsAdapter.itemCount - 1)
        }

        // 给每个元素设置加载动画
        addItemAnimations(screenshotUris.size)
    }
}

    private fun addItemAnimations(itemCount: Int) {
        val handler = Handler(Looper.getMainLooper())
        val delayStep = 1000L // 每个元素延迟的时间（毫秒）

        // 遍历每个 item 给它设置延迟动画
        for (i in 1 until itemCount) {
            handler.postDelayed({
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(i)
                viewHolder?.itemView?.let { itemView ->
                    // 设置动画，例如淡入效果
                    val fadeIn = ObjectAnimator.ofFloat(itemView, "alpha", 1f, 0f,1f)
                    fadeIn.duration = 10000L // 动画持续时间
                    fadeIn.start()
                }
            }, delayStep * i) // 为每个元素设置不同的延迟
        }
    }


    private fun queryScreenshots(): List<Uri> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%Screenshot%")

        val list = mutableListOf<Uri>()
        contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(uri, id.toString())
                list.add(contentUri)
            }
        }
        return list
    }

    private fun deleteScreenshot(uri: Uri) {
        try {
            val rowsDeleted = contentResolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                //finishAndRemoveTask()
                val intent = intent
                finish()
                startActivity(intent)

            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                handleRecoverableSecurityException(e, uri)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleRecoverableSecurityException(
        securityException: RecoverableSecurityException,
        uri: Uri
    ) {
        pendingDeleteUri = uri
        val intentSender: IntentSender = securityException.userAction.actionIntent.intentSender
        startIntentSenderForResult(intentSender, REQUEST_DELETE, null, 0, 0, 0, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DELETE) {
            if (resultCode == RESULT_OK) {
                pendingDeleteUri?.let { uri ->
                    pendingDeleteUri = null
                    deleteScreenshot(uri)
                }
            } else {
                pendingDeleteUri = null
                // ----------------------------------------------------------------------------------------有一种情况是快速点击两个元素导致重叠显示
//                Toast.makeText(this, "233", Toast.LENGTH_SHORT).show()
//                val intent = intent
//                finish()
//                startActivity(intent)
                lastPauseTime = System.currentTimeMillis()
            }
        }
    }
}

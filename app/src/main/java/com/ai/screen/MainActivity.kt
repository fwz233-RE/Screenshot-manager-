package com.ai.screen

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

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
    private val BACKGROUND_THRESHOLD = 1000L

    private lateinit var fabDeleteRecent: FloatingActionButton

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

    private fun loadScreenshots() {
        val screenshotUris = queryScreenshots()
        screenshotsAdapter.submitList(screenshotUris) {
            recyclerView.setHasFixedSize(true)
            recyclerView.setItemViewCacheSize(screenshotUris.size)

            if (screenshotsAdapter.itemCount > 0) {
                recyclerView.scrollToPosition(screenshotsAdapter.itemCount - 1)
            }
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
                // ----------------------------------------------------------------------------------------
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
            }
        }
    }
}

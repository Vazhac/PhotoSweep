package com.photosweep.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: PhotoSweepViewModel by viewModels {
        PhotoSweepViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deleteLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            viewModel.completePendingDelete(it.resultCode, this)
        }

        setContent {
            PhotoSweepTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions[imagePermission()] == true) {
                        viewModel.loadPhotos()
                    }
                }

                LaunchedEffect(Unit) {
                    val permission = imagePermission()
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.loadPhotos()
                    } else {
                        permissionLauncher.launch(arrayOf(permission))
                    }
                }

                PhotoSweepApp(
                    viewModel = viewModel,
                    onLaunchDeleteRequest = { pendingIntent ->
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    },
                    onRequestVideoAccess = {
                        val permission = videoPermission()
                        if (permission == null || ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                permission,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.loadPhotos()
                        } else {
                            permissionLauncher.launch(arrayOf(permission))
                        }
                    },
                )
            }
        }
    }

    private fun imagePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun videoPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            null
        }
    }
}

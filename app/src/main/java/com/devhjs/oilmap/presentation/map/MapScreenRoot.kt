package com.devhjs.oilmap.presentation.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val mapState by viewModel.state.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            mapState.currentLocation ?: LatLng(37.4979, 127.0276), 14f
        )
    }

    // 위치가 실시간으로 갱신되므로, 카메라 자동 이동은 최초 1회로 제한한다.
    // 그러지 않으면 사용자가 지도를 움직일 때마다 현재 위치로 되돌아간다.
    var hasCenteredOnUser by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(mapState.currentLocation, mapState.isMapLoaded) {
        if (mapState.isMapLoaded && !hasCenteredOnUser) {
            mapState.currentLocation?.let { location ->
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(location, 14f)
                )
                hasCenteredOnUser = true
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (isGranted) {
                viewModel.onAction(MapAction.OnPermissionGranted)
            }
        }
    )

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is MapEvent.NavigateToStationDetail -> {
                    onNavigateToDetail(event.stationId)
                }
                is MapEvent.NavigateToSettings -> {
                    onNavigateToSettings()
                }
            }
        }
    }

    MapScreen(
        state = mapState,
        cameraPositionState = cameraPositionState,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}

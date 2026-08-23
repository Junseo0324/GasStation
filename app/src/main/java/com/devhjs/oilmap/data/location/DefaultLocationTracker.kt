package com.devhjs.oilmap.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.devhjs.oilmap.domain.location.LocationTracker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultLocationTracker @Inject constructor(
    private val locationClient: FusedLocationProviderClient,
    private val application: Application
) : LocationTracker {

    override suspend fun getCurrentLocation(): Location? {
        if (!canAccessLocation()) return null

        return try {
            locationClient.lastLocation.await()
        } catch (e: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(
        intervalMillis: Long,
        minUpdateDistanceMeters: Float
    ): Flow<Location?> = callbackFlow {
        if (!canAccessLocation()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        // 반경 1~5km 내 주유소를 찾는 용도이므로 100m 내외 정확도면 충분하다.
        // PRIORITY_HIGH_ACCURACY는 GPS를 계속 깨워 배터리 소모가 크기 때문에
        // BALANCED_POWER_ACCURACY(Wi-Fi/셀 기반)를 사용한다.
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMillis
        )
            // 정지 상태에서 주기마다 값이 흐르는 것을 막는다.
            // 배터리와 불필요한 재조회를 동시에 줄이는 가장 효과적인 옵션.
            .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                // 콜백은 suspend가 아니므로 send가 아닌 trySend를 사용한다.
                result.locations.lastOrNull()?.let { location ->
                    trySend(location)
                }
            }
        }

        locationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )

        // 구독이 취소되면 반드시 요청을 해제한다.
        // 이게 없으면 GPS가 계속 돌고 콜백이 수집자를 붙잡아 배터리와 메모리가 함께 샌다.
        awaitClose {
            locationClient.removeLocationUpdates(locationCallback)
        }
    }
        // 위치는 최신값만 의미가 있다. 버퍼가 밀릴 경우 오래된 값은 의도적으로 버린다.
        .conflate()

    private fun canAccessLocation(): Boolean {
        val hasAccessFineLocationPermission = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasAccessCoarseLocationPermission = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationManager =
            application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        return hasAccessFineLocationPermission &&
                hasAccessCoarseLocationPermission &&
                isGpsEnabled
    }
}

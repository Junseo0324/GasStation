package com.devhjs.oilmap.data.location

import android.location.Location
import com.devhjs.oilmap.domain.location.LocationTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class MockLocationTracker @Inject constructor() : LocationTracker {

    /**
     * 개발 환경 및 테스트용으로 서울 강남역 인근의 고정된 가짜 위경도를 반환합니다.
     */
    override suspend fun getCurrentLocation(): Location? {
        val mockLocation = Location("MockProvider").apply {
            latitude = 37.498095
            longitude = 127.027610
            time = System.currentTimeMillis()
            accuracy = 1.0f
        }
        return mockLocation
    }

    /**
     * 항상 같은 좌표를 방출하므로 이동 거리가 0입니다.
     * 따라서 dev 빌드에서는 위치 변화로 인한 재조회가 발생하지 않습니다.
     */
    override fun getLocationUpdates(
        intervalMillis: Long,
        minUpdateDistanceMeters: Float
    ): Flow<Location?> {
        return flow {
            while (true) {
                emit(getCurrentLocation())
                delay(intervalMillis)
            }
        }
    }
}

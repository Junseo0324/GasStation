package com.devhjs.oilmap.domain.location

import android.location.Location

import kotlinx.coroutines.flow.Flow

interface LocationTracker {
    /**
     * 디바이스의 현재 위치를 가져옵니다.
     * 권한이 없거나 위치 서비스를 사용할 수 없는 경우 null을 반환합니다.
     */
    suspend fun getCurrentLocation(): Location?

    /**
     * 디바이스의 위치 업데이트를 실시간 스트림(Flow)으로 반환합니다.
     *
     * @param intervalMillis 업데이트 주기 (밀리초)
     * @param minUpdateDistanceMeters 이 거리 이상 이동했을 때만 값을 방출합니다.
     *   정지 상태에서 불필요한 갱신이 흐르는 것을 막아 배터리 소모를 줄입니다.
     */
    fun getLocationUpdates(
        intervalMillis: Long = 10_000L,
        minUpdateDistanceMeters: Float = 0f
    ): Flow<Location?>
}

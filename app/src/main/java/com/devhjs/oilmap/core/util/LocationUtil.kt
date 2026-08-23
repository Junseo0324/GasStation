package com.devhjs.oilmap.core.util

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

object LocationUtil {

    /**
     * WGS84 위경도 좌표를 KATEC 좌표계로 변환합니다.
     * 오피넷 API는 거주 위치 기반 주유소 검색 시 KATEC 좌표를 요구합니다.
     */

    private val crsFactory = CRSFactory()
    private val wgs84 = crsFactory.createFromParameters("WGS84", "+proj=longlat +datum=WGS84 +no_defs")
    private val katec = crsFactory.createFromParameters(
        "KATEC",
        "+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 +ellps=bessel +units=m +no_defs +towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43"
    )

    fun wgs84ToKatec(lat: Double, lng: Double): Pair<Double, Double> {
        val transform = CoordinateTransformFactory().createTransform(wgs84, katec)
        val result = ProjCoordinate()

        // 투영 수행 (lng: X, lat: Y)
        transform.transform(ProjCoordinate(lng, lat), result)
        return Pair(result.x, result.y)
    }

    /**
     * KATEC 좌표를 WGS84 위경도로 역변환합니다.
     * Google Maps 마커 표시 시 Station의 x/y(KATEC)를 위경도로 변환할 때 사용합니다.
     */
    fun katecToWgs84(x: Double, y: Double): Pair<Double, Double> {
        val transform = CoordinateTransformFactory().createTransform(katec, wgs84)
        val result = ProjCoordinate()

        transform.transform(ProjCoordinate(x, y), result)
        // result.x = lng, result.y = lat
        return Pair(result.y, result.x)
    }
}

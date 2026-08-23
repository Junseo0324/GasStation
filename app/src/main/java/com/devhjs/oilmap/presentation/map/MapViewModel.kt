package com.devhjs.oilmap.presentation.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devhjs.oilmap.core.util.LocationUtil
import com.devhjs.oilmap.core.util.Result
import com.devhjs.oilmap.domain.location.LocationTracker
import com.devhjs.oilmap.domain.model.OilType
import com.devhjs.oilmap.domain.model.SortType
import com.devhjs.oilmap.domain.usecase.GetAroundStationsUseCase
import com.devhjs.oilmap.domain.usecase.GetUserPreferenceUseCase
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val getAroundStationsUseCase: GetAroundStationsUseCase,
    private val getUserPreferenceUseCase: GetUserPreferenceUseCase,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _state = MutableStateFlow(MapState())

    /**
     * 화면이 State를 구독하는 동안에만 위치 스트림을 켠다.
     *
     * MapScreenRoot가 collectAsStateWithLifecycle로 수집하므로 화면이 백그라운드로 가면
     * 구독이 끊기고, STOP_TIMEOUT_MILLIS 뒤 upstream이 취소되면서 GPS 요청도 함께 해제된다.
     * 화면 회전처럼 짧게 구독이 끊기는 경우에는 타임아웃 덕분에 스트림이 재시작되지 않는다.
     */
    val state: StateFlow<MapState> = _state
        .onStart { startLocationUpdates() }
        .onCompletion { stopLocationUpdates() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MapState()
        )

    private val _event = MutableSharedFlow<MapEvent>()
    val event = _event.asSharedFlow()

    private var locationJob: Job? = null
    private var fetchJob: Job? = null

    /** 마지막으로 주유소를 조회한 지점. 재조회 여부를 판단하는 기준점. */
    private var lastFetchAnchor: Location? = null

    init {
        viewModelScope.launch {
            getUserPreferenceUseCase().collect { prefs ->
                val prevOilType = _state.value.selectedOilType
                val prevRadius = _state.value.searchRadius

                val wasLoaded = _state.value.isPreferencesLoaded

                _state.update {
                    it.copy(
                        selectedOilType = prefs.favoriteOilType,
                        searchRadius = prefs.searchRadius,
                        isPreferencesLoaded = true
                    )
                }

                if (!wasLoaded) {
                    fetchStations()
                } else if (prevOilType != prefs.favoriteOilType || prevRadius != prefs.searchRadius) {
                    fetchStations()
                }
            }
        }
    }

    fun onAction(action: MapAction) {
        when (action) {
            is MapAction.OnResourceTypeSelected -> {
                _state.update { it.copy(selectedOilType = action.oilType) }
                fetchStations(oilType = action.oilType)
            }
            is MapAction.OnStationClick -> {
                viewModelScope.launch {
                    _event.emit(MapEvent.NavigateToStationDetail(action.stationId))
                }
            }
            is MapAction.OnMarkerClick -> {
                _state.update { it.copy(selectedStationId = action.stationId) }
            }
            is MapAction.OnPermissionGranted -> {
                // 권한이 방금 허용되었으므로 위치 스트림을 다시 시작한다.
                // (권한 없이 시작된 스트림은 즉시 종료된 상태다)
                restartLocationUpdates()
                fetchStations()
            }
            is MapAction.OnMapLoaded -> {
                _state.update { it.copy(isMapLoaded = true) }
            }
            is MapAction.OnSettingsClick -> {
                viewModelScope.launch {
                    _event.emit(MapEvent.NavigateToSettings)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return

        locationJob = viewModelScope.launch {
            locationTracker.getLocationUpdates(
                intervalMillis = LOCATION_INTERVAL_MILLIS,
                minUpdateDistanceMeters = MIN_UPDATE_DISTANCE_METERS
            )
                .filterNotNull()
                .collect(::onLocationUpdate)
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun restartLocationUpdates() {
        stopLocationUpdates()
        startLocationUpdates()
    }

    /**
     * 위치가 갱신될 때마다 호출된다.
     *
     * 지도에 표시되는 현재 위치는 매번 갱신하되, 주유소 재조회는 마지막 조회 지점에서
     * REFETCH_DISTANCE_METERS 이상 벗어났을 때만 수행한다. 위치가 조금 흔들릴 때마다
     * 재조회하면 캐시가 매번 빗나가 오히려 API 호출이 늘어나기 때문이다.
     */
    private fun onLocationUpdate(location: Location) {
        _state.update {
            it.copy(currentLocation = LatLng(location.latitude, location.longitude))
        }

        val anchor = lastFetchAnchor
        val shouldRefetch = when {
            // 아직 한 번도 조회하지 못한 경우(예: lastLocation이 없던 기기)에는
            // 스트림이 첫 조회를 맡는다. 설정 로드 전이라면 init 쪽 조회를 기다린다.
            anchor == null -> _state.value.isPreferencesLoaded
            else -> anchor.distanceTo(location) >= REFETCH_DISTANCE_METERS
        }

        if (shouldRefetch) {
            fetchStations(location = location)
        }
    }

    private fun fetchStations(
        oilType: OilType = _state.value.selectedOilType,
        searchRadius: Int = _state.value.searchRadius,
        location: Location? = null
    ) {
        // 늦게 도착한 이전 응답이 최신 결과를 덮어쓰지 않도록 진행 중인 요청을 취소한다.
        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val target = location ?: locationTracker.getCurrentLocation()
            if (target == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            lastFetchAnchor = target
            _state.update {
                it.copy(currentLocation = LatLng(target.latitude, target.longitude))
            }

            val result = getAroundStationsUseCase(
                lat = target.latitude,
                lng = target.longitude,
                radius = searchRadius,
                oilType = oilType,
                sortType = SortType.DISTANCE
            )
            when (result) {
                is Result.Success -> {
                    val lowestPrice = result.data.minOfOrNull { it.price } ?: 0
                    val uiStations = result.data.map { station ->
                        val x = station.x ?: 0.0
                        val y = station.y ?: 0.0
                        val (lat, lng) = LocationUtil.katecToWgs84(x, y)
                        MapStationUiModel(
                            station = station,
                            latLng = LatLng(lat, lng),
                            isLowestPrice = station.price == lowestPrice
                        )
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            stations = uiStations
                        )
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    companion object {
        /** 마지막 구독이 끊긴 뒤 스트림을 유지하는 시간. 화면 회전 시 재시작을 막는다. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** 위치 갱신 요청 주기. */
        private const val LOCATION_INTERVAL_MILLIS = 10_000L

        /** 이 거리 이상 움직였을 때만 위치 콜백을 받는다. (지도 표시 갱신 기준) */
        private const val MIN_UPDATE_DISTANCE_METERS = 100f

        /** 이 거리 이상 움직였을 때만 주유소를 다시 조회한다. (API 호출 기준) */
        private const val REFETCH_DISTANCE_METERS = 500f
    }
}

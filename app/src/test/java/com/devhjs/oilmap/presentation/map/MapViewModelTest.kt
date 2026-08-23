package com.devhjs.oilmap.presentation.map

import android.location.Location
import com.devhjs.oilmap.core.util.LocationUtil
import com.devhjs.oilmap.core.util.Result
import com.devhjs.oilmap.domain.location.LocationTracker
import com.devhjs.oilmap.domain.model.OilType
import com.devhjs.oilmap.domain.model.SortType
import com.devhjs.oilmap.domain.model.UserPreferences
import com.devhjs.oilmap.domain.usecase.GetAroundStationsUseCase
import com.devhjs.oilmap.domain.usecase.GetUserPreferenceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val getAroundStationsUseCase: GetAroundStationsUseCase = mockk()
    private val getUserPreferenceUseCase: GetUserPreferenceUseCase = mockk()
    private val locationTracker: LocationTracker = mockk()

    /** 최초 조회 지점 (설정 로드 시 getCurrentLocation으로 얻는 위치) */
    private lateinit var anchorLocation: Location

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(LocationUtil)

        anchorLocation = mockLocation(lat = 37.498095, lng = 127.027610)

        every { getUserPreferenceUseCase() } returns flowOf(
            UserPreferences(
                favoriteOilType = OilType.GASOLINE,
                defaultSortType = SortType.DISTANCE,
                searchRadius = 3000
            )
        )
        coEvery { locationTracker.getCurrentLocation() } returns anchorLocation
        coEvery {
            getAroundStationsUseCase(any(), any(), any(), any(), any())
        } returns Result.Success(emptyList())
    }

    @After
    fun tearDown() {
        unmockkObject(LocationUtil)
        Dispatchers.resetMain()
    }

    @Test
    fun `위치가_임계거리_미만으로_변하면_주유소를_재조회하지_않는다`() = runTest {
        // Given: 최초 조회 지점에서 100m 떨어진 위치가 스트림으로 들어온다 (임계값 500m 미만)
        val nearbyLocation = mockLocation(lat = 37.4990, lng = 127.0280)
        every { anchorLocation.distanceTo(nearbyLocation) } returns 100f
        every {
            locationTracker.getLocationUpdates(any(), any())
        } returns delayedFlowOf(nearbyLocation)

        // When
        val viewModel = createViewModelWithActiveCollector()
        advanceUntilIdle()

        // Then: 설정 로드 시점의 최초 조회 1회만 발생해야 한다
        coVerify(exactly = 1) {
            getAroundStationsUseCase(any(), any(), any(), any(), any())
        }
        // 재조회는 없었지만 지도에 표시되는 현재 위치는 갱신되어야 한다
        assertEquals(
            "위치 갱신이 State에 반영되지 않았습니다",
            37.4990,
            viewModel.state.value.currentLocation?.latitude ?: 0.0,
            0.0001
        )
    }

    @Test
    fun `위치가_임계거리_이상_벗어나면_주유소를_재조회한다`() = runTest {
        // Given: 최초 조회 지점에서 600m 떨어진 위치가 들어온다 (임계값 500m 이상)
        val farLocation = mockLocation(lat = 37.5040, lng = 127.0330)
        every { anchorLocation.distanceTo(farLocation) } returns 600f
        every {
            locationTracker.getLocationUpdates(any(), any())
        } returns delayedFlowOf(farLocation)

        // When
        createViewModelWithActiveCollector()
        advanceUntilIdle()

        // Then: 최초 조회 + 이동으로 인한 재조회 = 2회
        coVerify(exactly = 2) {
            getAroundStationsUseCase(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `State를_구독하지_않으면_위치_스트림을_시작하지_않는다`() = runTest {
        // Given
        every { locationTracker.getLocationUpdates(any(), any()) } returns emptyFlow()

        // When: State를 수집하지 않고 ViewModel만 생성한다
        MapViewModel(getAroundStationsUseCase, getUserPreferenceUseCase, locationTracker)
        advanceUntilIdle()

        // Then: 화면이 보이지 않는 동안에는 GPS 요청이 나가지 않아야 한다
        io.mockk.verify(exactly = 0) { locationTracker.getLocationUpdates(any(), any()) }
    }

    /**
     * State를 구독해야 위치 스트림이 시작되므로(WhileSubscribed),
     * 화면이 떠 있는 상황을 흉내내기 위해 백그라운드에서 State를 수집한다.
     */
    private fun kotlinx.coroutines.test.TestScope.createViewModelWithActiveCollector(): MapViewModel {
        val viewModel = MapViewModel(
            getAroundStationsUseCase,
            getUserPreferenceUseCase,
            locationTracker
        )
        backgroundScope.launch { viewModel.state.collect { } }
        return viewModel
    }

    /**
     * 설정 로드에 의한 최초 조회가 끝난 뒤 위치가 들어오도록 지연시킨다.
     * (가상 시간이므로 실제로 대기하지 않는다)
     */
    private fun delayedFlowOf(location: Location) = flow {
        delay(1_000)
        emit(location)
    }

    private fun mockLocation(lat: Double, lng: Double): Location =
        mockk<Location>(relaxed = true).also {
            every { it.latitude } returns lat
            every { it.longitude } returns lng
        }
}

# 주유 어디 - 내 주변 최저가 주유소 찾기

> **실시간 주유소 가격 비교 앱** — 내 주변 가장 저렴한 주유소를 한눈에 찾아보세요!

한국석유공사 [오피넷(OPINET)](https://www.opinet.co.kr) API를 활용하여 실시간 유가 정보를 제공하는 Android 애플리케이션입니다.  
사용자의 현재 위치 기반으로 주변 주유소를 검색하고, 가격·거리순 정렬, 지도 표시, 즐겨찾기 등 다양한 기능을 제공합니다.

---

## 📱 주요 기능

| 기능 | 설명 |
|:---|:---|
| **주변 주유소 검색** | GPS 기반 현재 위치에서 반경 1~5km 내 주유소를 실시간 검색 |
| **가격·거리순 정렬** | 가격순 / 거리순으로 주유소 목록 정렬 |
| **유종별 조회** | 휘발유, 경유, LPG 등 유종별 가격 비교 |
| **지도 보기** | Google Maps 기반 주유소 위치 및 가격 마커 표시 |
| **즐겨찾기** | 자주 이용하는 주유소를 즐겨찾기에 저장 (Room DB) |
| **주유소 상세정보** | 유종별 가격, 편의시설(세차장·경정비·편의점), 주소, 연락처 등 상세 정보 확인 |
| **설정** | 선호 유종, 기본 정렬 기준, 검색 반경 설정 (DataStore) |

---

## 📸 스크린샷

| Home | Map | Detail | Favorite |
|:---:|:---:|:---:|:---:|
| <img width="200" alt="image" src="https://github.com/user-attachments/assets/0aaa2651-b9c4-4ca5-bf52-bc90066592e6" />| <img width="200" alt="image" src="https://github.com/user-attachments/assets/62b855eb-8852-4fdb-b833-67757d88ea18" />| <img width="200" alt="image" src="https://github.com/user-attachments/assets/4a77bf9d-f4ae-4908-9b6b-88b1c2e525e2" />| <img width="200" alt="image" src="https://github.com/user-attachments/assets/0a91756c-c7f1-4028-8881-be0b18a9d97c" /> |

---

## 🏗️ 아키텍처

**Clean Architecture + MVI (Model-View-Intent)** 패턴을 적용하여 관심사를 명확히 분리했습니다.

```
┌─────────────────────────────────────────────┐
│              Presentation Layer             │
│  (Compose UI ← State ← ViewModel ← Action) │
├─────────────────────────────────────────────┤
│                Domain Layer                 │
│       (UseCase, Model, Repository IF)       │
├─────────────────────────────────────────────┤
│                 Data Layer                  │
│  (Repository Impl, Remote/Local DataSource) │
└─────────────────────────────────────────────┘
```

- **Presentation**: Jetpack Compose UI + MVI 패턴 (State, Action, Event 분리)
- **Domain**: UseCase 기반 비즈니스 로직, Repository 인터페이스 정의
- **Data**: Retrofit(Remote) + Room(Local) 구현체, DTO → Domain Model 매핑

---

## 🔄 데이터 흐름 & MVI 아키텍처

앱은 MVI(Model-View-Intent)와 Clean Architecture에 기반하여 단방향 데이터 흐름(UDF)을 따릅니다.

```mermaid
graph TD
    %% Presentation Layer
    subgraph Presentation Layer [Presentation Layer]
        View[Compose View] -- User Action (Intent) --> ViewModel[ViewModel]
        ViewModel -- UI State / Effect --> View
    end

    %% Domain Layer
    subgraph Domain Layer [Domain Layer]
        ViewModel -- Execute --> UseCase[UseCase]
        UseCase -- Request Data --> Repository[Repository Interface]
    end

    %% Data Layer
    subgraph Data Layer [Data Layer]
        RepositoryImpl[Repository Implementation] -- Implements --> Repository
        RepositoryImpl -- Query --> LocalDataSource[Local DataSource Room / DataStore]
        RepositoryImpl -- Fetch --> RemoteDataSource[Remote DataSource Retrofit]
    end

    %% Data flow back
    RemoteDataSource -- DTO --> RepositoryImpl
    LocalDataSource -- Entity --> RepositoryImpl
    RepositoryImpl -- Mapping to Domain Model --> UseCase
    UseCase -- Result --> ViewModel
```

---

## 💉 DI (Dependency Injection) 구조

Hilt를 사용하여 종속성 주입을 관리하며, 다음과 같이 3개의 모듈로 나누어 제공합니다.

```mermaid
graph TD
    subgraph Hilt Container [SingletonComponent]
        NetworkModule[NetworkModule] -->|Provides| OpinetService[OpinetService (Retrofit)]
        
        DataModule[DataModule] -->|Provides| StationDatabase[StationDatabase (Room)]
        DataModule -->|Provides| StationDao[StationDao]
        DataModule -->|Provides| DataStore[DataStore (Preferences)]
        DataModule -->|Provides| StationRepository[StationRepository (Dev / Prod 분기)]
        DataModule -->|Provides| UserPreferenceRepository[UserPreferenceRepository]
        
        LocationModule[LocationModule] -->|Provides| LocationTracker[LocationTracker (GPS)]
    end

    StationRepository -->|Inject| UseCases[UseCases]
    LocationTracker -->|Inject| UseCases
    UserPreferenceRepository -->|Inject| UseCases
    UseCases -->|Inject| ViewModels[ViewModels]
```

* **DataModule의 분기 처리:** `BuildConfig.IS_DEV` 환경에 따라 개발 환경(`dev`)에서는 Mock 데이터를 반환하는 `MockStationRepositoryImpl`을 주입하고, 실서비스 환경(`prod`)에서는 `StationRepositoryImpl`을 주입하도록 구성되어 있습니다.

---

## 📦 모듈 구조 (Module Structure)

현재 프로젝트는 단일 Gradle 모듈로 구성되어 있으며, Clean Architecture 계층 구조가 패키지 수준에서 분리되어 관리됩니다.

* **`:app` (단일 모듈)**
  * **`core/`**: DI 설정 (`di`), 라우팅 및 네비게이션 (`navigation`), 유틸리티 (`util`)
  * **`data/`**: 로컬 DB (`local`), 서버 통신 (`remote`), 위치 추적 (`location`), 매퍼 (`mapper`), 데이터 저장소 구현체 (`repository`)
  * **`domain/`**: 위치 인터페이스 (`location`), 도메인 모델 (`model`), 데이터 저장소 인터페이스 (`repository`), 비즈니스 로직 (`usecase`)
  * **`presentation/`**: UI 컴포넌트 (`component`), 디자인 테마 (`designsystem`), 각 화면 (`home`, `map`, `detail`, `favorite`, `settings`) 및 메인 진입점 (`main`)

---

## 🛠️ 기술 스택

| 카테고리 | 기술 |
|:---|:---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material3 |
| **Architecture** | Clean Architecture, MVI |
| **DI** | Hilt (Dagger) |
| **Networking** | Retrofit2, OkHttp, Kotlinx Serialization |
| **Local DB** | Room |
| **Preferences** | DataStore |
| **Map** | Google Maps Compose |
| **Location** | FusedLocationProvider (Play Services) |
| **Coordinate** | Proj4J (WGS84 ↔ KATEC 좌표 변환) |
| **Navigation** | Navigation Compose (Type-Safe) |
| **Ad** | Google AdMob (Banner, Native) |
| **Testing** | JUnit4, MockK, Coroutines Test |
| **Build** | Gradle (Kotlin DSL), Version Catalog |

---

## 📂 프로젝트 구조

```
app/src/main/java/com/devhjs/oilmap/
├── core/
│   ├── di/                     # Hilt 모듈 (Network, DB, Location 등)
│   ├── navigation/             # Navigation 그래프, Route, BottomNavBar
│   └── util/                   # Result wrapper 등 공통 유틸
│
├── data/
│   ├── local/                  # Room DB, DAO, Entity
│   ├── remote/
│   │   ├── api/                # OpinetService (Retrofit 인터페이스)
│   │   └── dto/                # 서버 응답 DTO
│   ├── location/               # LocationTracker 구현체
│   ├── mapper/                 # DTO ↔ Domain 모델 매퍼
│   └── repository/             # Repository 구현체
│
├── domain/
│   ├── location/               # LocationTracker 인터페이스
│   ├── model/                  # Station, StationDetail, OilType, SortType 등
│   ├── repository/             # Repository 인터페이스
│   └── usecase/                # GetAroundStations, GetStationDetail, ToggleFavorite 등
│
├── presentation/
│   ├── component/              # 재사용 UI 컴포넌트 (카드, 헤더, 마커 등)
│   │   └── ad/                 # AdMob 광고 컴포넌트
│   ├── designsystem/           # 컬러, 타이포그래피, 테마
│   ├── home/                   # 홈(리스트) 화면 (MVI)
│   ├── map/                    # 지도 화면 (MVI)
│   ├── detail/                 # 주유소 상세 화면 (MVI)
│   ├── favorite/               # 즐겨찾기 화면 (MVI)
│   ├── settings/               # 설정 화면 (MVI)
│   └── main/                   # MainScreen (Scaffold + BottomNav)
│
├── MainActivity.kt
└── OilMapApplication.kt
```

---

## 🧪 테스트

Domain Layer의 **8개 UseCase**에 대해 **총 15개의 단위 테스트**를 작성하였습니다.  
Repository를 MockK으로 모킹하여 **비즈니스 로직만 독립적으로 검증**하는 전략을 사용합니다.

### 테스트 환경

| 도구 | 용도 |
|:---|:---|
| **JUnit4** | 테스트 프레임워크 |
| **MockK** | Kotlin 전용 Mocking 라이브러리 |
| **Coroutines Test** | `runTest`를 활용한 비동기 코루틴 테스트 |


---

## 📐 Build Variants

| Flavor | 설명 |
|:---|:---|
| `dev` | 개발 환경 (테스트 광고 ID 사용) |
| `prod` | 프로덕션 환경 (실제 광고 ID 사용) |

---

## 📄 API 참고

- [오피넷 유가정보 API 가이드](./GasNow_Opinet_API_Guide.md)
- [오피넷 API 요약 정리](./opinet_api_summary.md)

> **참고:** 오피넷 무료 API 일일 호출 제한은 **1,500건**이며, 좌표계는 KATEC 방식을 사용합니다.  
> 앱 내에서 Proj4J를 통해 WGS84(GPS) ↔ KATEC 좌표 변환을 자동 처리합니다.


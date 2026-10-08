# BusyBirds

Оператор бизнес-авиации (`World25.BusyBirdsOperatorId`). Игрок (пилот-человек, `CharacterMode.PC`) берёт
«миссии» — это журнеи (`Journeys.Journey`) пассажиров, которые хотят премиальный сервис — и сам их выполняет
на парке самолётов BusyBirds.

## Компоненты

| Компонент | Файл | Роль |
|---|---|---|
| `BusyBirdsController` | `app/user/BusyBirdsController.java` | REST API `/busy-birds`, DTO, оркестрация бронирования |
| `BusyBirdsMissionControl` | `world/processors/BusyBirdsMissionControl.java` | Бизнес-логика: список миссий, оплата, построение планов |
| `BusyBirdsMissions` | `world/datamodel/BusyBirdsMissions.java` | **Заготовка** хранилища миссий (см. «Состояние / TODO») |

`world.busyBirdsMissionControl()` — доступ к логике из `World`.

## REST API (`/busy-birds`, `@CrossOrigin`, `userId` из request attribute)

Чтение идёт через `worldBean.read(...)`, запись — через `worldBean.modifySync(...)`.
Во всех методах первым вызывается `checkUserHasAccessToBusyBirds(userId)` (сейчас пустая заглушка).

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/mission/to-book` | Список доступных миссий, сортировка по городу вылета, затем прилёта |
| GET | `/aircraft/available` | Свободные самолёты BusyBirds: idle + припаркованы в аэропорту |
| GET | `/mission/build-plans?missionId&aircraftId&turnaroundTime=1&ferryBackToBase=true` | Варианты планов выполнения миссии |
| PUT | `/mission/book?...&planId` | Бронирование выбранного плана |

| PUT | `/ferry/book?aircraftId&destinationIcao` | Бронирование ferry-рейса (см. ниже) |

### Ferry-рейс (`PUT /ferry/book`)
Одиночный перегон самолёта без пассажиров: из текущего аэропорта самолёта в аэропорт `destinationIcao`.
- Плановый вылет = мировое время + 30 минут (без выравнивания на 5 минут).
- Создаётся только диспетчированная лётная миссия (`scheduleDispatchedMission`) с `CharacterMode.PC` и `userId`;
  `TransportFlight` и журней не затрагиваются.
- Валидация (ответ `failure` + сообщение): самолёт не припаркован / не idle, аэропорт не найден,
  самолёт уже в аэропорту назначения. Принадлежность самолёта BusyBirds не проверяется.

ID наружу кодируются `Id.encode/decode`. `missionId` == id журнея.

## Бизнес-логика

### Что считается миссией
Журней в статусе `LookingForTickets` с `preferredCabinService` = `F` или `J`.
Отдельной сущности миссии пока нет — `Mission` это вычисляемая обёртка над журнеем
(`journey`, `validTill` (пока всегда null), `distance`, `pay`).

### Расстояние и оплата
- `distance` = расстояние по прямой между координатами городов (nm, int).
- `pay = ((distance / 400) * 7000 + 2000) * (1 + d(fromCity)*0.01) * (1 + d(toCity)*0.01) * (1 + d(journey)*0.01)`,
  где `d(x)` = последняя цифра id (`Tools.lastDigit`). Т.е. база 2000 + 7000 за каждые 400 nm,
  плюс детерминированный «шум» до +9% по каждому из трёх множителей (стабилен между запросами, не random).

### Выбор аэропортов города (`listAirports`)
Для города берутся неисключённые (`!isExcluded`) аэропорты. Приоритет:
1. Базовый аэропорт BusyBirds (`BaseAirport` для оператора) — возвращается один он;
2. Бизнес-терминалы (`BusinessAviationTerminal`) этого оператора;
3. Любые бизнес-терминалы;
4. Все аэропорты города.

### Построение планов (`buildPlans`)
Для каждой пары (аэропорт вылета × аэропорт прилёта) строится свой план; планы сортируются по
суммарной дистанции (кратчайший первым). Параметры: `turnaroundTime` (часы между плечами), `ferryBackToBase`.

Структура плана (`_buildPlan`):
1. **Reposition** из текущего аэропорта самолёта в аэропорт вылета — если самолёт стоит не там.
2. **Revenue** — рейс с пассажирами (`pax = journey.groupSize`).
3. **Reposition** в ближайший базовый аэропорт BusyBirds (ближайший к аэропорту прилёта) — если
   прилёт не в базе И `ferryBackToBase = true`.

Время: первый вылет = `worldTime + 1 час`, каждое следующее плечо = прилёт предыдущего + turnaround.
Время вылета выравнивается на 5 минут (`Time.alignTo5mins`), длительность — `SimpleFlight` по
`AircraftPerformanceData` типа ВС. ID плана = `"<ICAO from>-<ICAO to>"`.

### Бронирование (`PUT /mission/book`)
План **пересчитывается заново** и ищется по `planId` (серверного хранения планов нет — план stateless).
Ошибки возвращаются как `BookMissionResponse("failure", messages)` (не исключением): план не найден
или план в статусе Failure.

Для каждого плеча:
1. `FlightMissionHelper.scheduleDispatchedMission(...)` — создаётся диспетчированная лётная миссия
   и событие `PilotOnDuty`; на миссии ставится `CharacterMode.PC` и `userId`.
2. Если плечо `Revenue`: создаётся `TransportFlight`, на него выкупаются билеты на всю группу
   в классе `J`, у журнея выставляются `transportFlight1Id` и `bookedCabinService = J`,
   журней переводится в `waitForCheckin`.

Допущение: все самолёты BusyBirds имеют только J-компоновку салона.

## Известные ограничения / TODO (из кода)

- `checkUserHasAccessToBusyBirds` — заглушка, доступ не проверяется.
- `buildPlans` собирает `messages` о проблемах (самолёт не idle/не припаркован, нет аэропортов), но
  `_buildPlan` их не использует: ни одного плана со статусом `Failure` сейчас не создаётся.
  Следствие: бронирование проверяет только `planId`, **состояние самолёта на бронировании не валидируется**
  (на практике защищает только то, что список `/aircraft/available` показывает idle-самолёты).
  Если аэропортов нет, циклы пусты и вернётся пустой список планов.
- Не учитывается максимальная дальность ВС и вместимость кресел vs размер группы
  (`todo ak2` в `BusyBirdsMissionControl`).
- `Mission.validTill` всегда null — срока жизни миссии нет.
- `pay` только показывается, фактическое начисление в `bookMission` отсутствует.
- Несколько журнеев/миссий можно забронировать на один самолёт подряд без проверки пересечений
  (после первой брони статус самолёта зависит от обработки лётной миссии).
- Неиспользуемый код: `chooseAirport` (дублирует `listAirports` с random) и `findNearestSuitableAircraft`.
- `Geo.distance` — прямая; `totalDistance` плана включает reposition-плечи.
- `BusyBirdsMissions` (datamodel): скопирован из фасилити (поля `airportId/aircraftOperatorId/type`,
  enum `Type`, методы `hasFacility`, `by`), нигде не используется. В комментариях задуманная схема
  персистентных миссий: status, journeyId, pilotId, aircraftId, createdAt, assignedAt, до трёх пар
  flightMission+transportFlight. Это направление развития: хранить миссии вместо вычисления на лету.
- В `bookMission` вся цепочка внутри `modifySync` не атомарна относительно ошибок посередине
  (исключение на 2-м плече оставит созданным 1-е).

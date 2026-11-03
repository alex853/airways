# Playbook: как добавлять типы самолётов в мир

Аналог [city-airport-playbook.md](city-airport-playbook.md) для типов самолётов. Первый прогон: 2026-11-02 (AT76, BCS1, BCS3).

## 1. Откуда берутся задачи

`GET /admin/flight-stats` (счётчики за текущий период; блок «aircraft type …» в `admin-requests.http`):

| Ключ счётчика | Что значит |
|---|---|
| `aircraft type missing XXXX` | в мире нет типа `XXXX`; рейс подменяется на A320 (`PilotContext.mission_dispatchNewAndStart`) |
| `aircraft type performance missing XXXX` | в библиотеке `refdata-aircrafts` нет данных производительности для типа |
| `aircraft type performance data missing XXXX.<поле>` | данные для типа есть, но не заполнено поле (`takeoffSpeed`, `landingSpeed`, `climbVerticalSpeed`, `descentVerticalSpeed`); берётся дефолт 140/130 узлов, 2000/1500 фт/мин (`AircraftPerformanceData`) |
| `aircraft type remapped XXXX` | код из плана подменён на близкий (`AircraftTypeRemapping`) |

Сначала смотрим самые частые: это очередь на добавление. Одиночные редкие типы (вертолёты, военные, экзотика вроде A225, DC6, F35, U2) обычно не добавляем.

## 2. Три места, где живёт тип

1. **Мир:** `World25_008_create_aircraft_types` (`aircraftTypes.create(ICAO, IATA)`). Без этого тип в рейсах подменяется на A320.
2. **Макет салона:** `src/main/resources/cabin-layouts.properties` (`ICAO=J12/Y120`, классы F/J/W/Y). Читается при старте (`TransportFlightControl.loadCabinLayouts`), без строки у типа нет мест для пассажиров.
3. **Производительность:** репозиторий `refdata-aircrafts` (отдельный, `C:\Dev\sf\refdata-aircrafts`): `src/main/resources/eurocontrol/<ICAO>.json` (V2, посадка, набор до FL240, снижение до FL100, крейсер, потолок, дальность); файл в `doc8643/` даёт только крейсер, потолок и дальность и для скоростей взлёта и посадки не годится.

Зависимость в airways: `net.simforge:refdata-aircrafts:latest.release`, отдельно версию в airways поднимать не нужно, она подтягивается при сборке (локально: `./gradlew ... --refresh-dependencies`).

## 3. Подводные камни

- Если данные производительности для близкого типа есть, не выдумываем свои, а берём его значения (так сделан AT76 по AT72: V2 110, посадка 120, набор 1000, снижение 1500, крейсер 275, потолок FL250, дальность 1500). Дальность и потолок точно не проверялись.
- `AircraftTypeRemapping` (A32N→A20N, A330→A333 и т.д.) подходит, когда отдельный тип не нужен, но подмена делает рейс другим типом: отдельного AT76 в рейсах не будет.
- Файлы `World25_008`, `cabin-layouts.properties` в CRLF: править скриптом с `newline=''` или Edit.
- Число мест в макете салона можно ставить прикидкой (BCS1 `J12/Y96`, BCS3 `J12/Y120`), уточняется позже.
- Импорт идемпотентный (`byIcao(...).orElseGet(create)`), повторный прогон ничего не ломает, но лишний запуск при каждом старте не нужен.

## 4. Технический порядок работы (шаги)

1. **Разбор:** `flight-stats`, выбрать частые отсутствующие типы и дыры в данных; проверить, есть ли данные в `refdata-aircrafts` (`eurocontrol/<ICAO>.json`) и тип в `World25_008`.
2. **Правки** (после согласия):
   - `refdata-aircrafts`: новый или дополненный `eurocontrol/<ICAO>.json`; релиз библиотеки (версия поднимается, например 1.5.3 → 1.5.5; собирает пользователь).
   - `World25_008_create_aircraft_types`: строка `aircraftTypes.byIcao("XXXX").orElseGet(() -> aircraftTypes.create("XXXX", "iata"))`.
   - `cabin-layouts.properties`: макет салона.
   - `Application.main`: **раскомментировать** `World25_008_create_aircraft_types.main(args)` на время деплоя (импорты городов `009`, `012`, `013` в это время закомментированы).
3. **Проверка:** `./gradlew test -q --refresh-dependencies` (все тесты проходят, новая версия `refdata-aircrafts` подтянута).
4. **Коммит и пуш** (пуш перевыкатывает прод и запускает импорт при старте).
5. **После деплоя:** в `GET /admin/flight-stats` должны исчезнуть/перестать расти `aircraft type missing …` и `performance data missing …` для добавленных типов; в логах `/admin/log/search?q=BCS3` появляются `Fleet - creating a/c …, BCS3` (флот ShadowJet создаёт самолёты нового типа).
6. **Закомментировать** `World25_008` обратно и закоммитить **без пуша** (пуш передеплоил бы сервер зря).

## 5. Журнал

- **2026-11-02:** `AT76` (ATR 72-600): 9444 событий `performance data missing AT76.takeoffSpeed` за период, данных `eurocontrol/AT76.json` не было; добавлен по значениям AT72, релиз `refdata-aircrafts` 1.5.5. `BCS1` (A220-100, IATA 221) и `BCS3` (A220-300, IATA 223) добавлены в `008`, макеты `J12/Y96` и `J12/Y120`; данные производительности в библиотеке уже были. Остальной хвост (`DA62` с пустыми полями, `FA8X`, `PC12`, `FA50`, `B463`, `H60`, `TBM8`, `C919`, `PC24`, `G2` и далее) пока не трогали.

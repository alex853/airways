# Airports: расширение записи аэропорта (план, не реализовано)

Статус: **только план**, в код не вносилось (решение: не трогаем сейчас). Основание: несколько TODO в `Airports.java` и
`docs/cities.md` требуют новых полей в `Airports.Airport`.

## Что требует новых полей

| TODO | Где | Что нужно |
|---|---|---|
| «add `excluded`, countryId, and some reserve space» | `Airports.java` (todo ak1) | `countryId`, признак исключения, резерв |
| «rework `isExcluded` into `exclusion` field, or status» | `Airports.Airport.isExcluded` (todo ak1) | сейчас захардкожены LFPY, EDDT, LGAT, CGAS; читают `BusyBirdsMissionControl` и `RandomFlightMissionGenerator` |
| TODO №4 «Страна аэропорта» | `docs/cities.md` | страна считается на лету по `airport2city` (`GeoController`, кэш + `invalidateAirportCountries`) |
| TODO №1 «Военные аэродромы с типом `civil`» | `docs/cities.md` | признак «военный» (EGDM, KINS, KNZJ, KNTD, KSLI, KPMD, KNKX, KNHK, KAPG, KFMH, RKSM, RKSW и др.) |
| `ImportMajorAirportsInVicinityOfCities` (todo ak3) | IATA сейчас `null` | не схема, а заполнение IATA из refdata |

## Предлагаемая структура записи

Сейчас запись 20 байт (1 заголовок + latitude 3, longitude 3, iata 4, icao 5, nameId 4). По образцу `Journeys`
выровнять до 32 байт:

- `countryId` — `Unsigned8bit` (как `Cities.countryId`: ссылка на страну 1 байтом, до 255 стран);
- `exclusion` — `Unsigned8bit`, перечисление: `NONE(0)`, `EXCLUDED(1)`, `MILITARY(2)`, `CLOSED(3)`, остальные значения свободны
  (альтернатива — набор отдельных битовых флагов);
- резерв 10 байт: `Signed32bit` + `Signed32bit` + `Unsigned16bit`. Кандидат на первое использование — `size` из
  `icaodata.csv` (`Unsigned16bit`, длина полосы), чтобы фильтры по размеру не зависели от CSV.

## Главный риск: смена размера записи

`Storage.loadIfExists` читает файл как есть и делит на размер записи, поэтому новая раскладка на старых данных даст
мусор. Миграций в проекте нет (коммит «migration removed» — это был стартовый шаг world builder). Варианты:

- **А (рекомендуется):** новая раскладка пишется в новый файл (`name("airports2")` или версия в имени); при отсутствии
  нового файла, но наличии старого `airports`, `Airports.loadIfExists` читает старый файл **старой раскладкой**
  (приватный legacy `Storage`) и копирует запись за записью (id совпадают с позицией, удалений у аэропортов нет). Старый
  файл остаётся как бэкап. Автоматически, без отдельного запуска.
- Б: определять формат по длине файла — отвергнуто, неоднозначно (длина кратна и 20, и 32 при 160 байтах).
- В: офлайн-инструмент или шаг world builder до запуска — надёжно, но руками и с остановкой.

Откат на старую версию после перехода на новый файл потеряет изменения аэропортов с момента перехода (они редки);
старый файл останется как бэкап.

## Заполнение новых полей

- `countryId` — после загрузки мира (нужны города и связи) идемпотентный шаг «заполнить нулевые»: страна самого
  населённого города из `airport2city`, то есть то же правило, что сейчас в `GeoController`. Для новых аэропортов
  (`World25_009`, `ImportMajorAirportsInVicinityOfCities`) проставлять при создании или при `create-links`.
- `exclusion` — миграцией: LFPY, EDDT, LGAT, CGAS → `EXCLUDED`, известные военные из `docs/cities.md` → `MILITARY`;
  дальше админ-ручка `set-exclusion?icao=&value=&dryRun=true` (по умолчанию `dryRun=true`).
- `isExcluded()` оставить как `exclusion != NONE` (или раздельные `isExcluded`/`isMilitary`), хардкод убрать.
- IATA — отдельный шаг из refdata.

## Что переключить после появления полей

- `GeoController.getAirports`: страна из поля, добавить `countryId` в `AirportDto` (как в `CityDto`), имя страны оставить
  для совместимости; убрать пересчёт `buildCountryIdByAirportId` (кэш и `invalidateAirportCountries()` уже убраны).
- `RandomFlightMissionGenerator`, `BusyBirdsMissionControl` — на новый признак; возможно, исключать военные при
  автоимпорте и `create-links`.
- Закрыть TODO №4 и №1 в `docs/cities.md`.

## Тесты

`AirportsTest` (создание, диапазон `countryId`, `exclusion`), тест миграции (старый файл → новый, id и поля сохранены,
повторная загрузка), заполнение `countryId`, `World.load` со старым файлом, обновление существующих тестов
`GeoController`/ShadowJet, где создаются аэропорты (около 36 мест вызова `airports().create`, нужен перегруженный
`create` без новых аргументов).

## Порядок работ

1. Раскладка + миграция + тесты → деплой, проверка `/geo/airports` и логов загрузки.
2. Заполнение `countryId` и `exclusion` (шаг при загрузке + админ-ручка).
3. Переключение `GeoController`/`AirportDto`, удаление кэша.
4. Документация и удаление старого файла `airports` (через неделю стабильной работы).

## Открытые вопросы

1. `exclusion` как одно перечисление (EXCLUDED/MILITARY/CLOSED) или набор отдельных флагов?
2. Военные аэродромы: только пометка, или ещё исключать из автоимпорта и создания рейсов?
3. Добавить `size` в резерв сразу?
4. Новый файл с версией в имени (вариант А) или офлайн-миграция?

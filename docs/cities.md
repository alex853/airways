# Города и население

Идея проекта — симуляция пассажиропотока между городами (в первую очередь для авиакомпаний).
Поэтому «город» в мире — это не административная единица, а зона притяжения аэропорта.

## Источники данных

- `src/main/resources/city-population.csv` — исходный список городов и населения. Во многом устарел.
- `src/main/resources/icaodata.csv` — аэропорты (в т.ч. колонка `city`).
- `ImportCities` — создаёт города из CSV. Уже существующие города (страна + имя) **пропускает**, население не обновляет.
- `World25_012_selected_cities` — «классический» список городов для импорта (`city-code` + `city-name`), после него
  `ImportMajorAirportsInVicinityOfCities` импортирует аэропорты в окрестностях и проставляет airport2city-линки.

## Единицы измерения

Все расстояния в мире и в коде (`Geo.distance`, `maxDistance` в админке, пороги в импортах) — в **морских милях (nm)**.
При ручном анализе окрестностей города считать в nm (1 nm = 1,852 км).

## Соглашение `del`

Пригород/район, вошедший в население соседнего большого города, помечается в CSV префиксом `del ` в имени
(например `del Ecatepec`). Такие записи игнорируются (в т.ч. в `/admin/airport/missing-cities`).

## Операция «актуализировать и укрупнить мажорный сити»

0. При анализе города выяснить, какие аэропорты в нём используются для бизнес-авиации (помимо основного
   пассажирского) — например, Las Vegas: KLAS основной, KHND как «разгрузочный». Такие аэропорты
   рассмотреть для добавления в `World25_009_create_some_airports` (мелкие надо линковать вручную, см. шаг 6).
1. Проверить/поправить название города в CSV (бывают кривые).
2. Поставить в CSV актуальное суммарное население агломерации (хвост — последние цифры исходного числа,
   например Austin 675600 → 2475600, Mexico 8705100 → 21705100).
3. Всем городам-пригородам, включённым в эту цифру, добавить `del ` в имя.
4. Города мельче 100 тыс. игнорируем — их не трогаем.
5. Добавить город в `World25_012_selected_cities`.
6. Аэропорты и линки подтянутся автоматически после импорта городов — но только для гражданских аэропортов с
   `size >= 5000` в `icaodata.csv` в радиусе 50 nm от города (`ImportMajorAirportsInVicinityOfCities`).
   Мелкие аэропорты, добавленные через `World25_009_create_some_airports` (`ImportSelectedAirports`),
   создаются **без** airport2city-линков — их надо привязывать вручную:
   `/admin/airport/create-links?icao=...&dryRun=true`.
7. Проверить результат (`/geo/cities`, `/admin/airport/missing-cities`, `/admin/airport/create-links?dryRun=true`).

Для города, который **уже есть в мире**, импорт население не обновит — нужно выставить его вручную
(разовый метод вроде `/admin/f1-tour-fixes` с `city.setPopulation(...)`).

## Операция «добавить аэропорт из списка отсутствующих (VATSIM)»

Источник: `GET /admin/flight-stats/vatsim-top-missing-airports` — ICAO аэропортов, которых нет в мире, и сколько раз
они упоминались в VATSIM (по убыванию). Берём по одному, сверху вниз:

1. Взять самый верхний аэропорт, убедиться, что его **действительно нет в мире** (`/geo/airports`) — список может
   отставать от мира (после деплоя в нём ещё были MMMX и SBGR, хотя они уже созданы).
2. Найти ближайший/наибольший город в окрестностях (в nm): сначала в мире (`/geo/cities`), потом в CSV.
3. **Город уже есть в мире** — только создать аэропорт (`World25_009_create_some_airports`) и привязать к городу
   (`create-links?icao=...`, мелкие `size < 5000` автоматически не линкуются).
4. **Города в мире нет** — отдельное решение пользователя, добавляем ли город. Если да — дальше обычная
   операция «актуализировать и укрупнить мажорный сити» (анализ окрестностей, население, `del`,
   `World25_012_selected_cities`), аэропорт идёт в `World25_009`.
5. Проверить результат и идти к следующему аэропорту списка.

## Ситуации и что делать в каждой

| Ситуация | Пример | CSV | `World25_012` | `World25_009` | `f1-tour-fixes` | Вручную после деплоя |
|---|---|---|---|---|---|---|
| Город в мире, пригородов в CSV нет | Austin | население | уже есть | — | `setPopulation` | `f1-tour-fixes` |
| Города нет в мире, страна есть | Mexico City | население + `del` | добавить | — | — | проверка |
| Нет ни страны, ни города, ни аэропортов | Sao Paulo | имя + население + `del` | добавить | — | — | проверка (страна создаётся импортом) |
| Город в мире, пригороды слиты, есть бизнес-аэропорт | Las Vegas | население + `del` | уже есть | мелкий аэропорт (KHND) | `setPopulation` | `f1-tour-fixes`, `create-links`, attraction/mobility |
| Мир уже правильный, CSV отстал | San Francisco | население как в мире + `del` | — | — | — | — (мир не трогаем) |
| Город в мире с заниженным населением, нужны вторичные аэропорты | Los Angeles | население + `del` | уже есть | вторичные (KBUR, KSNA) | `setPopulation` | `f1-tour-fixes`, `create-links` |

Общие правила: импорт существующие города не обновляет; аэропорты `size < 5000` не линкуются автоматически;
расстояния в nm; пригороды ≥100 тыс. → `del`, мельче 100 тыс. не трогаем; соседние самостоятельные рынки
(Campinas, Santos, Toluca, Inland Empire) остаются отдельными.

## Выполнено

- **Austin (US):** 2 475 600. CSV обновлён, население в мире выставляется через `/admin/f1-tour-fixes`.
  Пригородов в CSV нет.
- **Mexico City (MX, в CSV `Mexico`):** 21 705 100. 17 пригородов ≥100 тыс. помечены `del`
  (Ecatepec, Nezahualcoyotl, Naucalpan, Tlalnepantla, Chimalhuacan, Lopez mateos, Cuautitlan izcalli, Xico,
  Coacalco, Ixtapaluca, Los reyes, Nicolas romero, Buenavista, San pablo de las salinas, Chalco, Huixquilucan,
  Texcoco). Добавлен в `World25_012_selected_cities`. Toluca (MMTO) остаётся отдельным городом.
- **Las Vegas (US):** 2 327 900 (агломерация Las Vegas–Henderson–Paradise). Город уже был в мире, население
  выставляется через `/admin/f1-tour-fixes`. Пригороды `del`: Paradise, Henderson, Sunrise manor, North las vegas,
  Spring valley. Аэропорт KLAS; KINS (Indian Springs AF Aux) привязан ошибочно — игнорируем. KHND (Henderson Executive,
  бизнес-авиация, size 1524) добавлен в `World25_009_create_some_airports`; линк к Las Vegas нужно создать вручную
  через `/admin/airport/create-links?icao=KHND`. KVGT в мир не добавляем (возможно позже).
- **San Francisco (US):** в мире уже 7 650 000 (Bay Area); CSV приведён к этому значению (было 746 900). 12 городов
  ≥100 тыс. помечены `del` (San jose, Oakland, Fremont, Hayward, Sunnyvale, Concord, Vallejo, Santa clara,
  Richmond, Antioch, Berkeley, Daly city). Мир не менялся. Аэропорты KSFO, KOAK, KSJC уже в мире.
- **Los Angeles (US):** 12 847 400 (агломерация LA–Long Beach–Anaheim). Город уже был в мире, население
  выставляется через `/admin/f1-tour-fixes`. 20 пригородов ≥100 тыс. ближнего круга помечены `del` (East los
  angeles, Glendale, Inglewood, Pasadena, Downey, Burbank, El monte, Norwalk, Torrance, Long beach, West covina,
  Fullerton, Garden grove, Anaheim, Pomona, Huntington beach, Orange, Santa clarita, Santa ana, Costa mesa).
  Дальние (Ontario, Irvine, Riverside, Palmdale и др.) не трогаем.
- **Abu Dhabi (AE):** 1 560 400 (город ~1,5 млн). Пригородов в CSV нет. Город уже в мире, население через
  `/admin/f1-tour-fixes`. Аэропорт OMAA; OMAD (Bateen, бизнес-авиация) добавлен в `World25_009_create_some_airports`,
  линк вручную `create-links?icao=OMAD`.
- **Dubai (AE):** 5 940 600 (Dubai + Sharjah + Ajman). Город уже в мире, население через `/admin/f1-tour-fixes`.
  `del As-sariqah` (Sharjah), `del #ajman`. Ras al-Khaimah, Fujairah, Al Ain остаются отдельными. Аэропорт OMDB;
  OMSJ (Sharjah Intl) добавлен в `World25_009_create_some_airports`, линк вручную `create-links?icao=OMSJ`.
- **Sydney (AU, аэропорт YSSY):** CSV 4 277 200 → 6 077 200 (Greater Sydney ~5,3 млн + Wollongong + Newcastle, ~6,1 млн).
  `del Wollongong` (262 500, ~32 nm) и `del Newcastle` (502 300, ~68 nm) — слиты в Sydney по решению пользователя.
  Страны Australia в мире не было — создаётся импортом. Добавлен в `World25_012_selected_cities`; YSSY (size 8938 ≥5000)
  подтянется и залинкуется автоматически через `ImportMajorAirportsInVicinityOfCities`. Аэропорты YWOL (Wollongong)
  и YWLM (Newcastle/Williamtown), а также YSBK (Bankstown) в мир не добавляем.
- **Bristol (GB, аэропорт EGGD):** CSV 417 600 → 717 600 (агломерация ~700 тыс.). `del` не нужен (пригороды <100 тыс.).
  Города в мире не было (ближайший Birmingham, 72 nm) — добавлен в `World25_012_selected_cities`. EGGD (size 2000 <5000)
  добавлен в `World25_009_create_some_airports`; линк вручную `create-links?icao=EGGD` (~6 nm от центра). Cardiff
  (285 300, ~18 nm), Newport (117 200, ~16 nm) — отдельные города со своими аэропортами; создать позже и
  при необходимости линковать друг на друга (в этот заход не делаем).
- **Doha (QA):** CSV `Ad-dawhah` → `Doha`, население 2 328 500 (Greater Doha). `del Ar-rayyan` (единственный ≥100 тыс.).
  Страны Qatar в мире не было — создаётся импортом. Добавлен в `World25_012_selected_cities`. Аэропорт OTHH
  (уже в мире, не залинкован) — линк вручную `create-links?icao=OTHH`. OTBD (закрыт для коммерческих рейсов) не трогаем.
- **Sao Paulo (BR):** переименован `Sao paulo` → `Sao Paulo`, население 22 260 100. 20 пригородов ≥100 тыс. в радиусе
  ~24 nm (45 км) помечены `del` (Guarulhos, Sao bernardo do campo, Osasco, Santo andre, Diadema, Maua, Carapicuiba,
  Moji das cruzes, Itaquaquecetuba, Suzano, Barueri, Embu, Taboao da serra, Itapevi, Cotia, Ferraz de vasconcelos,
  Francisco morato, Itapecerica da serra, Sao caetano do sul, Ribeirao pires). Cubatao, Jundiai, Santos и порт
  Сантос (Sao vicente, Praia grande, Guaruja), Campinas остаются отдельными городами. Добавлен в
  `World25_012_selected_cities`. Аэропорты — SBGR, SBSP (SBKP — Campinas).

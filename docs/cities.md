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
   **Не забыть:** вызовы `World25_009_create_some_airports.main(args)` и `World25_012_selected_cities.main(args)`
   в `Application.main` по умолчанию **закомментированы** — перед деплоем их нужно раскомментировать, а после
   того как импорт отработал, закомментировать обратно (иначе импорты гоняются при каждом старте).
6. Аэропорты и линки подтянутся автоматически после импорта городов — но только для гражданских аэропортов с
   `size >= 5000` в `icaodata.csv` в радиусе 50 nm от города (`ImportMajorAirportsInVicinityOfCities`).
   Мелкие аэропорты, добавленные через `World25_009_create_some_airports` (`ImportSelectedAirports`),
   создаются **без** airport2city-линков — их надо привязывать вручную:
   `/admin/airport/create-links?icao=...` (по умолчанию `dryRun=true`, ничего не меняет; для реального создания
   линков нужно передать `dryRun=false`).
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

## TODO / известные проблемы

### 1. Военные аэродромы с типом `civil` в `icaodata.csv`

Автоимпорт (`ImportMajorAirportsInVicinityOfCities`: `civil`, `size >= 5000`, радиус 50 nm) тащит их в мир и
привязывает к городам, хотя регулярных рейсов там нет. Определять по названию ненадёжно (EGDM «Boscombe Down» не
содержит маркеров), чинить точечно.

**Уже в мире и привязаны к городам:**

| ICAO | Название | Привязан к | Комментарий |
|---|---|---|---|
| EGDM | Boscombe Down | Bristol | военная база |
| KINS | Indian Springs AF Aux | Las Vegas | военная авиабаза |
| KNZJ | El Toro MCAS | Los Angeles | бывшая база, рейсов нет (size 12170) |
| KNTD | Point Mugu NAS | Los Angeles | военно-морская база |
| KSLI | Los Alamitos AAF | Los Angeles | армейский аэродром |
| KPMD | Palmdale AF Plant 42 | Los Angeles | военный завод/испытания |
| KNKX | Miramar MCAS | San Diego, Tijuana | военная база морской пехоты |
| KNHK | Patuxent River NAS | Washington | военно-морская испытательная база |
| KAPG | Phillips AAF | Baltimore | армейский аэродром |
| KFMH | Otis ANGB | Boston | база нацгвардии |
| KNUQ | Moffett Federal AFLD | San Francisco | NASA/бизнес-джеты — не обязательно плохой |
| LIRN | Capodichino Mil | — (без города) | военная часть аэропорта Неаполя |
| KFTG | Front Range | Aurora, Denver | гражданский GA, ложное срабатывание |

**План:** военные удаляем из мира и при необходимости заменяем на реальные гражданские аэродромы города.
Перед удалением **проанализировать использование** (самолёты, расположенные там; миссии/рейсы, где аэродром
вылета/назначения; airport2city-линки; facilities) — убедиться, что удаление не опасно. Отдельного эндпоинта
удаления аэропорта/линка в админке нет. Одновременно поправить `type` на `military` в `icaodata.csv`.

**Ловушки на будущее** (`civil`, `size >= 5000`, ещё не в мире, импортируются при появлении города рядом):
KNTU Oceana NAS, KYUM Yuma MCAS/Intl (смешанный), KNKT Cherry Point MCAS, KNJK El Centro NAF, KNFL Fallon NAS,
KNQI Kingsville NAS, KNBC Beaufort MCAS, KNLC Lemoore NAS, KNQX Key West NAS (смешанный), KNPA Pensacola NAS,
KNGP Corpus Christi NAS, KNMM Meridian NAS, KNHZ Brunswick NAS, KFHU Sierra Vista (смешанный), KFTK Godman AAF,
KGTB Wheeler-Sack AAF, PABI Allen AAF, KNFE Fentress NALF, LIBV Gioia Del Colle Mil, LIED Decimomannu Mil,
NZWP Whenuapai (Military), YPEA Pearce (Military), SAZB Comandante Espora NAS, FZSA Kamina Base и др.

### 2. Аэропорты в мире без привязки к городу (отдельная категория)

Созданы через `World25_009_create_some_airports`, но ни один город рядом в мир не импортирован, поэтому в симуляции
пассажиропотока не участвуют: EFKS, EGJJ, ENBR, EVRA, LDDU, LDSP, LEAL, LFKF, LGIR, LGKO, LGKR, LGRP, LGSK, LGSM,
LICJ, LIRN, LPFR, LPMA, LTAI.

**План:** для каждого проанализировать, какой город/города рядом нужны, и пройти обычную процедуру
«актуализировать и укрупнить мажорный сити» (импорт города → линк аэропорта). Займёмся отдельно.

**Активность** (по `/geo/airport/{icao}/details`; sum — сумма рейсов/сутки трёх крупнейших направлений, нижняя оценка):

| ICAO | Name | Sum |
|---|---|---|
| LPMA | Madeira | 130 |
| ENBR | Bergen Flesland | 104 |
| EGJJ | Jersey | 83 |
| LPFR | Faro | 73 |
| EVRA | Riga Intl | 70 |
| LGKR | Ioannis Kapodistrias (Corfu) | 62 |
| LGIR | Nikos Kazantzakis (Heraklion) | 62 |
| LTAI | Antalya | 46 |
| LGSK | Alexandros Papadiamandis (Skiathos) | 44 |
| LICJ | Punta Raisi (Palermo) | 39 |
| LIRN | Capodichino Mil (Naples) | 36 |
| LGRP | Diagoras (Rhodes) | 30 |
| LDDU | Cilipi (Dubrovnik) | 26 |
| LDSP | Kastela (Split) | 24 |
| LEAL | Alicante | 23 |
| LGKO | Ippokratis (Kos) | 16 |
| LGSM | Samos | 11 |
| EFKS | Kuusamo | 9 |
| LFKF | Figari/Sud Corse | 8 |

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
  бизнес-авиация, size 1524) добавлен в `World25_009_create_some_airports` и привязан к Las Vegas. KVGT в мир не добавляем (возможно позже).
- **San Francisco (US):** в мире уже 7 650 000 (Bay Area); CSV приведён к этому значению (было 746 900). 12 городов
  ≥100 тыс. помечены `del` (San jose, Oakland, Fremont, Hayward, Sunnyvale, Concord, Vallejo, Santa clara,
  Richmond, Antioch, Berkeley, Daly city). Мир не менялся. Аэропорты KSFO, KOAK, KSJC уже в мире.
- **Los Angeles (US):** 12 847 400 (агломерация LA–Long Beach–Anaheim). Город уже был в мире, население
  выставляется через `/admin/f1-tour-fixes`. 20 пригородов ≥100 тыс. ближнего круга помечены `del` (East los
  angeles, Glendale, Inglewood, Pasadena, Downey, Burbank, El monte, Norwalk, Torrance, Long beach, West covina,
  Fullerton, Garden grove, Anaheim, Pomona, Huntington beach, Orange, Santa clarita, Santa ana, Costa mesa).
  Дальние (Ontario, Irvine, Riverside, Palmdale и др.) не трогаем.
- **Abu Dhabi (AE):** 1 560 400 (город ~1,5 млн). Пригородов в CSV нет. Город уже в мире, население через
  `/admin/f1-tour-fixes`. Аэропорт OMAA; OMAD (Bateen, бизнес-авиация) добавлен в `World25_009_create_some_airports` и привязан к Abu Dhabi.
- **Dubai (AE):** 5 940 600 (Dubai + Sharjah + Ajman). Город уже в мире, население через `/admin/f1-tour-fixes`.
  `del As-sariqah` (Sharjah), `del #ajman`. Ras al-Khaimah, Fujairah, Al Ain остаются отдельными. Аэропорт OMDB;
  OMSJ (Sharjah Intl) добавлен в `World25_009_create_some_airports` и привязан к Dubai.
- **Sydney (AU, аэропорт YSSY):** CSV 4 277 200 → 6 077 200 (Greater Sydney ~5,3 млн + Wollongong + Newcastle, ~6,1 млн).
  `del Wollongong` (262 500, ~32 nm) и `del Newcastle` (502 300, ~68 nm) — слиты в Sydney по решению пользователя.
  Страны Australia в мире не было — создаётся импортом. Добавлен в `World25_012_selected_cities`; YSSY (size 8938 ≥5000)
  подтянется и залинкуется автоматически через `ImportMajorAirportsInVicinityOfCities`. Аэропорты YWOL (Wollongong)
  и YWLM (Newcastle/Williamtown), а также YSBK (Bankstown) в мир не добавляем.
- **Jersey (GB, аэропорт EGJJ):** в CSV такого города не было (в наборе только суверенные страны, зависимых
  территорий нет), добавлена строка `United Kingdom,GB,Jersey,103300,49.19,-2.11` (St Helier и весь остров как
  один город, страна GB как у аэропорта в `icaodata`). Добавлен в `World25_012_selected_cities`. EGJJ уже был в мире
  без города (size 1705 < 5000) — линк вручную `create-links?icao=EGJJ` (~9 nm). Guernsey (EGJB), Alderney (EGJA),
  Isle of Man (EGNS) не трогаем, при необходимости добавлять так же строками.
- **Bergen (NO, аэропорт ENBR Flesland):** CSV 212 600 → 412 600 (агломерация/регион ~420 тыс.). `del` не нужен
  (пригороды <100 тыс.). Добавлен в `World25_012_selected_cities`. ENBR уже был в мире без города (size 2983 < 5000,
  автоматически не линкуется) — линк вручную `create-links?icao=ENBR` (~6 nm от центра).
- **Funchal (PT, аэропорт LPMA Madeira):** CSV 103 200 → 253 200 (весь остров Мадейра, аэропорт обслуживает его
  целиком). `del` не нужен (остальное <100 тыс.). Добавлен в `World25_012_selected_cities`. LPMA уже был в мире без
  города (size 2782 < 5000, автоматически не линкуется) — линк вручную `create-links?icao=LPMA` (~7 nm от центра).
  LPPS (Porto Santo, другой остров) не трогаем.
  После импорта вручную: `set-attraction?id=<id Funchal>&attraction=4` (mobility по умолчанию).
- **Bristol (GB, аэропорт EGGD):** CSV 417 600 → 717 600 (агломерация ~700 тыс.). `del` не нужен (пригороды <100 тыс.).
  Города в мире не было (ближайший Birmingham, 72 nm) — добавлен в `World25_012_selected_cities`. EGGD (size 2000 <5000)
  добавлен в `World25_009_create_some_airports` и привязан к Bristol (~6 nm от центра; автоимпорт при этом
  по радиусу 50 nm привязал к Bristol ещё военный EGDM Boscombe Down — игнорируем). Cardiff
  (285 300, ~18 nm), Newport (117 200, ~16 nm) — отдельные города со своими аэропортами; создать позже и
  при необходимости линковать друг на друга (в этот заход не делаем).
- **Doha (QA):** CSV `Ad-dawhah` → `Doha`, население 2 328 500 (Greater Doha). `del Ar-rayyan` (единственный ≥100 тыс.).
  Страны Qatar в мире не было — создаётся импортом. Добавлен в `World25_012_selected_cities`. Аэропорт OTHH
  привязан к Doha. OTBD (закрыт для коммерческих рейсов) не трогаем.
- **Sao Paulo (BR):** переименован `Sao paulo` → `Sao Paulo`, население 22 260 100. 20 пригородов ≥100 тыс. в радиусе
  ~24 nm (45 км) помечены `del` (Guarulhos, Sao bernardo do campo, Osasco, Santo andre, Diadema, Maua, Carapicuiba,
  Moji das cruzes, Itaquaquecetuba, Suzano, Barueri, Embu, Taboao da serra, Itapevi, Cotia, Ferraz de vasconcelos,
  Francisco morato, Itapecerica da serra, Sao caetano do sul, Ribeirao pires). Cubatao, Jundiai, Santos и порт
  Сантос (Sao vicente, Praia grande, Guaruja), Campinas остаются отдельными городами. Добавлен в
  `World25_012_selected_cities`. Аэропорты — SBGR, SBSP (SBKP — Campinas).

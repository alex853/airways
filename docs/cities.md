# Города и население

Идея проекта — симуляция пассажиропотока между городами (в первую очередь для авиакомпаний).
Поэтому «город» в мире — это не административная единица, а зона притяжения аэропорта.

> Практическая инструкция по всему процессу (чек-лист анализа, правила населения и имён, острова, терминалы,
> attraction, порядок работы): [city-airport-playbook.md](city-airport-playbook.md).

## Источники данных

- `src/main/resources/city-population.csv` — исходный список городов и населения. Во многом устарел.
- `src/main/resources/icaodata.csv` — аэропорты (в т.ч. колонка `city`).
- `ImportCities` — создаёт города из CSV. Уже существующие города (страна + имя) **пропускает**, население не обновляет.
- `World25_012_selected_cities` — «классический» список городов для импорта (`city-code` + `city-name`), после него
  `ImportMajorAirportsInVicinityOfCities` импортирует аэропорты в окрестностях и проставляет airport2city-линки.

## Связь аэропорт — город (`airport2city`)

Связь **многие-ко-многим**: один аэропорт может обслуживать несколько городов, а у одного города может быть
несколько аэропортов. «Своего» единственного города у аэропорта нет, лишняя связь не является ошибкой
(например, RCNN у Kaohsiung, LFKF у Ajaccio и Porto-Vecchio).
- Автоимпорт линкует гражданские аэропорты с `size >= 5000` в радиусе 50 nm (`ImportMajorAirportsInVicinityOfCities`).
- `create-links` по умолчанию берёт радиус 50 nm, его можно расширить параметром `maxDistance` (например, 75).
- Связывать через море/границу (например, Calais с британским аэропортом) сомнительно, такие связи не делаем.
- Планируется: параметры «предпочтения» у связи (расстояние, качество наземного транспорта), чтобы journey
  выбирала аэропорт.

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

### Как устроен список и стратегия работы с ним («VATSIM-бэклог»)

**Что в списке.** Список считается `PilotContext.countDestinationIfMissing`: трекер отслеживает (`PilotContext`) только
пилотов, стоящих в аэропорту, который **уже есть в мире** (`VatsimTrackerBean`: `p.isInAirport() && worldIcaos.contains(...)`),
и если пункт назначения плана полёта в мире отсутствует, пишется событие `vatsim - missingAirport <ICAO>`.
Эндпоинт суммирует эти события за **скользящее окно: 7 суток + сегодня**, отдаёт топ-50.
Следствия:
- в списке только **пункты назначения рейсов из уже созданных аэропортов**; регион, где у нас нет ни одного
  аэропорта, в списке не появится вовсе;
- созданный аэропорт остаётся в списке, пока его упоминания не выйдут из окна (до 8 суток), поэтому каждый раз
  нужно фильтровать по `/geo/airports`;
- числа «упоминаний» плавают от запроса к запросу, ориентироваться стоит на устойчивое положение в списке.

**Как работаем.**
- Регулярно (лучше ежедневно) запрашиваем свежий список (таблица нигде не сохраняется, собирается заново),
  берём несколько верхних аэропортов и решаем их вместе с городами вокруг (процедура выше + «актуализировать и
  укрупнить мажорный сити»). Постепенно большая часть аэропортов и городов оказывается создана.
- Создание аэропорта «открывает» новый слой: появляются рейсы из него, и их назначения попадают в список
  (пример: после создания YSSY в списке появился YMML, после YMML ожидаются соседние аэропорты Австралии).
- Многие части мира не покрыты вообще, поэтому часть аэропортов создаётся намеренно **как «семена»** региона,
  даже если они в хвосте списка (пример: FACT для Южной Африки, SAEZ для Южной Америки). Приоритетные
  регионы по первой букве ICAO: **S** (Южная Америка), **Y** (Австралия), **N** (Океания); дальше Африка к северу
  от ЮАР, Центральная Азия, Карибы, Центральная Америка.
- Из списка по умолчанию видны только 50 строк (`limit(50)`); хвост нужных регионов не виден, пока их аэропорты
  не наберут упоминаний (возможное улучшение: параметры `prefix` и `limit` у эндпоинта).

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
| KINS | Indian Springs AF Aux (Creech AFB) | ~~Las Vegas~~ | военная авиабаза; **связь с Las Vegas удалена на проде 2026-10-22** (`/admin/airport/delete-link`), в `icaodata.csv` тип исправлен на `military`; сам аэропорт остался в мире без города |
| KNZJ | El Toro MCAS | Los Angeles | бывшая база, рейсов нет (size 12170) |
| KNTD | Point Mugu NAS | Los Angeles | военно-морская база |
| KSLI | Los Alamitos AAF | Los Angeles | армейский аэродром |
| KPMD | Palmdale AF Plant 42 | Los Angeles | военный завод/испытания |
| KNKX | Miramar MCAS | San Diego, Tijuana | военная база морской пехоты |
| KNHK | Patuxent River NAS | Washington | военно-морская испытательная база |
| KAPG | Phillips AAF | Baltimore | армейский аэродром |
| KFMH | Otis ANGB | Boston | база нацгвардии |
| KNUQ | Moffett Federal AFLD | San Francisco | NASA/бизнес-джеты — не обязательно плохой |
| RKSM | Seoul / Domestic (Seoul Air Base, Seongnam) | Seoul | военная база ВВС (K-16), VIP/правительственные рейсы, в мире трафика нет; название в `icaodata` — артефакт |
| RKSW | Suwon (Suwon Air Base) | Seoul | военная база ВВС (K-13), регулярных гражданских рейсов нет, в мире трафика нет |
| KFTG | Front Range | Aurora, Denver | гражданский GA, ложное срабатывание |
| KNQX | Key West NAS | ~~Key West~~ | военно-морская авиабаза; создана автоимпортом при добавлении Key West (партия 2026-10-23), **связь с Key West удалена на проде 2026-10-23** (`/admin/airport/delete-link`), в `icaodata.csv` тип исправлен на `military`; сам аэропорт в мире остался, без города |

**План:** военные удаляем из мира и при необходимости заменяем на реальные гражданские аэродромы города.
Перед удалением **проанализировать использование** (самолёты, расположенные там; миссии/рейсы, где аэродром
вылета/назначения; airport2city-линки; facilities) — убедиться, что удаление не опасно. Отдельного эндпоинта
удаления аэропорта/линка в админке нет. Одновременно поправить `type` на `military` в `icaodata.csv`.

**Ловушки на будущее** (`civil`, `size >= 5000`, ещё не в мире, импортируются при появлении города рядом):
KNTU Oceana NAS, KYUM Yuma MCAS/Intl (смешанный), KNKT Cherry Point MCAS, KNJK El Centro NAF, KNFL Fallon NAS,
KNQI Kingsville NAS, KNBC Beaufort MCAS, KNLC Lemoore NAS, KNPA Pensacola NAS,
KNGP Corpus Christi NAS, KNMM Meridian NAS, KNHZ Brunswick NAS, KFHU Sierra Vista (смешанный), KFTK Godman AAF,
KGTB Wheeler-Sack AAF, PABI Allen AAF, KNFE Fentress NALF, LIBV Gioia Del Colle Mil, LIED Decimomannu Mil,
NZWP Whenuapai (Military), YPEA Pearce (Military), SAZB Comandante Espora NAS, FZSA Kamina Base и др.

### 2. Аэропорты в мире без привязки к городу — закрыто

Все 19 аэропортов, созданных через `World25_009_create_some_airports` без города, привязаны к городам (2026-10-15):
LPMA, ENBR, EGJJ, EVRA, LGKR, LPFR, LGIR, LTAI, LGSK, LICJ, LIRN, LGRP, LDDU, LDSP, LEAL, LGKO, LGSM, LFKF, EFKS.
Дополнительно созданы LFKJ и LFKB. Названия аэропортов приведены к читаемым в шаге `World25_013`
(`RenameAirports`): LIRN `Capodichino`, LFKJ `Ajaccio Napoleon Bonaparte`, LFKB `Bastia Poretta`, LGKR
`Corfu Ioannis Kapodistrias`, LGIR `Heraklion Nikos Kazantzakis`. Подробности по каждому городу — в разделе «Выполнено». В мире сейчас
160 городов и 219 аэропортов без бесхозных.

### 3. Повторная сверка населения и имён городов с реальным миром (ближайшее будущее)

Цифры ревизии 2026-10-15 (раздел «Выполнено», «Ревизия населения мира и имён») — приблизительные оценки по памяти,
без сверки с источниками. Нужно ещё раз пройти **все** города мира и сверить имена и население с реальными данными
(агломерации по Eurostat, Demographia, переписи), уточнить цифры.

Известные расхождения, которые ещё не исправлены:

| Город | В мире | Оценка | Комментарий |
|---|---|---|---|
| Giza | 4 800 000 | ~7 000 000 | Giza governorate; кластер Greater Cairo ~22M против 16.5M в мире |
| Shubra El Kheima | 1 300 000 | ~3 000 000 | Qalyubia |
| Mecca | 1 618 400 | ~2 400 000 | |
| Montreal | 3 256 300 | ~4 300 000 | CMA |
| Orlando | 1 533 646 | ~2 800 000 | метро Orlando-Kissimmee |
| Seattle (+ Tacoma) | 2 546 200 | ~2 900 000 | Seattle-Tacoma-Bellevue ~4.0M суммарно |
| Frankfurt | 2 300 000 | ~3 500 000 | Rhein-Main ~5.8M, в мире это один узел |
| Nashville | 2 546 000 | ~2 200 000 | возможно завышено (метро ~2.1M) |
| Salt Lake City | 2 779 900 | ~2 700 000 | Wasatch Front, спорно (метро ~1.3M) |

Правила те же: хвост сохраняем, общий бюджет кластера разносим между ядрами без двойного счёта, правки через
`AdminController.f1TourFixes` и `city-population.csv`.

### 4. Страна аэропорта: нет собственного `countryId`

Сейчас у аэропорта в `Airports` нет страны. `/geo/airports` вычисляет её на лету как страну самого населённого
города из `airport2city` (`GeoController.buildCountryIdByAirportId`, при каждой сборке закэшированного ответа), поэтому:
- аэропорт без городской связи отдаёт `country = null` (так выглядели EKVG, LGTS, UMMS между `009` и `create-links`);
- у аэропорта с несколькими городами из разных стран страна зависит от населения (пока граница не пересекается,
  но это неявное правило);
- в `AirportDto` страна отдаётся именем, а не `countryId`, как в `CityDto`.

Подробный план (структура записи, миграция хранилища, порядок работ, открытые вопросы): [airports-record-extension.md](airports-record-extension.md), пока не реализовано.

Что поправить:
- **Расширить структуру `Airports`:** добавить поле `countryId` (`Unsigned8bit`, как в `Cities`: ссылка на страну
  хранится 1 байтом, до 255 стран) и **несколько резервных байт** под будущие расширения аэропорта, чтобы не
  мигрировать хранилище при каждом новом поле.
- Заполнять `countryId` при создании аэропорта (из `icaodata.csv`/refdata) и отдавать его в `AirportDto` (по аналогии
  с `CityDto.countryId`), а не вычислять по связям.
- Нужны миграция хранилища `Airports` и заполнение `countryId` у существующих аэропортов.
- После этого убрать из `GeoController` пересчёт `buildCountryIdByAirportId` (кэш `countryIdByAirportId`,
  `invalidateAirportCountries()` и зависимость `AdminController` от `GeoController` уже убраны, ответы `/geo/*` кэшируются
  по отметкам изменений хранилищ, см. [change-tracking-cache.md](change-tracking-cache.md)).

### 5. Импорты `009`, `012`, `013` в `Application.main` после деплоя

Обычное состояние: `World25_009_create_some_airports`, `World25_012_selected_cities` и `World25_013_rename_cities` **закомментированы**
и не вызываются, иначе импорты гоняются при каждом старте. Раскомментируем только на время пакета с изменениями мира. Когда мир больше менять не
нужно, комментируем обратно и **коммитим сами, без вопросов и без пуша** (пуш вызвал бы лишний деплой через CI). Сейчас они закомментированы
(последний пакет: ROAH, LJLJ, EETN, KEYW, YBCG, 2026-10-23).

### 6. Прочее, обсуждённое и не сделанное

- **Cozumel** как отдельный город: MMCZ Cozumel Intl создан автоматически и привязан к Cancun, сам город Cozumel
  (69 400) не импортирован (по аналогии с Victoria у Vancouver можно добавить).
- **Имена аэропортов:** многие аэропорты в мире имеют сырые имена из refdata (например, `Lake X`); проверить и привести к
  формату `<Город> <Название>`, как делали через `World25_013` (`RenameAirports`).
- **`/geo/*`:** готовый JSON кэшируется и пересобирается только при изменении хранилищ
  ([change-tracking-cache.md](change-tracking-cache.md)); после появления `countryId` в `Airports` убрать пересчёт страны.
- **Бэклог VATSIM:** остаются «тонкие» EETN Tallinn, LIEO Olbia (Сардиния), далее Европа/США по списку; особые KPNS, KJAC.

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
- **Корсика (FR, остров ~350 тыс.) — большой остров, собран в три города, а не в один (как Cyprus, не как Jersey):**

  | Город | Зона | Население | Аэропорты |
  |---|---|---|---|
  | Ajaccio | юго-запад (Ajaccio, Propriano, Sartène) | 46 800 → 146 800 | LFKJ (в мире нет) |
  | Bastia | северо-восток и север (Bastia, Cap Corse, Balagne, Calvi, Corte) | 42 000 → 132 000 | LFKB, LFKC (в мире нет) |
  | Porto-Vecchio | юго-восток (Porto-Vecchio, Bonifacio) | в CSV не было, добавлена строка `France,FR,Porto-Vecchio,70000,41.59,9.28` | LFKF (в мире есть) |

  Аэропорты острова: LFKF (Figari), LFKJ (Ajaccio Napoléon-Bonaparte, самый крупный, ~1,5 млн пасс.) и LFKB (Bastia
  Poretta, ~1,2 млн) добавлены в `World25_009`. Линки с дефолтным радиусом 50 nm: LFKF привяжется к Porto-Vecchio
  и к Ajaccio (30 nm), LFKJ к Ajaccio и Porto-Vecchio, LFKB к Bastia — это нормально, аэропорты острова
  взаимозаменяемы для соседних городов. Attraction: выставить всем трём городам (значения TBD).
- **Бизнес-авиация Los Angeles:** `BusinessAviationTerminal` созданы в KBUR, KSNA, KLGB, KLAX (по подтверждению
  со спутника). **KVNY Van Nuys** (size 3665, 7 nm от центра, главный бизнес-аэропорт региона) добавлен в
  `World25_009`, линк `create-links?icao=KVNY` (дефолтный радиус) и терминал вручную. KONT, KCNO — без терминала;
  военные KPMD, KSLI, KNTD, KNZJ — в списке военных.
- **Melbourne (AU, аэропорт YMML) — из списка отсутствующих по VATSIM (появился после создания YSSY):** CSV
  3 666 000 → 5 266 000 (Greater Melbourne ~5,3 млн), `del` не нужен (пригороды <100k). Geelong (161 500, 37 nm),
  Ballarat, Bendigo не трогаем. Добавлен в `World25_012_selected_cities`. YMML (size 5945) и YMMB Moorabbin (size
  5432, GA) создаются и привязываются автоматически; **YMEN Essendon** (size 3506, основной бизнес-аэропорт
  города) добавлен в `World25_009`, переименован `Melbourne Essendon`, линк `create-links?icao=YMEN` и
  `BusinessAviationTerminal` вручную. YMAV Avalon не берём. Attraction не нужен.
- **Delhi (IN, аэропорт VIDP) — из списка отсутствующих по VATSIM, «семя» Северной Индии:** CSV `Dilli` → `Delhi`
  (английское имя), 10 400 900 → 31 900 900 (Delhi NCR ~32 млн). `del`: Ni dilli (New Delhi), Noida, Faridabad,
  Ghaziabad (≤20 nm). Rohtak (34 nm), Mirat/Meerut (40 nm), Panipat (50 nm) остаются отдельными. Добавлен в
  `World25_012_selected_cities`. VIDP (size 6652, автолинк) переименован в `Delhi Indira Gandhi`. VIDX Hindon не
  берём. Attraction не нужен.
- **Addis Ababa (ET, аэропорт HAAB) — из списка отсутствующих по VATSIM, «семя» Восточной Африки:** новая страна
  Ethiopia. CSV `Addis abeba` → `Addis Ababa` (английское имя), 2 763 500 → 4 763 500 (агломерация ~5 млн). `del`
  не нужен (пригородов ≥100k нет; Debre zeyit 98 700 и Nazret 176 800 отдельные). Добавлен в
  `World25_012_selected_cities`. HAAB (size 7500, автолинк) переименован `Bole Intl` → `Addis Ababa Bole`. Военный
  HAHM Harar Meda не берём. Attraction не нужен.
- **New Orleans (US, аэропорт KMSY) — из списка отсутствующих по VATSIM:** CSV `New orleans` → `New Orleans`,
  466 600 → 1 270 600 (агломерация MSA ~1,27 млн; город сам ~380k). `del Metairie` (147 900, 4 nm от KMSY).
  Baton Rouge (53 nm) отдельный, не трогаем. Добавлен в `World25_012_selected_cities`. KMSY (size 6304) создаётся и
  привязывается автоматически; `BusinessAviationTerminal` в KMSY вручную. KNEW Lakefront (size 4174, 12 nm) не берём
  по решению; KNBG (NAS JRB, в icaodata помечен civil) — военный. Attraction не нужен.
- **Johannesburg + Pretoria (ZA, аэропорт FAOR) — из списка отсутствующих по VATSIM:** FAOR не было ни в
  `icaodata.csv` (там старый код FAJS), ни в refdata до 3.1.3 (добавлен вручную рядом с OTHH, LTFM, VTBS).
  В `icaodata.csv` строка `FAJS` заменена на `FAOR` (координаты -26.138889/28.245833, `O. R. Tambo International`),
  чтобы автоимпорт создал правильный код. CSV: Johannesburg 1 975 500 → 8 900 500 (Johannesburg + Ekurhuleni +
  West Rand), `del`: Soweto, Benoni, Tembisa, Boksburg, Krugersdorp, Brakpan, Springs, Alberton, Midrand,
  Randfontein, Nigel, Westonaria; Pretoria 1 473 800 → 2 473 800, `del Verwoerdburg` (Centurion). Vereeniging,
  Vanderbijlpark, Carltonville остаются отдельными. Оба города в `World25_012`; FAOR (size 10696) создаётся
  автоматически и связывается с обоими; переименован `Johannesburg O.R. Tambo`. `BusinessAviationTerminal` в FAOR
  вручную. FALA, FAGM, FAWB не берём; военные FAWK, FASK не берём. Attraction не нужен.
  Страна `South africa` в CSV исправлена на `South Africa` (все строки ZA), существующая страна в мире
  переименована через `RenameCountries` в `World25_013` (страны ищутся по коду, дублей не будет).
- **Bangkok (TH, аэропорты VTBS и VTBD) — из списка отсутствующих по VATSIM, «семя» Юго-Восточной Азии:** новая
  страна Thailand. CSV `Krung thep` → `Bangkok` (английское имя), 5 455 200 → 10 900 200 (Bangkok Metropolitan
  Region ~11 млн). `del`: Samut prakan, Nonthaburi, Pak kret, Phra pradaeng, Thanyaburi, Khlong luang (≤22 nm от
  центра). Nakhon pathom (26 nm), Chon buri, Si racha остаются отдельными. VTBS (Suvarnabhumi) нет в
  `icaodata.csv`, берётся из refdata 3.1.3: добавлен в `World25_009`, переименован `Bangkok Suvarnabhumi`, линк
  `create-links?icao=VTBS`. VTBD (в `icaodata.csv` назван `Bangkok Intl`, на самом деле Don Mueang, size 7227)
  создаётся и привязывается автоматически, переименован `Bangkok Don Mueang`. `BusinessAviationTerminal` только в
  VTBS. VTBK Kamphaeng Saen не берём. Attraction 2 (`set-attraction`).
- **Италия — актуализация существующих городов:** `Roma` → `Rome` (2 618 900 → 4 318 900, агломерация), `Milano` →
  `Milan` (1 212 900 → 4 312 900, urban area; `del Monza`, `del Bergamo`), Venice 271 800 → 850 800 (метро с
  Местре и Падуей). Naples, Palermo не менялись. Имена — шаг `RenameCities` в `World25_013`, население — `f1-tour-fixes`
  (ImportCities не обновляет существующие). **LIML Linate** (size 3043, 4 nm) добавлен в `World25_009`, линк
  `create-links?icao=LIML` на Milan. Attraction: Rome 5, Venice 5, Milan 3, Naples 2, Palermo 2.
- **Германия — актуализация до агломераций:** 15 городов подняты до агломерации, 31 город ≥100k в радиусе 25 nm от
  ближайшего ядра слит `del` (Рейн-Рур поделён по ближайшему ядру: Essen, Duisburg, Dusseldorf, Dortmund, Koln, Bonn;
  Rhein-Main — Frankfurt; Halle — Leipzig; Potsdam — Berlin и т.д.). Bremen 745 000 не меняется (задан под F1),
  Oldenburg не сливается. Английские имена: Munchen → Munich, Koln → Cologne, Nurnberg → Nuremberg, Hannover →
  Hanover, Frankfurt am Main → Frankfurt (`RenameCities` в `World25_013`). Население — `f1-tour-fixes`. Attraction в
  Германии не ставим. **EDLW** (Dortmund, size 1997) добавлен в `World25_009`, `create-links?icao=EDLW` с дефолтным
  радиусом.
- **Osaka / Kobe / Kyoto (JP, аэропорты RJBB и RJOO) — из списка отсутствующих по VATSIM, «семя» Кансая:** три
  ядра остаются отдельными (как Tokyo/Yokohama/Kawasaki). CSV: Osaka 2 596 700 → 10 000 700, Kobe 1 535 100 →
  2 300 100, Kyoto 1 468 200 → 2 500 200; `del` — города ≥100k в радиусе 25 nm до ближайшего ядра (Osaka: Sakai,
  Higashiosaka, Nishinomiya, Amagasaki, Hirakata, Toyonaka, Nara, Suita и др.; Kobe: Akashi, Kakogawa, Sanda; Kyoto:
  Takatsuki, Otsu, Uji, Kusatsu). Wakayama, Himeji, Tokushima остаются отдельными. RJBB (size 3500) и RJOO (size 4829)
  ниже 5000 — через `World25_009`, переименованы `Osaka Kansai` и `Osaka Itami`, затем `create-links` на оба.
  Терминалы и attraction не нужны.
- **Manila (PH, аэропорт RPLL) — из списка отсутствующих по VATSIM, «семя» Филиппин:** новая страна Philippines.
  CSV Manila 10 330 100 → 14 000 100 (Metro Manila), `del` 12 городов ≥100k в радиусе 25 nm (Bacoor, Cainta,
  Binan, Taytay, Malolos, Binangonan, Santa rosa, Meycauayan, Antipolo, Baliuag, Cavite, San mateo). RPLL (size
  5414) и RPLC Clark (size 6431, ≈41 nm) создаются автоматически и связываются с Manila; переименованы `Manila
  Ninoy Aquino` и `Manila Clark` (`World25_013`). RPLB Subic, военные RPLS, RPUF не берём. Терминалы и attraction не
  нужны.
- **Taiwan (RCTP, RCKH) — из списка отсутствующих по VATSIM:** новая страна, два города (остров большой, но
  остальное — Taichung, Tainan, Chiayi и др. — пока не добавляем):

  | Город | Было → стало | `del` (абсорбированные окрестности) | Аэропорт |
  |---|---|---|---|
  | Taipei | 2 609 300 → 8 609 300 (Большой Тайбэй) | 21: Panchiao, Hsinchuang, Shulin, Tucheng, Chungho, Yungho, Sanchung, Luchou, Hsintien, Yingko, Sanhsia, Taoyuan, Tanshui, Pate, Hsichih, Tachi, Chungli, Pingchen, Keelung, Yangmei, Juifang | RCTP (size 7017, авто), переименован в `Taipei Taoyuan` |
  | Kaohsiung | 1 519 800 → 2 819 800 | 6: Fengshan, Kangshan, Pingtung, Tungkang, Chaochou, Chishan | RCKH (size 6204, авто) |

  Не абсорбируются: Hsinchu (28 nm), Ilan (22 nm), Taichung, Tainan (23 nm от Kaohsiung), Meinung. Второй аэропорт
  Taipei (RCSS Songshan, бизнес-авиация) не добавляем. Attraction не нужен.
- **Пять городов из списка отсутствующих по VATSIM (SAEZ, WIII, ZBAA, PANC, FACT):**

  | Аэропорт | Город | Было → стало | `del` | Линк |
  |---|---|---|---|---|
  | SAEZ | Buenos Aires (в CSV `Buenos aires`; Argentina новая страна) | 11 928 400 → 15 428 400 | нет | авто (size 6404); аэропорт переименован `Buenos Aires Ezeiza` |
  | WIII | Jakarta (Indonesia новая страна) | 8 987 800 → 33 987 800 | Tangerang, Bekasi, Bogor, Depok, Ciputat, Pondokgede, Cimanggis, Ciomas (Jabodetabek, ≤30 nm) | авто (size 7258) |
  | ZBAA | Beijing | 7 209 900 → 22 009 900 | нет (Langfang 34 nm и Tianjin 63 nm отдельные) | авто (size 7006) |
  | PANC | Anchorage | 273 000 → 403 000 | нет (Аляска изолирована, ближайший город мира Seattle 1247 nm) | авто (size 10 058) |
  | FACT | Cape Town (в CSV `Cape town`; ЮАР новая страна) | 2 984 100 → 4 584 100 | Somerset west, Paarl (≥100k, ≤25 nm) | вручную `create-links?icao=FACT` (size 4908) |

  Второй аэропорт (WIIH Halim, SABE Aeroparque, ZBAD Daxing) не добавляем. Для Jakarta второй `Depok` в CSV
  (136 700, Центральная Ява) не трогается. Attraction не нужен. Шаг `World25_013` в `Application.main` теперь
  стоит **после** `World25_012`: переименование аэропортов должно идти после того, как импорт их создал
  (переименования городов давно выполнены, повторно они пропускаются).
- **Stavanger (NO, аэропорт ENZV) — из списка отсутствующих по VATSIM:** CSV 171 300 → 331 300 (регион Nord-Jæren
  с Sandnes, Sola, Randaberg ~330 тыс.). `del` не нужен (пригороды <10 тыс.), Haugesund (40 300, 35 nm) отдельный.
  Добавлен в `World25_012_selected_cities`. ENZV (size 4992 < 5000, автоматически не линкуется) добавлен в
  `World25_009`, линк `create-links?icao=ENZV` с дефолтным радиусом (Stavanger 6 nm; Bergen 91 nm не попадает).
  Имя аэропорта `Sola` → `Stavanger Sola` (шаг `World25_013`). Attraction не нужен.
- **Sochi (RU, аэропорт URSS) — из списка отсутствующих по VATSIM:** CSV `Soci` → `Sochi` (английское имя),
  313 900 → 513 900 (Большое Сочи). Пригородов в CSV нет (абхазские городки Gagra/Pitsunda под `GE`, не берём).
  Добавлен в `World25_012_selected_cities`. URSS (size 5057) создаётся и привязывается автоматически импортом,
  `create-links` не нужен. Attraction не нужен.
- **Shanghai (CN, аэропорты ZSPD и ZSSS) — из списка отсутствующих по VATSIM:** CSV 13 278 500 → 25 278 500
  (муниципалитет ~25 млн). В радиусе 45 nm в CSV только Suzhou (1 150 200, 44 nm, другая провинция), он, как и
  Jiaxing, Wuxi, Ningbo, Hangzhou (58–99 nm), остаётся отдельным городом; `del` ставить некому. Добавлен в
  `World25_012_selected_cities`. Два аэропорта: ZSPD Pudong (size 4008) и ZSSS Hongqiao (size 3407, второй
  аэропорт города, как KHND у Las Vegas) добавлены в `World25_009`, оба `size < 5000`, линки вручную
  (`create-links?icao=ZSPD`, `create-links?icao=ZSSS`, дефолтный радиус). Имена аэропортов:
  `Shanghai Pudong`, `Shanghai Hongqiao` (шаг `World25_013`). Attraction не нужен.
- **Honolulu (US, аэропорт PHNL) — из списка отсутствующих по VATSIM:** весь остров Oahu собран в один город
  (остров небольшой, Honolulu County ~1,0 млн), CSV 382 800 → 1 012 800. В CSV других гавайских городов нет, `del`
  не нужен. Добавлен в `World25_012_selected_cities`. PHNL (size 14 708) и PHJR Kalaeloa (size 5 643) создаются и
  привязываются автоматически импортом `ImportMajorAirportsInVicinityOfCities`, ручной `create-links` не нужен.
  Attraction Honolulu = 3. Остальные острова (Maui, Big Island, Kauai) не трогаем, при необходимости добавлять
  строками в CSV.
- **Bremen (DE, аэропорт EDDW) — из списка отсутствующих по VATSIM:** город уже был в мире (545 000, линк только к
  EDDV Hannover по радиусу 50 nm), поэтому нужен только аэропорт: EDDW (size 2736 < 5000, автоматически не
  линкуется) добавлен в `World25_009`, линк `create-links?icao=EDDW` с дефолтным радиусом (только Bremen, 2 nm;
  Hamburg 53 nm и Hannover 52 nm в радиус не попадают). Население Bremen 545 000 → 745 000 (город ~570 тыс. +
  Delmenhorst 76 тыс. и пригороды); Oldenburg (158 600, 22 nm) и Bremerhaven (119 500, 31 nm) — отдельные
  города, как и остальные города Рура/Нижней Саксонии. Население существующего города выставляется
  `/admin/f1-tour-fixes`.
- **Третья группа (особые):** LIRN → Naples (CSV `Napoli` → `Naples`, 995 000 → 3 095 000, агломерация ~3,1 млн;
  пригороды <100 тыс. не трогаем, Salerno 144 300 отдельный рынок); аэропорт LIRN переименован
  `Capodichino Mil` → `Capodichino` (шаг `World25_013`, `RenameAirports`). EFKS → Kuusamo 18 800 как есть
  (лыжный курорт). Attraction не нужен. Линки вручную: `create-links?icao=LIRN`, `create-links?icao=EFKS`.
- **Переименование существующих городов в английские имена:** в мире города были созданы транслитерацией из CSV.
  `Kerkira` → `Corfu`, `Iraklion` → `Heraklion`, `Rodos` → `Rhodes` (GR). Делается шагом
  `World25_013_rename_cities` (`RenameCities`, идемпотентный), который в `Application.main` стоит **перед**
  `World25_012_selected_cities`; иначе импорт по новым именам из CSV создал бы дубликаты. Имена в CSV и в
  `World25_012` изменены. После отработки шага вызов `World25_013` вернуть в комментарий.
- **Группа «курорты и острова» (6 аэропортов), добавлены в `World25_012_selected_cities`, линки вручную
  (все `size < 5000`):**

  | Аэропорт | Город | Было → стало | Примечание |
  |---|---|---|---|
  | LPFR Faro | Faro (PT) | 40 600 → 450 600 | весь Алгарве, аэропорт единственный крупный в регионе |
  | LEAL Alicante | Alicante (ES; в CSV было `Alacant`) | 312 000 → 812 000 | `del Elx` (Elche, 203 600); Murcia отдельный рынок, не трогаем |
  | LDDU Dubrovnik | Dubrovnik (HR) | 30 500 → 70 500 | с окрестностями, боснийское и черногорское не берём |
  | LGKO Kos | Kos (GR) | 18 300 → 37 300 | весь остров |
  | LGSM Samos | Samos (GR) | 6 400 → 32 400 | весь остров; `create-links` с `maxDistance=15`, иначе дефолтные 50 nm привяжут и к Izmir (46 nm) |
  | LGSK Skiathos | Skiathos (GR) | 5 100 → 6 100 | весь остров, поток через attraction |

  В CSV `Alacant` переименован в `Alicante` (английское имя).
  LFKF (Figari, Корсика) — см. запись про Корсику ниже.
- **Группа «острова и города без города» (7 аэропортов), население в CSV, `del` не нужен (пригородов ≥100 тыс. нет),
  все добавлены в `World25_012_selected_cities`:**

  | Аэропорт | Город (CSV) | Было → стало | Линк |
  |---|---|---|---|
  | LTAI Antalya | Antalya (TR) | 659 200 → 1 259 200 | привязан (авто) |
  | LICJ Palermo | Palermo (IT) | 671 100 → 1 171 100 | привязан (авто) |
  | LGIR Heraklion | Iraklion (GR, Крит) | 133 800 → 283 800 | привязан (авто) |
  | EVRA Riga | Riga (LV) | 689 300 → 889 300 | привязан |
  | LDSP Split | Split (HR) | 175 700 → 275 700 | привязан |
  | LGKR Corfu | Kerkira (GR) | 28 800 → 102 800 (весь остров) | привязан |
  | LGRP Rhodes | Rodos (GR) | 53 500 → 125 500 (весь остров) | привязан |

  Attraction выставлен (Antalya 4, Palermo 2, Iraklion 3, Split 2, Kerkira 3, Rodos 3; Riga по умолчанию).
  В CSV два `Iraklion` в GR (Крит и пригород Афин, 47 000):
  импорт создаёт первый по порядку в файле (Крит), второй пропускает как существующий.
- **Jersey (GB, аэропорт EGJJ):** в CSV такого города не было (в наборе только суверенные страны, зависимых
  территорий нет), добавлена строка `United Kingdom,GB,Jersey,103300,49.19,-2.11` (St Helier и весь остров как
  один город, страна GB как у аэропорта в `icaodata`). Добавлен в `World25_012_selected_cities`. EGJJ уже был в мире
  без города (size 1705 < 5000), теперь привязан к Jersey. Guernsey (EGJB), Alderney (EGJA),
  Isle of Man (EGNS) не трогаем, при необходимости добавлять так же строками.
- **Bergen (NO, аэропорт ENBR Flesland):** CSV 212 600 → 412 600 (агломерация/регион ~420 тыс.). `del` не нужен
  (пригороды <100 тыс.). Добавлен в `World25_012_selected_cities`. ENBR уже был в мире без города (size 2983 < 5000,
  автоматически не линкуется), теперь привязан к Bergen.
- **Funchal (PT, аэропорт LPMA Madeira):** CSV 103 200 → 253 200 (весь остров Мадейра, аэропорт обслуживает его
  целиком). `del` не нужен (остальное <100 тыс.). Добавлен в `World25_012_selected_cities`. LPMA уже был в мире без
  города (size 2782 < 5000, автоматически не линкуется), теперь привязан к Funchal.
  LPPS (Porto Santo, другой остров) не трогаем.
  Attraction Funchal = 4 (mobility по умолчанию).
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
- **Lyon (FR, аэропорты LFLL и LFLY) — из списка отсутствующих по VATSIM (№1):** CSV 443 600 → 2 300 600 (aire
  d'attraction ~2,3 млн), `del Villeurbanne`. Saint-etienne и Grenoble остаются отдельными (не импортируются).
  LFLL (size 6667) создаётся автоматически и связывается с Lyon, переименован `Lyon Saint-Exupery`. **LFLY Bron**
  (size 3200, деловой/GA аэропорт) добавлен в `World25_009`, переименован `Lyon Bron`, линк `create-links?icao=LFLY`.
  `BusinessAviationTerminal` в LFLL и LFLY. LFXA Amberieu (military) не берём. Attraction не нужен.
- **Vancouver, Victoria, Calgary (CA, аэропорты CYVR, CYYJ, CYYC) — из списка отсутствующих по VATSIM, Канада:**
  CSV Vancouver 1 836 500 → 2 900 500 (Metro Vancouver ~2,9 млн), Calgary 938 300 → 1 600 300 (CMA ~1,6 млн),
  Victoria 289 400 без изменений (отдельный город, CYYJ Victoria Intl связан и с Vancouver, и с Victoria, так как
  в 40 nm от центра Vancouver). `del` не нужен (пригороды <100k, Abbotsford 140k на 36 nm отдельный). CYVR (size
  9653), CYYJ (size 5304), CYYC (size 8177) создаются автоматически. `BusinessAviationTerminal` в CYVR и CYYC.
  CYXX Abbotsford (size 4952), CYPK, CZBB не берём. Attraction не нужен.
- **Fukuoka (JP, аэропорт RJFF) — из списка отсутствующих по VATSIM, «семя» Кюсю:** CSV 1 376 100 → 2 600 100
  (Fukuoka MEA ~2,6 млн), `del` Kasuga, Kurume, Saga (≤21 nm). Kitakyushu (28 nm), Omuta, Shimonoseki, Sasebo,
  Kumamoto, Nagasaki остаются отдельными (не импортируются). RJFF (size 2808, ниже 5000) — через `World25_009`,
  `create-links?icao=RJFF`. Терминал и attraction не нужны.
- **Malta (MT, LMML) и Sint Maarten (SX, TNCM) — острова, из списка отсутствующих по VATSIM:** в CSV для островов
  не было подходящего города (Malta — только пункты <100k, Sint Maarten — ничего), поэтому добавлены строки:
  `Malta,MT,Malta,560000` (остров целиком) и `Sint Maarten,SX,Sint Maarten,75000` (весь остров, голландская и
  французская части; аэропорт голландский, имя Sint Maarten; в `icaodata.csv` страна `Netherlands Antilles`).
  Мелкие пункты Malta не помечаются `del` (<100k). LMML (size 5815) создаётся автоматически, переименован
  `Malta Luqa`; TNCM (size 2301) — через `World25_009`, `create-links?icao=TNCM`. TFFG Grand Case не берём.
  `BusinessAviationTerminal` в LMML и TNCM. Attraction: Malta 5, Sint Maarten 15 (`set-attraction`).
- **Riyadh (SA, аэропорт OERK) — из списка отсутствующих по VATSIM (№8, 2512 упоминаний), Ближний Восток:** 
  CSV `Ar-riyad#` 3 561 500 → `Riyadh` 7 500 500 (агломерация, оценка ~7,5–8 млн; хвост 500 сохранён). 
  Al-Kharj (194,5k, 64 nm от аэропорта) остаётся отдельным городом, `del` не нужен. Страна в CSV `Saudi arabia` →
  `Saudi Arabia` (CSV + `RenameCountries` в `013`). OERK (size 8429) создаётся автоматически, переименован
  `Riyadh King Khalid`. OERY (military), OETH Thumamah (4307) и мелкие OE0x не берём. 
  `BusinessAviationTerminal` в OERK. Attraction не нужен.
- **Halifax (CA, аэропорт CYHZ) — из списка отсутствующих по VATSIM (1965 упоминаний), Канада:** CSV 268 900 →
  480 900 (CMA ~480–500 тыс.; хвост 900 сохранён). `del` не нужен (в 25 nm нет городов ≥100k). CYHZ (size 5027)
  создаётся автоматически, имя `Halifax Intl` оставлено. CYAW Halifax/Shearwater и CYZX Greenwood (военные) не
  берём. Терминал и attraction не нужны.
- **Minsk (BY, аэропорт UMMS) — из списка отсутствующих по VATSIM (1885 упоминаний), Беларусь:** CSV 1 646 600 →
  2 300 600 (агломерация ~2,3 млн; хвост 600 сохранён). `del` не нужен: Barisaw (154,9k) ~39 nm от центра, остаётся
  отдельным. UMMS Minsk-2 (size 3604, ниже 5000) — через `World25_009`, `create-links?icao=UMMS`, переименован
  `Minsk National`. UMMM Minsk-1 (2092) не берём, UMLI Machulishchi (военная авиабаза с типом `civil`) не берём.
  Терминал и attraction не нужны.
- **Tel Aviv (IL, аэропорт LLBG) — из списка отсутствующих по VATSIM (№1, 3436 упоминаний), Израиль, «семя
  региона»:** CSV `Tel aviv-yafo` 366 900 → `Tel Aviv` 4 000 900 (Gush Dan, ~4,0–4,3 млн; хвост 900 сохранён). `del`
  у девяти городов ≥100k в 25 nm от ядра: Rishon lez#iyyon, Ashdod, Petah# tiqwa, Netanya, H#olon, Bene beraq,
  Bat yam, Ramat gan, Reh#ovot. Ashqelon (26 nm, граница) не вливаем и не импортируем. Yerushalayim (29 nm от ядра,
  отдельный рынок) отложен. LLBG (size 8513) создаётся автоматически, переименован `Tel Aviv Ben Gurion`.
  Военные базы с типом `civil` и size ≥5000 в радиусе 50 nm (LLEK Tel Nov, LLHS Hatzor, LLRD Ramat David, LL0D
  Hatserim) переведены в `military` в `icaodata.csv`, чтобы автоимпорт их не создал. LLSD Sde Dov (закрыт) не берём.
  `BusinessAviationTerminal` в LLBG. Attraction не нужен.
- **Portland (US, аэропорт KPDX) — из списка отсутствующих по VATSIM (№2, 3033 упоминания):** CSV 539 900 →
  2 500 900 (метро Portland–Vancouver–Hillsboro, MSA ~2,5 млн; хвост 900 сохранён), `del Vancouver` (WA, 153,4k,
  4 nm от ядра; тёзка Vancouver BC, который в мире уже есть). Gresham (96,9k) <100k не помечен, Salem (44 nm)
  отдельный рынок, не импортируется. KPDX (size 7915) создаётся автоматически, имя `Portland Intl` оставлено.
  KHIO Hillsboro (3243) не берём. Терминал и attraction не нужны.
- **Thessaloniki (GR, аэропорт LGTS) — из списка отсутствующих по VATSIM (1837 упоминаний):** CSV 372 100 →
  1 000 100 (метро ~1,0 млн; хвост 100 сохранён). Пригородов ≥100k нет (Kalamaria 89k), `del` не нужен. Katerini,
  Larisa, Volos — отдельные рынки. LGTS (size 4848, ниже 5000) — через `World25_009`, `create-links?icao=LGTS`,
  переименован `Thessaloniki Makedonia`. Военные аэродромы рядом (LGSD, LGAX, LGLR…) уже `military`.
  `BusinessAviationTerminal` в LGTS. Attraction не нужен.
- **Faroe Islands (FO, аэропорт EKVG) — из списка отсутствующих по VATSIM (2855 упоминаний), «семя» Северной
  Атлантики:** в CSV городов Фарер не было, добавлена строка `Faroe Islands,FO,Faroe Islands,54000,62.01,-6.77` (весь
  архипелаг, центр на Torshavn, ~16 nm от аэропорта). Страну FO создаёт импорт. EKVG Vagar (size 1246) — через
  `World25_009`, `create-links?icao=EKVG`, переименован `Faroe Islands Vagar`. Терминал не нужен. Attraction 3
  (`set-attraction`).
- **Ревизия населения мира и имён (F1 Tour Fixes):** часть импортированных городов осталась с населением
  по CSV (муниципалитет), а не по агломерации. Укрупнены (хвост старого числа сохранён) через
  `AdminController.f1TourFixes` и в `city-population.csv`: Paris 11.5M, New York 19.0M, Atlanta 6.3M, Washington
  6.4M, Boston 4.9M, Chicago 9.5M, Houston 7.3M, Dallas 5.5M + Fort Worth 2.4M (метро 7.9M), Miami 3.0M + Fort
  Lauderdale 1.5M (Florida-кластер), Tampa 2.4M + St. Petersburg 0.75M, Detroit 4.3M, Phoenix 5.0M, Denver 2.4M +
  Aurora 0.6M, San Diego 3.3M, San Antonio 2.6M, Baltimore 2.8M, Madrid 6.7M, Barcelona 5.3M, London 9.5M,
  Manchester 2.8M, Athens 3.6M, Lisbon 2.9M, Istanbul 15.5M, Zurich 1.4M, Helsinki 1.5M, Dublin 2.1M, Brussels
  2.1M, Vienna 2.9M, Prague 2.7M, Budapest 3.0M, Mumbai 20.0M, Moscow 13.0M, Saint Petersburg 5.6M, Toronto
  6.5M, Santiago de Chile 6.5M, Kowloon 6.0M + Hong Kong Island 1.5M, Tokyo 13.5M (Yokohama, Kawasaki отдельно),
  Stockholm 2.4M, Copenhagen 2.1M, Birmingham 2.9M, Glasgow 1.8M, Liverpool 0.9M, Edinburgh 0.9M, Gothenburg
  1.0M, Gdansk 1.1M, Geneva 1.0M, Nice 1.0M, Stuttgart 2.7M, Warsaw 3.1M, Jeddah 4.7M, Bursa 3.1M, Tijuana
  2.1M. Для поликентричных кластеров общий бюджет агломерации разнесён между ядрами, чтобы не было двойного
  счёта потока. Переименованы: `Wien` → `Vienna`, `Praha` → `Prague`, `Geneve` → `Geneva`, `Mexico` → `Mexico
  City` (в CSV, в `World25_012` и в мире через `f1TourFixes`). Оценки приблизительные. Импорты `009`, `012`,
  `013` закомментированы в `Application.main`.
- **EKBI, HEGN, TJSJ, YBBN, OJAI, GCLP, MMUN, MYNN — пакет из списка отсутствующих по VATSIM (2026-10-16):**
  - **Billund (DK, EKBI):** в CSV город был как населённый пункт, `Billund` 6 100 → 400 100 (зона притяжения
    аэропорта: Vejle, Kolding, Esbjerg, Horsens, Herning, Fredericia, все <100k; хвост 100 сохранён). EKBI
    (size 3094) через `World25_009`.
  - **Hurghada (EG, HEGN):** `Al-#ardaqah` 70 200 → `Hurghada` 290 200, `Safaja` → `Safaga`. HEGN (7010)
    автоматически.
  - **San Juan (PR, TJSJ):** Пуэрто-Рико в CSV не было, строка-«семя» `Puerto Rico,PR,San Juan,2100000` (метро
    San Juan–Bayamón–Caguas). TJSJ (5492) автоматически, имя `San Juan Luis Munoz Marin`.
  - **Brisbane (AU, YBBN):** 1 598 600 → 2 600 600 (Greater Brisbane). Gold Coast, Sunshine Coast отдельные.
    YBBN (5332) автоматически; YBAF Archerfield не берём.
  - **Amman (JO, OJAI):** 1 293 700 → 4 500 700 (Greater Amman), `del` Az-zarqa', Ar-russayfah, Wadi as-sir.
    OJAI (7287) автоматически, `Amman Queen Alia`; OJAM Marka не берём. Страна Jordan создаётся импортом.
  - **Gran Canaria (ES, GCLP):** по прецеденту Tenerife/Mallorca остров-«семя» `Gran Canaria` 855 000,
    `del Las palmas` (356 200). GCLP (6215) автоматически, имя `Gran Canaria` оставлено.
  - **Cancun (MX, MMUN):** 478 400 → 900 400. MMUN (3504) через `World25_009`. Побочно MMCZ Cozumel Intl
    (5181, 31 nm) создастся автоматически и привяжется к Cancun (Cozumel как город не импортируем).
  - **Nassau (BS, MYNN):** 226 100 → 275 100 (New Providence). MYNN (5898) автоматически. Страна Bahamas
    создаётся импортом.
  - `BusinessAviationTerminal`: TJSJ, YBBN, OJAI, GCLP, MMUN, MYNN. Attraction: Hurghada 5, San Juan 5,
    Cancun 8, Nassau 5, Gran Canaria 5.
- **EGAA, NZAA — Belfast и Auckland (VATSIM-бэклог, 2026-10-17):**
  - **Belfast (GB, EGAA):** CSV 248 600 → 700 600 (городская агломерация ~650-700k, оценка приблизительная). Newtownabbey,
    Bangor, Lisburn меньше 100k, `del` не ставим (опечатка `Newtonabbey` в CSV исправлена на `Newtownabbey`). Добавлен
    в `World25_012_selected_cities`. EGAA (size 4722) и EGAC Belfast City (1826, 12 nm) через `World25_009`, линки
    вручную (`create-links`), имена `Belfast International` и `Belfast City` (`World25_013`). Londonderry и Dundalk не берём.
  - **Auckland (NZ, NZAA):** CSV разбивал агломерацию на четыре города: 379 600 → 1 700 600 (urban area ~1,7M,
    оценка приблизительная), `del Manukau` (344 400), `del North shore` (193 000), `del Waitakere` (154 400).
    Hamilton (52 nm) остаётся отдельным рынком. NZAA (6742) создаётся автоматически, имя `Auckland Intl` оставлено.
    Страна `New zealand` в CSV исправлена на `New Zealand` (все 189 строк, иначе страна создалась бы с кривым именем),
    страна создаётся импортом.
  - **NZWP Whenuapai** (военная база, в `icaodata` стоял `civil`, size 5906 попал бы в автоимпорт): тип в `icaodata.csv`
    изменён на `military`, в мир не попадёт.
  - Терминалы бизнес-авиации не ставим. Attraction: Auckland 3.
- **KOKC, HKJK, UWKD — Oklahoma City, Nairobi, Kazan (VATSIM-бэклог, 2026-10-17):**
  - **Oklahoma City (US, KOKC):** CSV 527 100 → 1 400 100 (метро ~1,4M, оценка приблизительная), `del Norman` (100 300, 16 nm).
    KOKC (size 9301) создаётся автоматически, имя `Oklahoma City Will Rogers`. KPWA Wiley Post (основной GA/бизнес) в мир
    не берём по решению; KTIK Tinker AFB военный. `BusinessAviationTerminal` на KOKC.
  - **Nairobi (KE, HKJK):** CSV 2 504 400 → 5 300 400 (метро с Kiambu, Ruiru, Thika, ~5,3M, приблизительно). Ruiru (93 200) и Thika
    (96 600) меньше 100k, `del` не ставим. Страна Kenya создаётся импортом. HKJK (size 4124) через `World25_009` и затем вручную
    `create-links`, имя `Nairobi Jomo Kenyatta`. HKNW Wilson в мир не берём по решению; HKRE Eastleigh (военная база Moi AB) не берём.
  - **Kazan (RU, UWKD):** CSV 1 108 100 → 1 600 100 (агломерация ~1,6M, приблизительно), `del Zelenodolsk` (100 100, ~20 nm).
    UWKD (size 6976) автоматически, имя `Kazan` оставлено.
  - Attraction не ставим. Бизнес-терминал только на KOKC.
- **VTSP, YPPH - Phuket и Perth (VATSIM-бэклог, 2026-10-18):**
  - **Phuket (TH, VTSP):** по прецеденту островов (Malta, Gran Canaria) одна строка на остров: CSV 82 800 → 420 800 (провинция
    Phuket, оценка приблизительная). VTSP (size 3000, меньше 5000) через `World25_009`, линк вручную (`create-links`), имя
    `Phuket Intl`. VTSG Krabi (материк, 40 nm) не берём. `BusinessAviationTerminal` на VTSP, attraction 15.
  - **Perth (AU, YPPH):** CSV 1 412 900 → 2 300 900 (Greater Perth ~2,3M, приблизительно), пригородов ≥100k нет, `del` нет.
    YPPH (size 5616) создаётся автоматически, имя `Perth Intl` оставлено. YPEA Pearce (военная база RAAF, в `icaodata` стоял
    `civil`, size 5881 попал бы в автоимпорт): тип изменён на `military`. YPJT Jandakot (GA/бизнес) в мир не берём по решению.
    Терминалы и attraction для Perth не ставим.
- **LYBE - Belgrade (VATSIM-бэклог, 2026-10-18) и чистка данных бывшей Югославии:**
  - **Чистка CSV:** блок городов бывшей Югославии (232 города, код `YU`) был записан в `city-population.csv` дважды, под именами `Montenegro`
    и `Serbia` (по сути идентичные строки, Beograd дважды). Лишний блок `Montenegro,YU` удалён, оставшиеся города разнесены по
    странам: `Serbia,RS` (218), `Kosovo,XK` (10: Pristina, Prizren, Pec, Dakovica, Kosovska mitrovica, Gnjilane, Urosevac,
    Kosovo polje, Orahovac, Podujevo), `Montenegro,ME` (4: Podgorica, Niksic, Pljevlja, Ulcinj). Аэропорты со страной
    `Serbia and Montenegro` в `icaodata.csv` (13 штук) отнесены к странам по ближайшему городу. `Beograd` → `Belgrade`.
  - **Belgrade (RS):** 1 126 900 → 1 700 900 (городская территория ~1,7M, приблизительно). Пригороды (Pancevo 77k, Borca 35k и др.)
    меньше 100k, `del` нет. LYBE (size 3397) через `World25_009`, линк вручную (`create-links`), имя `Belgrade Nikola Tesla`.
    LYBT Batajnica (военная авиабаза с типом `civil`, size 5917) помечен `military`. Страна Serbia создаётся импортом, Kosovo и
    Montenegro появятся вместе со своими городами. Терминал и attraction не ставим.
- **SBGL - Rio de Janeiro (VATSIM-бэклог, 2026-10-18):** CSV разбивал агломерацию на муниципалитеты. Rio de Janeiro 6 150 200 → 13 000 200
  (метро ~13M, приблизительно), имя `Rio de janeiro` → `Rio de Janeiro`. `del` на 10 муниципалитетов ≥100k в 25 nm от ядра: Duque de caxias,
  Sao joao de meriti, Niteroi, Nilopolis, Belford roxo, Sao goncalo, Nova iguacu, Mage, Queimados, Itaborai. Petropolis (~24 nm, граничный
  случай), Teresopolis, Nova friburgo, Volta redonda остаются отдельными. SBGL (size 7188) создаётся автоматически, имя
  `Rio de Janeiro Galeao`. SBRJ Santos Dumont, SBJR Jacarepagua (GA/бизнес), SBAF Campo dos Afonsos (военная) в мир не берём.
  Терминалы и attraction не ставим.
- **VRMM - Male (VATSIM-бэклог, 2026-10-18):** Мальдивы, остров-государство. Male 83 500 → 250 800 (Greater Male с Hulhumale и соседними
  островами, оценка приблизительная); остальные острова мелкие (самый крупный Hithadhoo 9 800), не трогаем. Страна Maldives создаётся
  импортом. VRMM (size 3215, меньше 5000) через `World25_009`, линк вручную (`create-links`), имя `Male Intl` оставлено. VRMG Gan
  и VRMH Hanimaadhoo не берём. `BusinessAviationTerminal` на VRMM, attraction 30.
- **OLBA - Beirut (VATSIM-бэклог, 2026-10-18):** `Bayrut` 1 185 300 → `Beirut` 2 500 300 (Greater Beirut вместе с Sidon, оценка
  приблизительная), `del Sayda'` (150 900, ~19 nm от центра). Juniyah, B#abda, #alayh меньше 100k, `del` не ставим. Sur (Tyre, 118 500, ~35 nm)
  и Tarabulus (Tripoli, 215 500, ~41 nm) остаются отдельными. Damascus и города Израиля рядом не берём (граница). OLBA (size 10491)
  создаётся автоматически, имя `Beirut Intl` оставлено. OLRA Rayak AB и OLKA Rene Mouawad AB (`military`) не импортируются. Страна
  Lebanon создаётся импортом. Терминалы и attraction не ставим.
- **OKKK - Kuwait City (VATSIM-бэклог, 2026-10-18):** `OKKK` в бэклоге оказался не кодом района полётной информации, а **новым ICAO-кодом Kuwait
  International** (смена `OKBK` → `OKKK` в феврале 2023, IATA `KWI`). В `icaodata.csv` строка `OKBK` переименована в `OKKK` (координаты и
  размер те же, `size 6916`), отсюда аэропорт попадает в автоимпорт. В `city-population.csv` единого города не было: агломерация
  разбита на десятки районов. Ядро `Al-kuwayt` (38 400, 9 nm) → `Kuwait City` 3 100 400 (агломерация ~3,1M, приблизительно), `del` на районы ≥100k:
  As-salimiyah 152 100, Jalib as-suyuh 142 700, As-sabah#iyah 131 200, H#itan-al-janubiyah 127 700; районы меньше 100k не трогаем. Страна
  Kuwait создаётся импортом. Военные OKAJ, OKAS уже `military`; Z19M (дорожная полоса) не берём. Имя `Kuwait Intl` оставлено.
  `BusinessAviationTerminal` на OKKK, attraction не ставим.
- **LFML - Marseille, ENTC - Tromso, UMKK - Kaliningrad, RPVM - Cebu (VATSIM-бэклог, 2026-10-19):** все четыре тип 2 (нового города в мире нет).
  **Marseille** 826 300 → 1 900 300 (aire d'attraction Marseille-Aix, ~1,9M, приблизительно), `del Aix-en-provence` (139 000, 14 nm от Marseille).
  Toulon (161 600, 26 nm), Nimes, Avignon, Arles остаются отдельными рынками. LFML (size 5933) создаётся автоматически, имя
  `Marseille Provence`; Les Milles и военные (LFMY, LFMI, LFTR и др.) не берём. **Tromso** 52 100 → 77 100 (~77k), ENTC (size 2384, шаг `009` и
  `create-links`), имя `Tromso Langnes`. **Kaliningrad** 434 700 → 650 700 (агломерация ~650k), UMKK (size 2501, `009` и `create-links`), имя
  `Kaliningrad Khrabrovo`; Z25W Chkalovsk (10 nm) не трогаем (решение пользователя: `military` не ставим), Klaipeda и Elblag за границей отдельные.
  **Cebu** 776 600 → 3 000 600 (Metro Cebu ~3,0M), `del Mandaue` (309 700) и `del Lapu-lapu` (218 100) по 2 nm от ядра; **Toledo** (152 500, 16 nm,
  за горами) по решению пользователя остаётся отдельным городом; RPVM (size 3318, `009` и `create-links`), имя `Cebu Mactan`; RPVT
  Tagbilaran не берём. `BusinessAviationTerminal` только на LFML, attraction не ставим. Шаги `009`, `012`, `013` включены на деплой.
- **LEBB - Bilbao, LXGB - Gibraltar, GMMX - Marrakech, EFRO - Rovaniemi, YPAD - Adelaide, ZGGG - Guangzhou (VATSIM-бэклог, 2026-10-20):** все шесть типа 2 (города в мире нет), аэропорты размером <5000, поэтому идут через `009` + `create-links`.
  **Bilbao** 343 100 → 1 000 100 (Greater Bilbao ~1,0M, приблизительно); пригороды (Barakaldo 90k, Getxo 80k) <100k, `del` не нужен; Vitoria, Donostia, Santander остаются отдельными рынками. Имя аэропорта `Bilbao` (как в `icaodata`).
  **Gibraltar:** города в CSV не было, добавлен seed `Gibraltar,GI,Gibraltar,270000` (Campo de Gibraltar: Gibraltar, La Linea, Algeciras, San Roque; страна GI новая), `del Algeciras` (102 100, 5 nm от аэропорта). LXGB в `icaodata.csv` был `military` (Gibraltar AB), тип исправлен на `civil`, имя `Gibraltar` (через `013`). Ceuta и Tanjah через пролив не линкуем.
  **Marrakech:** в CSV `Marrakus` 848 000 переименован в `Marrakech`, 848 000 → 1 348 000 (агломерация ~1,35M), страна Morocco новая в мире, имя аэропорта `Marrakech Menara`, attraction не ставим.
  **Rovaniemi** 36 600 → 62 600 (муниципалитет), attraction 7 (Santa Claus Village), EFKE Tornio, EFKM, Z11E не берём.
  **Adelaide** 1 089 700 → 1 389 700 (Greater Adelaide ~1,4M), имя `Adelaide` (вместо `Adelaide Intl`); YPED Edinburgh (Military, тип `civil` в `icaodata`) не берём, YPPF Parafield не берём.
  **Guangzhou** 3 244 900 → 13 944 900 (агломерация ~14M), `del Foshan` (496 300, 12 nm) и `del Daliang` (156 700, 21 nm); Dongguan (380 700, 27 nm) остаётся отдельным рынком (решение пользователя), Zhuhai/Aomen/Jiangmen/Huicheng не трогаем. Имя `Guangzhou Baiyun`.
  Терминалы `BusinessAviationTerminal` не создаём (решение пользователя). Шаги `009`, `012`, `013` включены на деплой. После деплоя: `create-links` для LEBB, LXGB, GMMX, EFRO, YPAD, ZGGG и attraction 7 для Rovaniemi.
- **LFBO - Toulouse, WADD - Denpasar (Bali), SKBO - Bogota, CYEG - Edmonton, ZSAM - Xiamen, VAAH - Ahmedabad, MDPC - Punta Cana (VATSIM-бэклог, 2026-10-21):** все семь типа 2 (городов в мире нет, ближайшие далеко), население по предложениям пользователя (оценки агломераций, приблизительные).
  **Toulouse** 417 400 → 1 400 400 (аглом. ~1,4M), `del` не нужен (Montauban 51k, Colomiers 30k), LFBO size 6523 создаётся и линкуется автоматически; LFBF Francazal (military) не берём; имя `Toulouse Blagnac`.
  **Denpasar:** в `icaodata.csv` Bali Intl был под старым кодом `WRRR`, строка исправлена на `WADD` (size 3000, поэтому `009` + `create-links`); 511 000 → 2 511 000 (Sarbagita ~2,6M), Singaraja (96k, 38 nm) отдельный рынок; имя `Denpasar Ngurah Rai`, attraction 8.
  **Bogota** 6 981 500 → 11 981 500 (метро ~12M), `del Soacha` (304 600, 9 nm); Facatativa 91k, Zipaquira 88k и мелкие остаются; страна Colombia в мире новая; имя `Bogota El Dorado`; SKGY Guaymaral и военные SK* не берём.
  **Edmonton** 812 400 → 1 500 400 (CMA ~1,5M); CYXD Edmonton City Centre закрыт с 2013 года, не берём; имя аэропорта `Edmonton Intl` (как в `icaodata`).
  **Xiamen** 535 600 → 5 300 600 (муниципалитет ~5,3M); Zhangzhou (519k, граница 25 nm) по решению пользователя **не вливаем**, остаётся отдельным рынком; RCBS Kinmen за проливом не линкуем; `009` + `create-links`, имя `Xiamen Gaoqi`.
  **Ahmedabad:** в CSV `Ahmadabad` переименован в `Ahmedabad`, 3 724 300 → 8 024 300 (аглом. ~8M), `del Gandhinagar` (207 500, 14 nm); Nadiad (204k, граница 25 nm) по решению пользователя **не вливаем**; `009` + `create-links`, имя `Ahmedabad Sardar Vallabhbhai Patel`.
  **Punta Cana:** в 30 nm нет города ≥100k (Higuey 67k, La Romana 171k в 36 nm), добавлен seed `Dominican republic,DO,Punta Cana,130000,18.58,-68.40` (по решению пользователя, Higuey не трогаем); страна DO в мире новая; MDPC size 3103 (`009` + `create-links`), attraction 5; MDLR Casa de Campo не берём.
  Терминалы `BusinessAviationTerminal` не создаём (решение пользователя). Шаги `009`, `012`, `013` включены на деплой. После деплоя: `create-links` для WADD, ZSAM, VAAH, MDPC и attraction Denpasar 8, Punta Cana 5.

- **Партия 2026-10-22 (EGNT, DTTA, GMMN, OOMS, тонкие регионы по VATSIM-бэклогу):**
  - **Newcastle upon Tyne (GB, EGNT):** CSV 190 600 → 1 100 600 (Tyneside + Wearside). `del Sunderland` (183 800, 13 nm). Gateshead, South Shields (<100k) не трогаем; Middlesbrough (147k, ~32 nm, Teesside EGNV) отдельный рынок. Имя по решению пользователя «Newcastle upon Tyne» (в CSV есть южноафриканский Newcastle 309k, он не импортируется, но при `f1TourFixes` по имени был бы неоднозначен). EGNT size 2326: `009` + `create-links`. Имя аэропорта «Newcastle Intl».
  - **Tunis (TN, DTTA):** CSV 695 500 → 2 700 500 (Greater Tunis). `del Aryanah` (217 600, 3 nm). Страны Tunisia в мире не было, создаётся импортом. DTTA size 7868 подтянется автоматически. Имя «Tunis Carthage». DTTB (Sidi Ahmed, 32 nm, числится civil, на деле авиабаза) и мелкие DTTI не берём.
  - **Casablanca (MA, GMMN):** население оставлено как в CSV, 3 672 900 (по решению пользователя «поменьше», агломерацию 4,3M не ставим). Имена в CSV исправлены: `Ad-d#ar-al-bayda` → Casablanca, `H#uribkah` → Khouribga (отдельный рынок, 44 nm). `del Settat` (124 800, граничный случай по расстоянию, по решению пользователя помечен). Rabat/Salé (GMME) не вливаем. GMMN size 3744: `009` + `create-links`. Имя аэропорта «Casablanca Mohammed V». GMMC Anfa и GMMT Tit Mellil (GA) не берём.
  - **Muscat (OM, OOMS):** в CSV города Muscat не было. Ядром взят `Masqat` (56 400), переименован в Muscat, население 1 400 400 (агломерация ~1,4M, координаты ядра). `del As-sib` (248 200), `del Bawsar` (172 200), `del Ruwi` (116 600). Barka (78k) и Matrah (78k) <100k, не трогаем. Страны Oman в мире не было. OOMS size 4339: `009` + `create-links`. Имя «Muscat Intl». Военные Izki и Firq не берём.
  - Терминалы бизнес-авиации и attraction не ставим (решение пользователя). Импорты `009`, `012`, `013` в `Application.main` раскомментированы на время деплоя, после деплоя закомментировать обратно.

- **Партия 2026-10-23 (ROAH, LJLJ, EETN, KEYW, YBCG, тонкие регионы по VATSIM-бэклогу):**
  - **Naha (JP, ROAH):** CSV 303 500 → 1 000 500 (метро Окинавы). `del Urasoe` (107 500, 6 nm), `del Okinawa` (123 200, 12 nm); Ginowan (89 700) не трогаем. Аэропорт остаётся `Naha`; ROTM Futenma MCAS и RODN Kadena AB (military) не берём. Ближайший город мира Taipei (343 nm), это новый остров. ROAH size 3009 идёт через `009`.
  - **Ljubljana (SI, LJLJ):** CSV 258 700 → 560 700 (метро). Страны Slovenia в мире не было, создаётся импортом. Аэропорт переименован в `Ljubljana Joze Pucnik` (`013`). LOWK Klagenfurt (26 nm, другая страна) не берём. LJLJ size 3296, через `009`.
  - **Tallinn (EE, EETN):** CSV 396 000 → 600 000 (метро). Страны Estonia в мире не было. Аэропорт переименован в `Tallinn Lennart Meri`. Helsinki в 46 nm через море, поэтому `create-links` для EETN вызываем с `maxDistance=20`, иначе он залинкуется с Helsinki. EETN size 3063, через `009`.
  - **Key West (US, KEYW):** в 60 nm нет городов CSV, добавлен seed `United States,US,Key West,80000,24.56,-81.78` (Флорида-Кис целиком, по аналогии с островами Malta, Sint Maarten). Аэропорт `Key West Intl` (имя из `icaodata`); KNQX Key West NAS (military) и 61FD (гидроплан) не берём. KEYW size 1462, через `009`. **Attraction 5** (решение пользователя): ставится после деплоя через `/admin/flows/set-attraction` (id города из `/geo/cities`).
  - **Gold Coast (AU, YBCG):** CSV `Gold coast` 457 900 → `Gold Coast` 700 900 (метро с Tweed Heads; в CSV имя исправлено, так что переименование города не нужно). Влитых нет. Аэропорт `Gold Coast Coolangatta` (`013`), YBCG size 2630, через `009`. Brisbane в 50 nm (граница радиуса), поэтому `create-links` для YBCG с `maxDistance=30`. **Attraction 7** (решение пользователя).
  - Терминалы бизнес-авиации не создаём (решение пользователя). Импорты `009`, `012`, `013` в `Application.main` раскомментированы на деплой; после деплоя и `create-links` (ROAH, LJLJ, EETN `maxDistance=20`, YBCG `maxDistance=30`, KEYW) закомментировать обратно и закоммитить без пуша. Блоки вызовов лежат в `admin-requests.http` (не в git).

- **Имена стран приведены к правильным (2026-10-23):** в `city-population.csv` (1689 строк по коду страны) и в `icaodata.csv` (355 строк) названия стран выровнены между собой и с
  английскими официальными (подробности и список в [city-airport-playbook.md](city-airport-playbook.md), раздел 10). В мире три страны с неправильными именами (`Czech republic`,
  `Dominican republic`, `Rumania`) получат правильные через `RenameCountries` в `World25_013` при ближайшем деплое с включённым `013` (сейчас `013` закомментирован, как обычно).
  Флаги на сайте: `simforge-web/js/flags.js` дополнен новыми именами (старые оставлены: сервис аэропортов отдаёт свои имена).

- **Партия 2026-10-24 (NZWN, Океания по VATSIM-бэклогу):**
  - **Wellington (NZ, NZWN):** CSV 167 700 → 440 700 (агломерация с Lower Hutt, Upper Hutt и Porirua; хвост сохранён). `del Lower hutt` (96 200 в CSV, 9 nm; реально около 112 тыс.) по решению пользователя, Porirua (48 200) не трогаем. Страна New Zealand уже в мире (там был Auckland). Аэропорт `Wellington Intl` (имя из `icaodata`), NZWN size 2061, через `009`; NZPP Paraparaumu, NZOM, NZMS мелкие, NZWB Woodbourne (military) не берём. Терминал бизнес-авиации и attraction не ставим (решение пользователя). `create-links?icao=NZWN` по умолчанию (других городов мира рядом нет). Импорты `009`, `012` в `Application.main` раскомментированы на деплой; после деплоя и `create-links` закомментировать обратно и закоммитить без пуша.
  - **Baku (AZ, UBBB, Юго-Западная Азия по VATSIM-бэклогу):** в CSV `Baki` переименован в `Baku` (город новый, переименование в мире не нужно), население 1 112 900 → 2 600 900 (агломерация Баку с Абшероном и Сумгаитом; хвост сохранён). `del Sumqayit` (263 600, 16 nm от ядра); Qaracuxur, Bakixanov, Rasulzade, Bilajari (меньше 100 тыс.) не трогаем. Страны Azerbaijan в мире не было, создаётся импортом. UBBB size 6206 (больше 5000): создаётся и линкуется автоматически с `012`, отдельно в `009` не нужен; в `icaodata` у него имя `Bina`, переименован в `Baku Heydar Aliyev` через `013`. UB0C Nasosnaya (мелкий) не берём. Терминал бизнес-авиации и attraction не ставим (решение пользователя). Импорты `012` и `013` в `Application.main` раскомментированы на деплой (вместе с `009` и `012` партии Wellington); после деплоя и проверки связей закомментировать обратно и закоммитить без пуша.

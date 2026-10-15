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
| RKSM | Seoul / Domestic (Seoul Air Base, Seongnam) | Seoul | военная база ВВС (K-16), VIP/правительственные рейсы, в мире трафика нет; название в `icaodata` — артефакт |
| RKSW | Suwon (Suwon Air Base) | Seoul | военная база ВВС (K-13), регулярных гражданских рейсов нет, в мире трафика нет |
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

### 2. Аэропорты в мире без привязки к городу — закрыто

Все 19 аэропортов, созданных через `World25_009_create_some_airports` без города, привязаны к городам (2026-10-15):
LPMA, ENBR, EGJJ, EVRA, LGKR, LPFR, LGIR, LTAI, LGSK, LICJ, LIRN, LGRP, LDDU, LDSP, LEAL, LGKO, LGSM, LFKF, EFKS.
Дополнительно созданы LFKJ и LFKB. Названия аэропортов приведены к читаемым в шаге `World25_013`
(`RenameAirports`): LIRN `Capodichino`, LFKJ `Ajaccio Napoleon Bonaparte`, LFKB `Bastia Poretta`, LGKR
`Corfu Ioannis Kapodistrias`, LGIR `Heraklion Nikos Kazantzakis`. Подробности по каждому городу — в разделе «Выполнено». В мире сейчас
160 городов и 219 аэропортов без бесхозных.

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

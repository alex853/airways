# Docs

Рабочая документация проекта: ключевые решения, логика, бизнес-логика.
Обновляется по мере изучения/изменения кода.

## Оглавление

- [busy-birds.md](busy-birds.md) — BusyBirds: корпоративные/бизнес-авиа миссии (контроллер, планирование, бронирование)
- [city-airport-playbook.md](city-airport-playbook.md) — playbook: как добавлять города и аэропорты (анализ, население, имена, острова, терминалы, порядок работы)
- [city-reuse.md](city-reuse.md) — реюз города: запись города получает другое место (имя, страна, координаты, население), потоки пересчитываются, `GET /admin/geo/city/reuse`
- [aircraft-types-playbook.md](aircraft-types-playbook.md) — playbook: как добавлять типы самолётов (flight-stats, мир `008`, макет салона, `refdata-aircrafts`, порядок работы)
- [flight-dashboard-texts.md](flight-dashboard-texts.md) — тексты-пояснения этапов дашборда полёта (часть про пилота и часть про пассажиров), как они устроены
- [airports-record-extension.md](airports-record-extension.md) — запись аэропорта (сделано 2026-11-04): `FixedLengthString` для кодов, `countryId`, флаги excluded и hub, админская карта, что фильтруется по excluded
- [change-tracking-cache.md](change-tracking-cache.md) — кэш ответов по отметкам изменений хранилищ (`Storage.getLastChangedAtMillis`, `ChangeTrackedCache`, `CachedJson`, gzip)
- [world-editor.md](world-editor.md) — редактор мира: admin/map.html, админский API /admin/world (город, аэропорт, attraction, население, бизнес-терминал)
- [cities.md](cities.md) — города и население: CSV, соглашение `del`, операция «актуализировать и укрупнить мажорный сити»
- [vatsim-backlog.md](vatsim-backlog.md) — VATSIM-бэклог: формат отчёта, как получить, приоритеты регионов
- [heartbeat-index.md](heartbeat-index.md) — индекс по heartbeat (`HeartbeatIndex`, `Storage.withHeartbeatIndex`) для журни и рейсов, circuit breaker в `Processing`
- [boarding-planning.md](boarding-planning.md) — планирование чекина и посадки по вместимости борта, Preflight по длительности посадки (решено, не реализовано); дебординг дробным счётчиком (реализовано)
- [journeys.md](journeys.md) — journeys: жизненный цикл, локация, решения (ItinerariesDone, TooLateToBoard), далёкое будущее
- [journeys-hub-mode.md](journeys-hub-mode.md) — перелёты через хабы (hub mode): идея, статус, драйран, план (отложено)
- [storage-quarantine.md](storage-quarantine.md) — Storage: карантин удалённых записей (не переиспользовать id сразу), дизайн
- [storage-paged.md](storage-paged.md) — Storage: страничный режим (`paged()`), адаптер байтов, отсечка пустого хвоста (`tryTrimTail`), лог
- [storage-extension.md](storage-extension.md) — как расширять запись хранилища (четыре выкатки: второе хранилище, копия по полям, проверка, своп, удаление старого; проверено на персонах) и `FixedLengthString`
- [persons-record-extension.md](persons-record-extension.md) — расширение записи персоны 32 → 40 байт (второе хранилище `persons2`): раскладка, как устроено, шаги
- [pilot-airline-travel.md](pilot-airline-travel.md) — Travel by airline: дата-модель (ссылка персоны на журни, флаг, бит журни пилота, индекс), события, что дальше
- [future-plans.md](future-plans.md) — дальние планы: пересчёт ETA в полёте и другие отложенные темы
- [users.md](users.md) — пользователи: свой storage (токен, персона, VATSIM CID), админские методы создания и токена
- [avatars.md](avatars.md) — список иконок и четыре флага (forbidden, common, pilot, special), файл вне classpath, админская страница; план аватарок пассажиров
- [calendar.md](calendar.md) — календарь рейсов пилота: ссылка `.ics` для подписки (Sqids + HMAC), секрет в файле, перегенерация админом, кнопка на ростере
- [g7-state-log.md](g7-state-log.md) — `POST /g7/state-log`: журнал событий инструмента G7 в файлы по дням (папка `./g7/`)
- [braindump-schedule-pay-pilots.md](braindump-schedule-pay-pilots.md) — брейндамп 2026-10-18: окна чекина/боардинга, выбор времени отбытия, оплата пилота, Person/Pilot, логбук (идеи, не решения)
- [status-and-backlog.md](status-and-backlog.md) — статус и бэклог: брейндамп по пунктам (сделано / частично / не сделано), отложенное и дальние планы, напоминания по проду, тудушки в коде (сверка 2026-10-22)
- [persons-pilots.md](persons-pilots.md) — Person: единая сущность пилотов и cabin crew (состав полей, связь с User, сценарий автономного пилота), дизайн, не реализовано
- [clients-and-flight-modes.md](clients-and-flight-modes.md) — клиенты (веб, G7 FlightBag), режимы флайт-дашборда (manual, SimTracker, VATSIM), BusyBirds и ShadowJet, стартовый экран (описание и планы)
- [use-cases.md](use-cases.md) — основные юзкейсы, которые обязана поддерживать система (BusyBirds через G7 автоматически; через веб, ручной режим и варианты), список изменений и оценка
- [diversions-and-alternates.md](diversions-and-alternates.md) — посадка вне планового аэропорта: принцип «довезти до точки назначения рейса», альтернейты и кнопка Divert, решение на блокс-оне, оплата и статистика (решения 2026-11-01, не реализовано)
- [flight-contracts.md](flight-contracts.md) — идея: два договора (перевозка = Journey с ценой, найм = PilotContract на рейс), оплата пилота по квалификации, счета и операции (не реализовано)
- [time-storage-review.md](time-storage-review.md) — ревью: как в дата-модели хранятся времена, даты и длительности (форматы, наблюдения, предложения)

## Правила ведения

- **Ветки:** основной веткой проекта считается `lightweight` (из неё деплой на прод, в неё мержим готовые ветки). `master` **не трогаем**: не мержим в неё, не пушим, не берём за основу.

- Один файл на предметную область/фичу.
- Фиксируем: что делает, почему так решено, известные TODO и ограничения.
- Расхождения кода и дока исправляем при каждом заходе в соответствующий код.

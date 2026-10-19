# Docs

Рабочая документация проекта: ключевые решения, логика, бизнес-логика.
Обновляется по мере изучения/изменения кода.

## Оглавление

- [busy-birds.md](busy-birds.md) — BusyBirds: корпоративные/бизнес-авиа миссии (контроллер, планирование, бронирование)
- [city-airport-playbook.md](city-airport-playbook.md) — playbook: как добавлять города и аэропорты (анализ, население, имена, острова, терминалы, порядок работы)
- [airports-record-extension.md](airports-record-extension.md) — план расширения записи аэропорта: `countryId`, `exclusion`, резерв, миграция хранилища (не реализовано)
- [change-tracking-cache.md](change-tracking-cache.md) — кэш ответов по отметкам изменений хранилищ (`Storage.getLastChangedAtMillis`, `ChangeTrackedCache`, `CachedJson`, gzip)
- [cities.md](cities.md) — города и население: CSV, соглашение `del`, операция «актуализировать и укрупнить мажорный сити»
- [vatsim-backlog.md](vatsim-backlog.md) — VATSIM-бэклог: формат отчёта, как получить, приоритеты регионов
- [heartbeat-index.md](heartbeat-index.md) — индекс по heartbeat (`HeartbeatIndex`, `Storage.withHeartbeatIndex`) для журни и рейсов, circuit breaker в `Processing`
- [boarding-planning.md](boarding-planning.md) — планирование чекина и посадки по вместимости борта, Preflight по длительности посадки (решено, не реализовано); дебординг дробным счётчиком (реализовано)
- [journeys.md](journeys.md) — journeys: жизненный цикл, локация, решения (ItinerariesDone, TooLateToBoard), далёкое будущее
- [journeys-hub-mode.md](journeys-hub-mode.md) — перелёты через хабы (hub mode): идея, статус, драйран, план (отложено)
- [storage-quarantine.md](storage-quarantine.md) — Storage: карантин удалённых записей (не переиспользовать id сразу), дизайн
- [future-plans.md](future-plans.md) — дальние планы: пересчёт ETA в полёте и другие отложенные темы
- [users.md](users.md) — пользователи: временное хранилище на property-файлах, токены, последняя посадка
- [braindump-schedule-pay-pilots.md](braindump-schedule-pay-pilots.md) — брейндамп 2026-10-18: окна чекина/боардинга, выбор времени отбытия, оплата пилота, Person/Pilot, логбук (идеи, не решения)
- [persons-pilots.md](persons-pilots.md) — Person: единая сущность пилотов и cabin crew (состав полей, связь с User, сценарий автономного пилота), дизайн, не реализовано
- [time-storage-review.md](time-storage-review.md) — ревью: как в дата-модели хранятся времена, даты и длительности (форматы, наблюдения, предложения)

## Правила ведения

- Один файл на предметную область/фичу.
- Фиксируем: что делает, почему так решено, известные TODO и ограничения.
- Расхождения кода и дока исправляем при каждом заходе в соответствующий код.

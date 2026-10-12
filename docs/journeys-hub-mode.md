# Journeys: перелёты через хабы (hub mode) — отложено

Статус: **отложено**, возвращаемся когда накопятся данные драйрана (`hub dry run` в логе).

## Идея

Если journey в течение некоторого времени не может улететь напрямую, переводим её во «второй» режим: она
соглашается лететь в сторону любого хаба (аэропорта с большим числом рейсов: EGLL, EDDF, OMDB и т.п.).
Долетев до хаба, journey заново ищет перелёт из текущего местоположения в `toCity`; хабов может быть несколько.
Позже: выбирать хаб, который приближает к точке назначения.

## Где что живёт сейчас

- **`ShadowJetLogic.provideTransportFlightIfRequired`** — когда появляется VATSIM-рейс, забирает journeys в
  `LookingForTickets`, у которых `locationCityId ∈ города вылета` и `toCityId ∈ города прилёта` (раньше было по
  `fromCityId`).
- **`JourneyProcessor.lookingForTickets`** — исторический механизм «рейсов по расписанию» (scheduled flights),
  оставляем: прямой рейс → стопавер (2 ноги) → 3 попытки по 1–3 суток → `couldNotFindTickets`. Ищет от
  `locationCityId`.
- Ограничения модели: 2 транспортных рейса на journey (`transportFlight1Id`, `transportFlight2Id`),
  `attemptCounter` 2 бита (макс 3). Свободно: бит 4 и 7 в `typeMode`, биты 4–6 в `status`, 12 reserved байт.

## Уже сделано (смежное)

- Все места подбора переведены с `fromCityId` на `locationCityId`: `ShadowJetLogic`, `JourneyProcessor.lookingForTickets`,
  `BusyBirdsMissionControl`, `BusyBirdsController`.
- `switchToReturnTrip` ставит `locationCityId = новый fromCityId`.
- `JourneyProcessor.itinerariesDone`: если `locationCityId != toCityId` (посадка в другом аэропорту/городе), journey
  не считается завершённой, а возвращается в `LookingForTickets` (`attemptCounter = 0`, heartbeat 1–3 суток).
  Общий хелпер `JourneyControl.scheduleLookingForTickets`.
- Драйран в `ShadowJetLogic.logHubDryRun` (только логирование, мир не меняется), хабы захардкожены в `HUB_ICAOS`
  (EGLL, EDDF, EHAM, KJFK, OMDB). Сообщение на рейс, летящий в хаб:
  `hub dry run - f/m #… - A -> B, a/c type … - … getting closer to destination: N, not getting closer: M,
  locationCityId != fromCityId: X out of Y, locationCityId == 0: Z`.

## Что показал драйран (первые логи)

- «Любой хаб» без направления работает плохо: на EGLL/OMDB/KJFK лишь ~5–36% журней в городе вылета приближаются к
  цели (EDDF иногда до 90%). Хаб надо выбирать в сторону цели.
- ~26 900 журней в `LookingForTickets` по миру; `locationCityId != fromCityId` ~8% (2195), из них `== 0` ~104
  (посадка в аэропортах без городов). После фикса `switchToReturnTrip` счётчик падает.

## План (когда вернёмся)

1. Биты в `Journeys`: флаг hub mode, счётчик хопов (2–3 бита).
2. Определение хабов: сначала статичный список, потом динамика по `Airport2AirportDailyFlightStat`.
3. `JourneyProcessor.lookingForTickets`: при исчерпании попыток — не отказ, а hub mode; в hub mode искать рейс на
   любой хаб (кроме текущего аэропорта).
4. `ShadowJetLogic.provideTransportFlightIfRequired`: два прохода (сначала прямые, потом hub mode), чтобы старые
   журни в hub mode не вытесняли прямые.
5. После посадки в хабе: не `ItinerariesDone`, а снова поиск от `locationCityId` (уже сделано через
   `itinerariesDone` + `isToCityReached`).
6. Success rate потока: не обновлять на промежуточных ногах, финальный успех — при достижении `toCity`.
7. Лимит хопов и дедлайн; статистика `FlightStats.event`; фича-флаг.
8. Умный выбор хаба: хаб ближе к цели, и из него есть направление в аэропорты `toCity` (по daily stats).

## Открытые вопросы

- Статичный список хабов или сразу динамика по статистике.
- Максимум хопов (2 или 3), окно ожидания в hub mode.
- Что делать с журнями, у которых `locationCityId == 0` (ближайший город по координатам или завершать).

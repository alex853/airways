# Дальние планы

Идеи, к которым хотим вернуться не сейчас. Здесь коротко: что, зачем и откуда начинать. Отдельные большие темы
имеют свои доки.

## Ближайший план: надёжность журней (разобрать)

### 1. JustArrived без `locationCityId` и потеря t/f или f/m

`JourneyControl.deboard` переводит журню в `JustArrived` с heartbeat до часа вперёд, а `locationCityId` ставится только
позже в `justArrived` (`moveToCityNextToLandingAirportOrToCity`, который читает `transportFlight1` и f/m через
`orElseThrow`). Если в это окно t/f или f/m удалены (`FlightCleanup`, отмена), обработка падает исключением;
`processJourney` уже обнулил heartbeat, поэтому журня остаётся в `JustArrived` с `heartbeat = 0` и не обрабатывается
никогда.

Идеи:
- Ставить `locationCityId` уже в `deboard` (момент реального прилёта), а `justArrived` оставить только для
  перехода по статусам.
- В `justArrived` и `moveToCityNext...` не использовать `orElseThrow`: если t/f или f/m нет, переводить в `orphaned`
  (или сразу `ItinerariesDone`, раз журня уже доехала и локация известна).
- Ловить исключение в `processJourney` для одной журни и переводить её в `Orphaned` (чтобы одна сломанная не
  вешалась навсегда и не роняла цикл).

### 2. Журни в `WaitingForDeboarding` без heartbeat

Появляются журни в `WaitingForDeboarding` с нулевым heartbeat, они «висят долго». Heartbeat им ставит
`scheduleDeboardingAtRandomTime` (случайно в пределах `DEBOARDING_DURATION`), `waitingForDeboarding` перепланирует
раз в минуту, если статус t/f неожиданный, и орфанит, если t/f нет. Нулевой heartbeat возникает, когда
обработка падает на `journeyControl.deboard` (`byId(...).orElseThrow()` по t/f) уже после обнуления heartbeat.

Идеи:
- Починить так же, как `WaitingForBoarding`: heartbeat раз в минуту до тех пор, пока статус t/f не позволяет
  деборд, и `orphaned`, если t/f пропал или завис дольше порога.
- Не использовать `orElseThrow` в `deboard` для t/f (журня уже решила деборд, `decreasePaxOnBoard` сделать
  опциональным).
- Общий механизм: если журня в статусе, который ждёт внешнего события, и heartbeat `0`, то периодическая
  страховочная проверка (сейчас частично делает `delete-invalid`, нужна системная). Рассмотреть такой же
  страховочный heartbeat (например раз в час) для `OnBoard`.

## Пересчёт ETA в полёте

**Сейчас.** Расчётное время посадки не пересчитывается в полёте: `FlightTimeline.updateEstimatedTimes` считает
`фактический взлёт + плановая длительность этапа`, поэтому после взлёта оно не меняется вообще (не зависит ни от
скорости, ни от маршрута, ни от позиции). Время прилёта рейса (`plannedArrivalWorldTime`) пишется только при создании
и при ручном `reschedule`. Ватсим-трекер (`PilotContext`) ETA не оценивает.

Где это видно: `AircraftInfoDto.eLdg` (страница самолёта `aircraft.html`, поле `eta`), `MapController`
(`getBlocksOn().getEstimatedTime()` на странице аэропорта), flight board.

**Что хотим.** ETA, которое живёт вместе с полётом: учитывает остаток расстояния и фактическую скорость.

**Из чего собирать (уже есть).**
- Текущая позиция пилота в `PilotContext` / `Aircraft.getLocationLatitude/Longitude`.
- Измеренная путевая скорость: `TrackLeg.calculateGroundspeed(newTrackTrail)` в `SimTracker` (`measuredGs`).
- Координаты аэропорта назначения.

**Идеи по формуле** (выбрать при реализации):
1. `ETA = now + остаток расстояния / путевая скорость` (сглаживание по окну 2–5 минут, в наборе и снижении шумит).
2. Гибрид: до взлёта как сейчас, в полёте `max(остаток плановой длительности, оценка по расстоянию)`, чтобы ETA не
   прыгала в минус.
3. Хранить расчётный ETA отдельным полем рейса/самолёта и обновлять трекером, либо считать на лету в DTO.

**Открытые вопросы.** Какую веху показывать (посадка или blocks-on), влияет ли ETA на планы журней и check-in
(сейчас нет, и это правильно), нужно ли хранить историю изменений ETA.

## SimTracker: сохранение и восстановление контекстов (реализовано, не задеплоено)

Реализация: `SimTrackerContextStore` (JSON `./sim-tracker/contexts.json`, версия 1, атомарная запись) и
`SimTracker.saveContexts()/restoreContexts()`.

- Сохраняется: `position`, `parkingBrake`, `numberOfEnginesRunning`, `trackTrail` (`TrackLeg.toString`), `measuredGs`,
  `flightMissionId`. Не сохраняется: `actions` (пересчитываются в `doChecks`), `airportInfos`.
- Save: раз в 60 с (`SimTrackerBean`) и при остановке бина (`destroy`, после остановки потока). Пока контексты не
  восстановлены (`contextsRestored`), save пропускается, чтобы не затереть файл пустым состоянием.
- Restore: при старте после готовности мира; контексты с позицией старше 30 минут отбрасываются; битый, пустой,
  отсутствующий файл или другая версия не ошибка (лог). Лог: сколько восстановлено / stale / broken.
- `processPosrep`: если старая позиция старше 10 минут, трек начинается заново (скорость не измеряется по разрыву),
  старая позиция остаётся для определения takeoff/landing.
- Тесты: `SimTrackerContextStoreTest` (round trip, null-поля, без позиции, stale, нет файла, битый, пустой, другая
  версия, битый контекст, перезапись).

Остаётся: контексты не удаляются из `userContexts` (пользователь «Connected» вечно), порог устаревания для
`getUserStatus`/`isUserConnected`; чтение времени из posrep (`E0Posrep.time`, `todo ak1`).

## Другие отложенные темы

- Перелёты через хабы: [journeys-hub-mode.md](journeys-hub-mode.md) (идёт hub test).
- Карантин удалённых записей Storage: [storage-quarantine.md](storage-quarantine.md) (дизайн).

## Transport flights: расширить запись, счётчик перевезённых пассажиров

В записи T/F (24 байта) нет резерва. Счётчик перевезённых пассажиров (`paxCarried`, ставится при взлёте в `whenFlightTakeoffs`, пока нигде
не читается) временно живёт в свободных битах 20..29 поля `paxCheckedInOnBoard` (10 бит, 0..1023, ноль значит «неизвестно»).
todo ak2: расширить запись T/F с запасом и вынести счётчик в собственное поле. Подробное объяснение у `Flight.getPaxCarried()`.

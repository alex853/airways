# Flight dashboard: пояснительные тексты по этапам (черновик)

Статус: **черновик текстов на согласование**, в код не вносилось. Пункт 5 текущего спринта (`status-and-backlog.md`): на этапах дашборда под заголовком этапа часто пусто
(например, просто «Preflight»), а нужно объяснять, что сейчас происходит и что делать дальше.

## Как это устроено сейчас

Область под плитками (`areaStageHtml` в `simforge-web/airways/flight-dashboard.html`): заголовок этапа (`STAGE_TITLES`), предупреждение о следующих рейсах, список условий
действий (`checks`) и кнопки. Свои пояснения есть только у: Flying (под Landing и Divert), wrong landing (три колонки), двух стартов, Finish Boarding. Остальное пусто.

## Как предлагается

Под заголовком этапа абзац `action-note` (1-3 предложения): что происходит в мире (пилот, пассажиры, наземные службы) и что делать пилоту. Текст зависит от **статуса рейса
(`fmSt`) + статуса транспортного рейса (`tfSt`) + режима (`mode`: трекер или ручной)**. Подставляются данные рейса: `{dep}`, `{dest}`, `{pdep}` (плановый вылет), `{cinStart}`,
`{brdStart}`, `{brdEnd}`, `{ckdIn}`, `{ponBrd}`, `{tTkts}` (пассажиров всего), `{debEnd}`. Тексты на английском, как весь дашборд.

Цепочка статусов рейса: `Dispatched` → (Start flight) → `Preflight` → (Blocks Off) → `Departure` → (Takeoff) → `Flying` → (Landing) → `Arrival` → (Blocks On) → `Postflight` →
(Finish) → `Finished`. Транспортный рейс идёт своей цепочкой: `Scheduled`, `CheckIn`, `WaitingForBoarding`, `Boarding`, `WaitingForDeparture`, `Departure`, `Flying`, `Arrival`,
`WaitingForDeboarding`, `Deboarding`, `Finished`.

## Тексты

### Dispatched (Before Start Flight), tf Scheduled

> The aircraft is assigned to you and stands at {dep}. The flight is planned for {pdep} UTC. The check-in of the passengers opens at {cinStart} UTC (it goes by itself, you do not
> wait for it). When you are at the airport and ready to begin, start the flight: with the sim tracker the flight follows the sim (Blocks Off, Takeoff, Landing, Blocks On),
> in the manual mode you press these steps yourself.

Если красные условия (пилот не на месте, лимиты, самолёт занят): добавить «Fix the red conditions below first.»

### Preflight

Регистрация обычно открывается задолго до Preflight (время открытия пишем в Dispatched), поэтому подготовка пилота описывается во всех вариантах, где регистрация идёт или ещё впереди.
- **tf Scheduled** (регистрация ещё не открыта, редкий случай):
  > The flight has started. The pilot studies the weather and the route, files the flight plan, walks around the aircraft, sets up the FMS and talks to the ground services
  > about the fuel and the ramp.
- **tf CheckIn** (регистрация идёт):
  > The pilot studies the weather and the route, files the flight plan, walks around the aircraft, sets up the FMS and talks to the ground services about the fuel and the ramp.
  > Meanwhile the passengers are checking in ({ckdIn} of {tTkts} so far). When the pilot is ready to let them on board, press Start Boarding (planned for {brdStart} UTC).
- **tf WaitingForBoarding**:
  > The check-in is over, the passengers are waiting at the gate. When the aircraft is ready, press Start Boarding.
- **tf Boarding** (ручной режим и трекер одинаково):
  > The passengers are boarding ({ponBrd} on board). The boarding ends at {brdEnd} UTC or when everybody is on board. Use the time to finish the preparation.
  > Finish Boarding closes the doors now, but not everybody may be on board.
- **tf WaitingForDeparture** (посадка закончена, Blocks Off ещё не было):
  - трекер: > The doors are closed, the passengers are seated. Release the parking brake and push back: Blocks Off follows the sim.
  - ручной: > The doors are closed, the passengers are seated. When the parking brake is released and the aircraft moves, press Blocks Off.

### Departure (после Blocks Off, до Takeoff)

- трекер: > The aircraft is taxiing out. Takeoff follows the sim when the wheels leave the ground.
- ручной: > The aircraft is taxiing out. Press Takeoff when you start the takeoff run (checks below).

### Flying (над кнопками Landing и Divert)

- трекер: > The aircraft is on its way to {dest}. The sim tracker lands the aircraft where you land it. If you cannot land at {dest}, declare a divert.
- ручной: > The aircraft is on its way to {dest}. Press Landing after the touchdown (not earlier than 90% of the planned flight time). If you cannot land at {dest}, declare a divert.
- после дивёрта (`div`): > You are going to {div} instead of {dest}. The passengers are expected at {dest} all the same, see the warnings above. Cancel divert takes it back.
- рейс без пассажиров (ferry, tfSt null): убрать фразы про пассажиров, остальное то же.

### Arrival (после Landing, до Blocks On)

- трекер: > The aircraft has landed and is taxiing in. Blocks On follows the sim when it stops at the gate and the engines are shut down.
- ручной: > The aircraft has landed and is taxiing in. When it is parked, the parking brake is set and the engines are off, press Blocks On.

### Postflight

- **tf WaitingForDeboarding** (самолёт на стоянке):
  > The aircraft is at the gate. The passengers are waiting to get off. Press Start Deboarding when you are ready.
- **tf WaitingForDeboarding, посадка не в плановом аэропорту**: у этого состояния уже есть три колонки (Continue now, Continue later, Cancel); добавить один общий абзац:
  > The aircraft has landed at {aldgA}, not at {dest}. Decide what happens to the passengers.
- **tf Deboarding**:
  > The passengers are getting off ({ponBrd} left). The deboarding ends at {debEnd} UTC. The flight is finished after that
  > (automatically{, or press Finish Flight when the autofinish is off}).
- **tf Finished, ждём Finish Flight (autoFinish выключен)**:
  > Everybody is off. Press Finish Flight to close the flight and get the pay.
- **tf Finished, autoFinish включён**: > Everybody is off. The flight is being finished automatically.

### Finished

> The flight is finished and paid. The pilot rests at {dest}.

(кнопки «To the pilot» и «Book another mission» уже есть)

## Что решить перед реализацией

1. Тексты показывать только пилоту рейса (как сейчас вся область) или всем: **только пилоту** (область и так для пилота).
2. Где хранить: на сервере (`StatusDto.hint`, тексты на английском в одном месте, видны в тестах и в G7) или в `flight-dashboard.html` (тексты вместе с остальным рендером,
   `?stub=` показывает их без сервера). Для дашборда в G7 (FlightBag) сервер удобнее.
3. Стиль: нейтральный (как выше) или «сюжетный» (как в примере про пилота, FMS, граунд-сервисы). Выше смесь: сюжет на Preflight и Boarding, инструкции на остальных.
4. Показывать ли «Time to departure» и подобные числа в тексте (сейчас они в плитках).

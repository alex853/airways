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

## Как это устроено (сделано 2026-11-05)

Код: `simforge-web/airways/flight-dashboard.html` (`pilotNote`, `passengersNote`, `stageNotesHtml`), вставляется под заголовком этапа, перед предупреждением о следующих рейсах.
Два абзаца с подписями «Flight» и «Passengers». «Flight» зависит от статуса рейса (`fmSt`) и режима (`SIM_TRACKER` или `MANUAL`) и показывается всегда; «Passengers» зависит от
статуса транспортного рейса (`tfSt`), у ferry-рейса и у законченного рейса его нет. Для посадки не в плановом аэропорту абзацы не показываются (у неё свой заголовок и три колонки).
Жирным: названия действий, числа и коды аэропортов (и во всех сообщениях ниже: предупреждения late/divert/conflict, подписи под кнопками Flying).
Данные: `{dep}`, `{dest}`, `{div}`, `{pdep}`, `{cinStart}`, `{brdStart}`, `{brdEnd}`, `{ckdIn}`, `{ponBrd}`, `{debEnd}`.

## Часть «Flight» (по статусу рейса и режиму)

- **Dispatched** (всегда, независимо от регистрации): The aircraft is assigned to you and stands at {dep}. The flight is planned for {pdep} UTC, the start opens 3 hours before it. When you are at the airport and ready to begin,
  start the flight: with the sim tracker the flight follows the sim (Blocks Off, Takeoff, Landing, Blocks On), in the manual mode you press these steps yourself. Если оба старта
  недоступны: «Fix the red conditions below first.»
- **Preflight**: The pilot studies the weather and the route, files the flight plan, walks around the aircraft, sets up the FMS and talks to the ground services about the fuel and the ramp.
- **Preflight, двери закрыты (tf WaitingForDeparture; у ferry без «The doors are closed.»)**: трекер: Release the parking brake and push back: Blocks Off follows the sim.
  Ручной: When the parking brake is released and the aircraft moves, press Blocks Off.
- **Departure**: The aircraft is taxiing out. Трекер: Takeoff follows the sim when the wheels leave the ground. Ручной: Press Takeoff when you start the takeoff run.
- **Flying**: The aircraft is on its way to {dest}. Трекер: The sim tracker lands the aircraft where you land it. Ручной: Press Landing after the touchdown (not earlier than 90% of
  the planned flight time). Дальше: If you cannot land at {dest}, declare a Divert.
- **Flying после дивёрта**: You are going to {div} instead of {dest}. The passengers are expected at {dest} all the same, see the warnings below (у ferry без этой фразы). Cancel divert takes it back.
- **Arrival**: The aircraft has landed and is taxiing in. Трекер: Blocks On follows the sim when it stops at the gate and the engines are shut down. Ручной: When it is parked, the parking
  brake is set and the engines are off, press Blocks On.
- **Postflight**: The aircraft is at the gate. Не ferry с autoFinish: The flight will be finished automatically after the passengers have got off. (У ferry дальше: Press Finish Flight to close the flight and get the pay, или The flight is being finished automatically.)
- **Finished**: The flight is finished and paid. The pilot rests at {dest}.

## Часть «Passengers» (по статусу транспортного рейса)

- **Scheduled**: The check-in opens at {cinStart} UTC.
- **CheckIn**: The passengers are checking in ({ckdIn} so far). До старта рейса: The boarding can be started after you start the flight. В Preflight: When the pilot is ready to let
  them on board, press Start Boarding (planned for {brdStart} UTC).
- **WaitingForBoarding**: The check-in is over, the passengers are waiting at the gate. До старта рейса: The boarding can be started after you start the flight. В Preflight: When the aircraft
  is ready, press Start Boarding.
- **Boarding**: The passengers are boarding ({ponBrd} on board). The boarding ends at {brdEnd} UTC or when everybody is on board. Finish Boarding closes the doors now, but not everybody may be on board.
- **WaitingForDeparture, Departure**: The passengers are seated.
- **Flying**: The passengers are in the air. (Без пункта назначения, потому что при дивёрте он ложный. Если рейс уже в Arrival, а транспортный рейс ещё Flying: The passengers are waiting for the
  aircraft to reach the gate.)
- **Arrival**: The passengers are waiting for the aircraft to reach the gate.
- **WaitingForDeboarding**: The passengers are waiting to get off. Press Start Deboarding when you are ready.
- **Deboarding**: The passengers are getting off ({ponBrd} left). The deboarding ends at {debEnd} UTC.
- **Finished**: Everybody is off. Autofinish выключен: Press Finish Flight to close the flight and get the pay. Включён: The flight is being finished automatically.

## Решено

Тексты в `flight-dashboard.html`, а не на сервере (проверяются на стабах `?stub=`); стиль: сюжет на Preflight, инструкции в остальном; числа «из скольких» (общее число проданных билетов) в
тексте не показываются, как и в плитке Check-in. Не сделано: версия для G7 (FlightBag).

## Окно старта рейса (2026-11-08)

Рейс можно начать не раньше, чем за 3 часа до планового вылета (`FlightMissionHelper.START_WINDOW_SECONDS`, `calcEarliestAllowedStartTime`). Раньше пилот мог начать рейс, до вылета которого дни: он занимал самолёт и пилота. Правило проверяется в слое пилота, не в `FlightMissionControl.start` (его используют ещё автостарт рейсов ИИ и VATSIM).

- Условие `The flight can be started not earlier than 3 hours before the planned departure (from yyyy-MM-dd HH:mm UTC)` добавляется к обоим стартам (`start-flight`, `start-flight-manual`) рейса в статусе `Dispatched` (`FlightDashboardController.withStartWindow`); пока оно красное, действие `allowed=false`. Дашборд и вкладка G7 получают его в `actions[].checks`, отдельных полей нет.
- `POST /flight-dashboard/start-flight` до окна отвечает `409` (`the flight can not be started, the check failed: ...`) в обоих режимах.
- Не касается: `continue-now` после посадки не в плановом (рейс продолжения начинается сразу), `continue-later` (новый рейс будет в окне, когда подойдёт время), рейсы после старта.
- Веб: условие в списке под кнопками, дата жирным, стаб `?stub=too-early`. G7: кнопка Start flight неактивна, под ней строкой текст условия.

# Journeys: жизненный цикл и решения

Статусы: `LookingForTickets` → `WaitingForCheckIn` → `WaitingForBoarding` → `OnBoard` → `WaitingForDeboarding` →
`JustArrived` → `ItinerariesDone` → (`SpendingTheirTime` → обратный путь → `Finished`).
Ветки: `CouldNotFindTickets`, `TooLateToBoard`.

## Локация

- `locationCityId` — где журни находится сейчас. Все подборы рейсов (`ShadowJetLogic.provideTransportFlightIfRequired`,
  `JourneyProcessor.lookingForTickets`, BusyBirds) ищут от `locationCityId`, а не от `fromCityId`.
- При check-in `locationCityId = 0` (в пути). После посадки — город у аэропорта посадки (`toCity`, если аэропорт к нему
  привязан, иначе случайный из привязанных; если у аэропорта нет городов — остаётся 0).
- `switchToReturnTrip` ставит `locationCityId` равным новому `fromCityId`.

## Решения

- **`ItinerariesDone` не означает «доехала».** `JourneyProcessor.itinerariesDone` сначала проверяет
  `isToCityReached` (`locationCityId == toCityId`). Если не достигнут (диверсия, другой аэропорт, аэропорт без
  городов), journey возвращается в `LookingForTickets` (`attemptCounter = 0`, heartbeat 1–3 суток) и продолжает
  путь к `toCity` (`JourneyControl.toCityNotReached`).
- **`TooLateToBoard` не терминальный.** В момент события: статус `TooLateToBoard`, heartbeat = now + 1…5 мин,
  локация возвращается в город у аэропорта вылета, success rate потока −0.01. По наступлению heartbeat
  (`restartAfterTooLateToBoard`): `transportFlight1Id = 0`, билеты tf2 возвращаются, journey уходит в
  `LookingForTickets` (общий хелпер `scheduleLookingForTickets`).
- Терминальные статусы (удаляются `cleanup` через сутки): `Finished`, `CouldNotFindTickets`, **`Orphaned`**.
- **`Orphaned`** — рейс (tf) журни неизвестен (удалён `FlightCleanup`), с ней ничего сделать нельзя
  (`JourneyControl.orphaned`: возвращает билеты tf2, если они ещё есть, чистится через сутки).
- **Ожидающие статусы живут по heartbeat раз в минуту** (`JourneyProcessor`):
  - `WaitingForCheckIn` — tf нет → `Orphaned`;
  - `WaitingForBoarding` — tf нет → `Orphaned`; посадка закончилась (статус tf не `CheckIn`/`WaitingForBoarding`/`Boarding`)
    → `tooLateToBoard`; иначе проверка через минуту;
  - `WaitingForDeboarding` — tf нет → `Orphaned`; tf в `Deboarding` или уже `Finished` → `deboard` («прибыли», дальше обычный
    воркфлоу); иначе проверка через минуту.
- `TooLateToBoard`: heartbeat 1–5 минут (чтобы tf ещё существовал, см. `FlightCleanup`).
- `FlightCleanup` при удалении tf до удаления разбирает журни: ссылающиеся как `t/f1` → `resetJourneyForcefully`
  (в `LookingForTickets`, если миссия ещё есть, иначе `Orphaned`), как `t/f2` → ссылка обнуляется.
- **Последний известный город.** `Journey.lastKnownLocationCityId` (занимает первые 2 из резервных байт записи)
  обновляется в `JourneyControl` явным вызовом рядом с каждым `journey.setLocationCityId(...)` с ненулевым городом
  (создание, прилёт, возврат, смена направления); обнуление `locationCityId` при `checkin` его не трогает. Если рейс потерян или журни опоздала,
  `moveBackToDepartureCity` возвращает её в этот город (в том числе без tf/миссии); если поле не задано (старые
  записи), работает старая логика (`fromCity` / случайный город аэропорта вылета).
- Потерянный tf у `WaitingForDeboarding` (в обработчике): `resetJourneyForcefully` (город — из `lastKnownLocationCityId`,
  для старых записей — `fromCity`), с `WARN` в логе.
- **Завершение дебординга tf.** tf в `Deboarding` завершается, когда `paxOnBoard == 0` **или** истекло окно дебординга
  (`TransportFlightControl.deboardingWindowExpired`, 1 час). Начало дебординга пишется событием
  `TransportFlightDeboardingStarted` в event log (`startDeboarding`); время старта читается из лога и кэшируется в
  мапе tfId → время старта (записи старше 2 часов вычищаются, при `finish` запись удаляется). Рейс без события
  (дебординг начался до введения события) считается просроченным и завершается сразу. При завершении по окну с
  `paxOnBoard > 0` пишется `WARN` и событие `t/f - finished with PAX still on board`; ожидающие журни
  разгружаются через ветку `Finished`.
- Момент случайного дебординга: при старте дебординга tf (`scheduleDeboardingForAllOnBoardJourneys`) журни из
  `OnBoard` переходят в `WaitingForDeboarding` с heartbeat = now + 1 мин. Случайный не момент, а решение: на каждом
  тике, пока tf в `Deboarding`, журни разгружается с вероятностью `DEBOARDING_PROBABILITY_PER_MINUTE` (0.33), иначе
  heartbeat = now + 1 мин и она засыпает снова (как `WaitingForBoarding`). Если tf уже `Finished`, журни
  разгружается сразу.
  `JourneyControl.deboard` уменьшает `paxOnBoard`, только если tf ещё в `Deboarding` (если tf уже `Finished`, то нет).
- `/admin/journey/delete-invalid?dryRun=true` — чистка невалидных journeys (в т.ч. терминальные без heartbeat,
  `TooLateToBoard` без heartbeat получает heartbeat = now).

## Далёкое будущее

- **Точный возврат в город вылета.** `moveBackToCityNextToDepartureAirportOrFromCity` (при `TooLateToBoard` и
  `resetJourneyForcefully`) возвращает в `fromCity`, если он привязан к аэропорту вылета, иначе в случайный город
  этого аэропорта. Если journey вылетала из `locationCityId ≠ fromCityId`, а у аэропорта несколько городов
  (KFTG: Aurora/Denver, KNKX: San Diego/Tijuana…), вернуться она может не в тот. Точное решение — хранить «город
  перед рейсом» (в записи 12 зарезервированных байт), либо детерминированная эвристика выбора города аэропорта.
  Пока не делаем, неточность редкая.
- Перелёты через хабы — см. [journeys-hub-mode.md](journeys-hub-mode.md).

## TODO: убрать старую логику возврата, когда `lastKnownLocationCityId` везде работает

`JourneyControl.moveBackToCityBeforeFlight` берёт город из `Journey.lastKnownLocationCityId`; если поле не задано,
идёт старая логика (по `fromCity` и аэропорту вылета миссии). За использованием старого пути следит статистика
`FlightStats`:
- `journey - move back to departure city - no last known location` — поле не задано, пошли по-старому;
- `journey - move back to departure city - no FM found` — нет ни поля, ни миссии, берём `fromCity`.

**Когда таких событий станет исчезающе мало, значит `lastKnownLocationCityId` везде проставляется и работает как
надо**; тогда старую логику (и ветку по миссии) можно удалить.

**TODO:** проверять статистику `no last known location` (в `FlightStats`) и в какой-то момент переписать
`JourneyControl.moveBackToDepartureCity` так, чтобы журни возвращалась **только** в `lastKnownLocationCityId`
(без старой логики по миссии и `fromCity`). Журни в полёте на момент деплоя поля не имеют, поэтому
вначале события неизбежны.

## TODO: не орфанить журни без tf (статистика для «толстых» мест)

В ожидающих статусах `WaitingForBoarding` и `WaitingForDeboarding`, когда tf пропал, журни теперь не `Orphaned`, а
`resetJourneyForcefully` (возврат в город перед рейсом) с событиями `FlightStats`:
- `journey - waiting for boarding - no TF found`;
- `journey - waiting for deboarding - no TF found`;
- `journey - waiting for deboarding - unexpected TF status` (tf жив, но не в `Deboarding`/`Finished`: непонятный
  случай, журни `Orphaned` с `WARN`, без сброса и без изменения `paxOnBoard`);
- `journey - reset journey forcefully` — общий счётчик сбросов.
Следующий шаг: по этой статистике найти «толстые» места, откуда журни теряют tf, и чинить причины; аналогичный
для `WaitingForCheckIn` теперь тоже `resetJourneyForcefully` вместо `Orphaned` (события `journey - waiting for check-in - no TF found` и `... - no FM found`); остальные пути пока не трогаем.

## TODO: поле в t/f для счётчиков времени и резерв места в записи

Время старта дебординга tf сейчас приходится восстанавливать из event log (событие `TransportFlightDeboardingStarted`)
и кэшировать в мапе в памяти (`TransportFlightControl.deboardingStartTimes`), потому что в записи t/f нет для этого
поля и нет зарезервированного места. Нужно:
- добавить в запись t/f поле для таких счётчиков времени (например, время старта дебординга; по аналогии можно хранить
  и другие моменты жизненного цикла), после чего убрать мапу и поиск по event log в `deboardingWindowExpired`;
- и вообще сделать в записи t/f **резерв места** (несколько зарезервированных байт, как в `Journeys`), чтобы
  расширять рейс без миграции хранилища.

## TODO: недоделки по дебордингу и возврату (ревью 2026-10-16)

Не сделано или обсуждено и отложено:

1. ~~Шум в статистике «no last known location» из `waitingForCheckin`~~ — закрыто: `lastKnownLocationCityId` теперь
   ставится везде, где ставится `locationCityId` (явные вызовы в `JourneyControl`), в том числе до `checkin`, поэтому
   `tooLateToBoard` из `waitingForCheckin` тоже возвращает в известный город. События остаются только для старых
   записей без поля.
2. **`FlightCleanup` и `WaitingForDeboarding`.** При удалении tf журни из `WaitingForDeboarding` сбрасываются в
   `LookingForTickets` и возвращаются в город перед рейсом (хотя уже прилетели). Мы решили пока так и оставить,
   пока ничего не делаем с «мёртвыми». Открытый вопрос: считать их прибывшими (`justArrived`), а не возвращать.
3. **Счётчик `paxOnBoard`.** Гипотеза, не проверенная: при посадке `PaxManager` пишет
   `confirmedOnBoard + (int) counterValue`, то есть туда попадают пассажиры ещё не севшей группы, и после дебординга
   всех журни счётчик может не вернуться в 0, tf зависает в `Deboarding`. Окно дебординга (1 час) это страхует.
   Сравнить список рейсов в `Deboarding` сегодня и через несколько часов (потерявшиеся на дебординге рейсы); при
   необходимости сделать для этого админ-ручку (id рейса, `paxOnBoard`, число ожидающих журни).
4. **`finish` по окну с `paxOnBoard > 0`.** Ожидающие журни разгружаются через ветку `Finished` и пишут `WARN` на каждую,
   у большого рейса это пачка строк за один тик. Если шумно, понизить уровень или агрегировать.
5. **`orphaned` для неожиданного статуса tf** (`waitingForDeboarding`): `paxOnBoard` не меняется, поэтому tf потом
   завершится только по окну. Приемлемо, смотреть по событию `journey - waiting for deboarding - unexpected TF status`.
6. **Покрытие тестами.** На новую логику (обработчик дебординга с вероятностью, `lastKnownLocationCityId`,
   `resetJourneyForcefully` без tf, окно дебординга) тестов нет, существующие проходят.
7. **Вживую не проверялось**: изменения не выкатывались на сервер. Смотреть `WARN` и события `FlightStats` после выкатки.
8. **Точный возврат** для старых journey (без `lastKnownLocationCityId`) остаётся приблизительным, см. раздел
   «Далёкое будущее» и TODO про удаление старой логики выше.

## ShadowJet: посадка рейса и вылет посреди посадки

`ShadowJetLogic.provideTransportFlightIfRequired` (вызывается, когда появляется VATSIM-миссия):
1. создаёт t/f и запускает check-in;
2. подбирает журни из `LookingForTickets` (прямые и хабовые в одной куче, цель 70–100% мест, подробности в
   [journeys-hub-mode.md](journeys-hub-mode.md)), бронирует их; 30% сразу регистрирует (`checkin`, `locationCityId`
   обнуляется, `lastKnownLocationCityId` хранит город), остальные регистрируются позже во время посадки: 30% в течение
   5 минут, 20% на 5-10 минуте, 20% на 10-20 минуте;
3. сразу запускает посадку (`startBoarding`): темп `PaxManager` 15 PAX/мин, оценка окончания = `now + ceil(остаток / 15 ×
   60) с + 3 мин запаса` (на 120 PAX это ~11 минут).

**Решение по модели: темп посадки и логику регистрации не меняем.** Пилот на VATSIM уходит с гейта когда хочет;
если он ждёт посадку до конца, он увозит больше пассажиров, если нет, меньше (так же и при полёте в Airways).
Опоздавшие журни не проблема модели, это ожидаемый результат (`tooLateToBoard`, возврат в город перед рейсом).
Поэтому ничего ускорять и «честнее» делать не нужно, нужно **правильно реагировать на прерывание посадки**.

**Сделано (тесты: `PaxManagerTest`, `FlightWorkflowTest`, `ShadowJetLogicTest`):**
- при вылете t/f с гейта в статусе `Boarding` (`TransportFlightControl.whenFlightDepartsFromGate`) вызывается
  `paxManager().finishBoarding(tf)`: не севшие журни получают `tooLateToBoard`, запись посадки удаляется из мапы
  `boardings`;
- при принудительном окончании посадки журни, посадка которой начата, но не закончена (в счётчике посадки уже есть её
  пассажиры), садится (`OnBoard`), чтобы `paxOnBoard` не прыгал вниз: скачок вверх лучше скачка вниз;
- `PaxManager.finishBoarding` и `TransportFlightControl.startDeboarding` выставляют `paxOnBoard` = сумма `groupSize`
  журни в `OnBoard` (`PaxManager.countPaxOnBoard`), без «призраков» счётчика посадки (`confirmedOnBoard + (int)
  counter`): раньше такие пассажиры не были привязаны ни к какой журни, рейс не доходил до `paxOnBoard == 0` и висел в
  `Deboarding` до окна дебординга (на деплое 16 октября окно закрыло 1253 таких рейса, 79% с остатком 1-3 PAX);
- при отмене рейса (`unloadJourneysForcefullyFromActiveFlight`) запись посадки тоже удаляется (`dropBoardingState`).

**Отключение VATSIM-пилота (`PilotContext`), что происходит с f/m, t/f и журни:**

| Ситуация | Стадия контекста | Действие | t/f и журни |
|---|---|---|---|
| Офлайн на земле до взлёта (Preflight/Departing, в том числе посреди посадки) | Preflight/Departing | `mission_cancelBeforeTakeoffIfExists` | `cancelTransportFlightIfExists`: журни возвращаются в город до рейса, tf `Cancelled`, `paxOnBoard = 0` |
| Офлайн в полёте дольше допустимого | Flying → FlyingOffline | `mission_cancelFromFlying` | то же самое, даже если фактически рейс почти долетел (см. todo в коде) |
| Посадка замечена, затем офлайн | Arriving | `mission_blocksOnAndFinish` | `blocksOn` → tf `WaitingForDeboarding`, `finish`, `deboardTransportFlightIfExists` → tf `Deboarding`, журни разгружаются как обычно |
| Дорулил до гейта и сразу офлайн, прибытие ещё не определено (определяется с лагом) | Arriving | то же самое | то же самое, отдельной ветки не нужно |
| Офлайн, когда уже `Arrived` | Arrived | сброс контекста | миссия уже завершена, tf разгружается |

Все эти ситуации покрыты в `FlightWorkflowTest` (раздел «the pilot disconnects», действия те же, что у
`PilotContext`). Сам `PilotContext` по-прежнему тестируется в `VatsimTrackerTest` только на уровне f/m и самолёта,
t/f и журни там не проверяются.

## Зависшие WaitingForDeboarding после смены схемы дебординга

В новой схеме журни при переходе в `WaitingForDeboarding` всегда получает heartbeat (проверка рейса раз в минуту). Журни без heartbeat в этом
статусе остались от старой схемы и никогда не проснутся. Анализ прода 2026-10-16 (через `/admin/journey/all` и `/admin/transport-flight/all`):
3884 журни (21 562 пассажира) в `WaitingForDeboarding` без heartbeat, все на рейсах в статусе `Finished` (даты рейсов 06.10 - 15.10).
`OnBoard` без heartbeat (2802) нормальны: рейсы сегодняшние и живые. Рейсов без heartbeat в статусах, где он нужен, нет
(`Boarding` и `Deboarding` с heartbeat, остальное без него по дизайну).

Лечение: `/admin/journey/stuck-waiting-for-deboarding?dryRun=true&mode=deboard|delete&limit=500`. `deboard` ставит heartbeat на
случайный момент в ближайшие 30 минут, дальше `JourneyProcessor` (рейс завершён → дебординг, рейса нет → forceful return, странный статус →
orphaned), пассажиры сохраняются; `delete` удаляет журни.

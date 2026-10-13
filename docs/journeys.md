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
- **`TooLateToBoard` не терминальный.** В момент события: статус `TooLateToBoard`, heartbeat = now + 10 мин…1 час,
  локация возвращается в город у аэропорта вылета, success rate потока −0.01. По наступлению heartbeat
  (`restartAfterTooLateToBoard`): `transportFlight1Id = 0`, билеты tf2 возвращаются, journey уходит в
  `LookingForTickets` (общий хелпер `scheduleLookingForTickets`).
- Терминальные статусы (удаляются `cleanup` через сутки): `Finished`, `CouldNotFindTickets`.
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

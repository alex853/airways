<%
    String backendURL = application.getInitParameter("BackendURL");

%>

<html>

<head>

    <title>Airways - Pilot</title>

    <link rel="stylesheet" href="css/bootstrap.min.css">
    <link rel="stylesheet" href="css/bootstrap-table.min.css">
    <script src="js/jquery-3.3.1.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/bootstrap-table.min.js"></script>
    <script src="common.js"></script>

    <script>

        let person;
        let pilot;

        let allCities;

        $(document).ready(function () {
            reloadPilotStatus();

            $.ajax({
                url: '<%=backendURL%>/geo/city/all',
                dataType: 'json',
                success: function (response) {
                    allCities = response;
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        });

        function loadPilotStatus() {
            $.ajax({
                url: '<%=backendURL%>/pilot/status',
                dataType: 'json',
                success: function (response) {
                    person = response.person;
                    pilot = response.pilot;

                    $("#person-name").text(person.fullName);
                    $("#person-origin").text(person.originCityName);
                    $("#person-status").text(person.statusName);

                    let location = '';
                    if (person.locationCityId) {
                        location = person.locationCityName;
                    } else if (person.locationAirportId) {
                        location = person.locationAirportName;
                    }
                    $("#person-location").text(location);

                    $("#person-journey").text(person.journeyDesc);

                    $("#pilot-status").text(pilot.statusName);

                    let actions = response.actions;
                    enableButton('#action-book-travel', actions.canTravel);
                    enableButton('#action-transfer-to-city', actions.canTransferToCity);
                    enableButton('#action-transfer-to-airport', actions.canTransferToAirport);
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        }

        function reloadPilotStatus() {
            loadPilotStatus();
            setTimeout(function () { reloadPilotStatus(); }, 60000);
        }

        function openBookTravelDialog() {
            var dialog = $('#bookTravelModal');

            if (person.locationCityId) {
                $('#bookTravelModal-location').val(person.locationCityName);
            } else if (person.locationAirportId) {
                $('#bookTravelModal-location').val(person.locationAirportName);
            }

            if ($('#bookTravelModal-travelTo option').length === 0) {
                allCities.forEach((city) => {
                    $("#bookTravelModal-travelTo")
                        .append('<option value="#id#">#name#</option>'
                            .replace('#id#', city.id)
                            .replace('#name#', city.name));
                });
            }

            $('#bookTravelModal-travelTo').val('');

            dialog.modal();
        }

        function bookTravel() {
            let dialog = $('#bookTravelModal');

            let destinationCityId = $("#bookTravelModal-travelTo").val();

            $.ajax({
                url: '<%=backendURL%>/pilot/travel/book',
                method: 'POST',
                data: {
                    destinationCityId: destinationCityId
                },
                success: function () {
                    showAlert("Travel booked successfully", "success", 5000);
                    loadPilotStatus();
                },
                error: function (e) {
                    showAlert("Error happened - " + e.responseText, "danger", 15000);
                    console.log(e.responseText);
                }
            });

            dialog.modal('hide');
        }

        function openTransferToAirportDialog() {
            var dialog = $('#transferToAirportModal');

            $('#transferToAirportModal-location').val(person.locationCityName);

            $('#transferToAirportModal-transferTo').empty();
            $.ajax({
                url: '<%=backendURL%>/geo/airport/by/city',
                data: {
                    cityId: person.locationCityId
                },
                success: function (response) {
                    response.forEach((airport) => {
                        $('#transferToAirportModal-transferTo')
                            .append('<option value="#id#">#name#</option>'
                                .replace('#id#', airport.id)
                                .replace('#name#', airport.name));
                    });
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });

            $('#transferToAirportModal-transferTo').val('');

            dialog.modal();
        }

        function transferToAirport() {
            let dialog = $('#transferToAirportModal');

            let destinationAirportId = $("#transferToAirportModal-transferTo").val();

            $.ajax({
                url: '<%=backendURL%>/pilot/transfer/to/airport',
                method: 'POST',
                data: {
                    destinationAirportId: destinationAirportId
                },
                success: function () {
                    showAlert("Transfer to Airport started successfully", "success", 5000);
                    loadPilotStatus();
                },
                error: function (e) {
                    showAlert("Error happened - " + e.responseText, "danger", 15000);
                    console.log(e.responseText);
                }
            });

            dialog.modal('hide');
        }

        function openTransferToCityDialog() {
            var dialog = $('#transferToCityModal');

            $('#transferToCityModal-location').val(person.locationAirportName);

            $('#transferToCityModal-transferTo').empty();
            $.ajax({
                url: '<%=backendURL%>/geo/city/by/airport',
                data: {
                    airportId: person.locationAirportId
                },
                success: function (response) {
                    response.forEach((city) => {
                        $('#transferToCityModal-transferTo')
                            .append('<option value="#id#">#name#</option>'
                                .replace('#id#', city.id)
                                .replace('#name#', city.name));
                    });
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });

            $('#transferToCityModal-transferTo').val('');

            dialog.modal();
        }

        function transferToCity() {
            let dialog = $('#transferToCityModal');

            let destinationCityId = $("#transferToCityModal-transferTo").val();

            $.ajax({
                url: '<%=backendURL%>/pilot/transfer/to/city',
                method: 'POST',
                data: {
                    destinationCityId: destinationCityId
                },
                success: function () {
                    showAlert("Transfer to City started successfully", "success", 5000);
                    loadPilotStatus();
                },
                error: function (e) {
                    showAlert("Error happened - " + e.responseText, "danger", 15000);
                    console.log(e.responseText);
                }
            });

            dialog.modal('hide');
        }

        function openDeclareFlightDialog() {
            var dialog = $('#declareFlightModal');

            dialog.modal();
        }

        function declareFlight() {
            let dialog = $('#declareFlightModal');

            $.ajax({
                url: '<%=backendURL%>/pilot/declare/flight',
                method: 'POST',
                data: {
                    dateOfFlight: 'today',
                    blocksOff: $('#declareFlightModal-blocksOff').val(),
                    aircraftTypeId: $('#declareFlightModal-typeOfAircraft').val(),
                    aircraftId: $('#declareFlightModal-aircraftRegNo').val(),
                    departureIcao: $('#declareFlightModal-departureAirportIcao').val(),
                    destinationIcao: $('#declareFlightModal-destinationAirportIcao').val()
                },
                success: function () {
                    showAlert("Flight booked successfully", "success", 5000);
                    loadPilotStatus();
                },
                error: function (e) {
                    showAlert("Error happened - " + e.responseText, "danger", 15000);
                    console.log(e.responseText);
                }
            });

            dialog.modal('hide');
        }

    </script>
</head>

<body>

<div class="container">
    <h1>Airways - Pilot</h1>
    <form>
        <div class="form-group row">
            <label for="person-name" class="col-sm-2 col-form-label">Name</label>
            <span class="col-sm-4 col-form-label" id="person-name"></span>
            <label for="person-origin" class="col-sm-2 col-form-label">Origin</label>
            <span class="col-sm-4 col-form-label" id="person-origin"></span>
        </div>
        <div class="form-group row">
            <label for="person-status" class="col-sm-2 col-form-label">Status (Person)</label>
            <span class="col-sm-4 col-form-label" id="person-status"></span>
            <label for="person-location" class="col-sm-2 col-form-label">Location</label>
            <span class="col-sm-4 col-form-label" id="person-location"></span>
        </div>
        <div class="form-group row">
            <label for="pilot-status" class="col-sm-2 col-form-label">Status (Pilot)</label>
            <span class="col-sm-4 col-form-label" id="pilot-status"></span>
            <label for="person-journey" class="col-sm-2 col-form-label">Journey</label>
            <span class="col-sm-4 col-form-label" id="person-journey"></span>
        </div>
    </form>

    <a class="btn btn-outline-primary btn-sm" id="action-book-travel" href="javascript:openBookTravelDialog()" role="button">Book Travel</a>
    <a class="btn btn-outline-primary btn-sm" id="action-transfer-to-city" href="javascript:openTransferToCityDialog()" role="button">Transfer to City</a>
    <a class="btn btn-outline-primary btn-sm" id="action-transfer-to-airport" href="javascript:openTransferToAirportDialog()" role="button">Transfer to Airport</a>
    <a class="btn btn-outline-primary btn-sm" id="action-declare-flight" href="javascript:openDeclareFlightDialog()" role="button">Declare Flight</a>

</div>



<div class="modal fade" id="bookTravelModal" tabindex="-1" role="dialog" aria-labelledby="bookTravelModalLabel"
     aria-hidden="true" data-backdrop="static">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="bookTravelModalLabel">Book Travel</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <form id="bookTravelModal-form">
                    <div class="form-row">
                        <div class="form-group col-md-6">
                            <label for="bookTravelModal-location">Now located at</label>
                            <input type="text" class="form-control" id="bookTravelModal-location" disabled>
                        </div>
                        <div class="form-group col-md-6">
                            <label for="bookTravelModal-travelTo">Travel To</label>
                            <select class="form-control" id="bookTravelModal-travelTo" required></select>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-primary" onclick="bookTravel()">Book Travel</button>
            </div>
        </div>
    </div>
</div>



<div class="modal fade" id="transferToAirportModal" tabindex="-1" role="dialog" aria-labelledby="transferToAirportModalLabel"
     aria-hidden="true" data-backdrop="static">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="transferToAirportModalLabel">Transfer to Airport</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <form id="transferToAirportModal-form">
                    <div class="form-row">
                        <div class="form-group col-md-6">
                            <label for="transferToAirportModal-location">Now located at</label>
                            <input type="text" class="form-control" id="transferToAirportModal-location" disabled>
                        </div>
                        <div class="form-group col-md-6">
                            <label for="transferToAirportModal-transferTo">Transfer To</label>
                            <select class="form-control" id="transferToAirportModal-transferTo" required></select>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-primary" onclick="transferToAirport()">Transfer</button>
            </div>
        </div>
    </div>
</div>



<div class="modal fade" id="transferToCityModal" tabindex="-1" role="dialog" aria-labelledby="transferToCityModalLabel"
     aria-hidden="true" data-backdrop="static">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="transferToCityModalLabel">Transfer to City</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <form id="transferToCityModal-form">
                    <div class="form-row">
                        <div class="form-group col-md-6">
                            <label for="transferToCityModal-location">Now located at</label>
                            <input type="text" class="form-control" id="transferToCityModal-location" disabled>
                        </div>
                        <div class="form-group col-md-6">
                            <label for="transferToCityModal-transferTo">Transfer To</label>
                            <select class="form-control" id="transferToCityModal-transferTo" required></select>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-primary" onclick="transferToCity()">Transfer</button>
            </div>
        </div>
    </div>
</div>



<div class="modal fade" id="declareFlightModal" tabindex="-1" role="dialog" aria-labelledby="declareFlightModalLabel"
     aria-hidden="true" data-backdrop="static">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="declareFlightModalLabel">Declare Flight</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <form id="declareFlightModal-form">
                    <div class="form-row">
                        <div class="form-group col-md-6">
                            <label for="declareFlightModal-dateOfFlight">Date of Flight</label>
                            <div class="btn-group" role="group" id="declareFlightModal-dateOfFlight">
                                <button type="button" class="btn btn-primary">Today</button>
                                <button type="button" class="btn btn-info">Tomorrow</button>
                            </div>
                        </div>
                        <div class="form-group col-md-6">
                            <label for="declareFlightModal-flightNumber">Flight Number</label>
                            <input type="text" class="form-control" id="declareFlightModal-flightNumber" placeholder="PHxxxx" readonly>
                            <small class="form-text text-muted">PhantomAir flight number will be assigned automatically.</small>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-6">
                            <label for="declareFlightModal-typeOfAircraft">Type Of Aircraft</label>
                            <select class="form-control" id="declareFlightModal-typeOfAircraft">
                                <option value="1">A320</option>
                                <option value="2">B744</option>
                                <option value="3">E170</option>
                            </select>
                        </div>
                        <div class="form-group col-md-6">
                            <label for="declareFlightModal-aircraftRegNo">Aircraft Identification</label>
                            <select class="form-control" id="declareFlightModal-aircraftRegNo">
                                <option value="0" selected>PA-xxx</option>
                            </select>
                            <small class="form-text text-muted">PhantomAir aircraft will be added if there is no aircraft available at the departure airport.</small>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-departureAirportIcao">Departure</label>
                            <input type="text" class="form-control" id="declareFlightModal-departureAirportIcao" required value="EGLL">
                        </div>
                        <div class="form-group col-md-6">
                            <label for="declareFlightModal-departureAirportName">Airport</label>
                            <input type="text" class="form-control" id="declareFlightModal-departureAirportName" readonly>
                        </div>
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-blocksOff">Blocks Off</label>
                            <input type="text" class="form-control" id="declareFlightModal-blocksOff" required value="18:00">
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-9"></div>
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-taxiOut">Taxi Out</label>
                            <input type="text" class="form-control" id="declareFlightModal-taxiOut" value="10 mins" readonly>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-distance">Distance</label>
                            <input type="text" class="form-control" id="declareFlightModal-distance" readonly>
                        </div>
                        <div class="form-group col-md-6"></div>
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-airTime">Air Time</label>
                            <input type="text" class="form-control" id="declareFlightModal-airTime" readonly>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-9"></div>
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-taxiIn">Taxi In</label>
                            <input type="text" class="form-control" id="declareFlightModal-taxiIn" value="10 mins" readonly>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-destinationAirportIcao">Destination</label>
                            <input type="text" class="form-control" id="declareFlightModal-destinationAirportIcao" required value="EGBB">
                        </div>
                        <div class="form-group col-md-6">
                            <label for="declareFlightModal-destinationAirportName">Airport</label>
                            <input type="text" class="form-control" id="declareFlightModal-destinationAirportName" readonly>
                        </div>
                        <div class="form-group col-md-3">
                            <label for="declareFlightModal-blocksOnEst">Blocks On Est</label>
                            <input type="text" class="form-control" id="declareFlightModal-blocksOnEst" readonly>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-primary" onclick="declareFlight()">Declare Flight</button>
            </div>
        </div>
    </div>
</div>



</body>

</html>

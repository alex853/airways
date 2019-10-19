<%--
  ~ Airways Project (c) Alexey Kornev, 2015-2019
  --%>

<%
    String backendURL = application.getInitParameter("BackendURL");

%>

<html>

<head>

    <title>Airways - Transport Flight #flight-info </title>

    <link rel="stylesheet" href="css/bootstrap.min.css">
    <link rel="stylesheet" href="css/bootstrap-table.min.css">
    <script src="js/jquery-3.3.1.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/bootstrap-table.min.js"></script>

    <script>
        var url = window.location.href;
        var captured = /id=([^&]+)/.exec(url)[1];
        if (!captured) {
            alert('No ID specified');
            window.close();
        }
        var id = captured;


        $(document).ready(function () {
            $.ajax({
                url: '<%=backendURL%>/misc/transport-flight?id=' + id,
                dataType: 'json',
                success: function (response) {
                    var transportFlight = response.transportFlight;
                    $.each(transportFlight, function (field, value) {
                        $("#transportFlight-" + field).text(value);
                    });

                    var flightInfo = '#' + transportFlight.id + ' ' + transportFlight.flightNumber + ' ' + transportFlight.dateOfFlight;
                    $('#flight-info').text(flightInfo);
                    document.title = document.title.replace('#flight-info', flightInfo);

                    var journeysTable = $('#journeys');
                    journeysTable.bootstrapTable({
                        data: response.journeys
                    });
                    journeysTable.bootstrapTable('resetView', {
                        height: journeysTable.height() + 30
                    });
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        });

        function actionsCell(value, row) {
            return '<a class="btn btn-outline-info btn-sm" href="journey.jsp?id=' + row.id + '">Details</a>';
        }

    </script>

    <style>
        .fixed-table-container { /* lets disable frame around table */
            border-width: 0px 0px 0px 0px;
        }
    </style>
</head>

<body>

<div class="container">
    <h1>Airways - Transport Flight <span id="flight-info"></span></h1>

    <a class="btn btn-outline-primary btn-sm" href="javascript:alert('TODO')" role="button">Flight</a>
    <a class="btn btn-outline-primary btn-sm" href="javascript:alert('TODO')" role="button">Timetable</a>

    <form>
        <div class="form-group row">
            <label for="transportFlight-flightNumber" class="col-sm-2 col-form-label">Flight #</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-flightNumber"></span>
            <label for="transportFlight-dateOfFlight" class="col-sm-2 col-form-label">Date of Flight</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-dateOfFlight"></span>
        </div>
        <div class="form-group row">
            <label for="transportFlight-fromIcao" class="col-sm-2 col-form-label">From</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-fromIcao"></span>
            <label for="transportFlight-toIcao" class="col-sm-2 col-form-label">To</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-toIcao"></span>
        </div>
        <div class="form-group row">
            <label for="transportFlight-departureTime" class="col-sm-2 col-form-label">Departure Time</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-departureTime"></span>
            <label for="transportFlight-arrivalTime" class="col-sm-2 col-form-label">Arrival Time</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-arrivalTime"></span>
        </div>
        <div class="form-group row">
            <label for="transportFlight-status" class="col-sm-2 col-form-label">Status</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-status"></span>
            <span class="col-sm-6 col-form-label"></span>
        </div>
        <div class="form-group row">
            <label for="transportFlight-freeTickets" class="col-sm-2 col-form-label">Free Tickets</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-freeTickets"></span>
            <label for="transportFlight-totalTickets" class="col-sm-2 col-form-label">Total Tickets</label>
            <span class="col-sm-4 col-form-label" id="transportFlight-totalTickets"></span>
        </div>
    </form>

    <h3>Journeys</h3>
    <table id="journeys" class="table table-no-bordered">
        <thead>
        <tr>
            <th data-field="fromCity">From</th>
            <th data-field="toCity">To</th>
            <th data-field="groupSize">Size</th>
            <th data-field="status">Status</th>
            <th data-field="itineraryCheck">Itinerary</th>
            <th data-formatter="actionsCell"></th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>

    <div class="alert alert-warning" role="alert">TODO - event log</div>
</div>

</body>

</html>

<%
    String backendURL = application.getInitParameter("BackendURL");

%>

<html>

<head>

    <title>Airways - Journey #journey-info</title>

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
                url: '<%=backendURL%>/misc/journey?id=' + id,
                dataType: 'json',
                success: function (response) {
                    var journey = response.journey;
                    $.each(journey, function (field, value) {
                        $("#journey-" + field).text(value);
                    });

                    var journeyInfo = '#' + journey.id;
                    $('#journey-info').text(journeyInfo);
                    document.title = document.title.replace('#journey-info', journeyInfo);

                    var personsTable = $('#persons');
                    personsTable.bootstrapTable({ data: response.persons });
                    personsTable.bootstrapTable('resetView', { height: personsTable.height() + 30 });

                    var itineraryTable = $('#itinerary');
                    itineraryTable.bootstrapTable({ data: response.itineraries });
                    itineraryTable.bootstrapTable('resetView', { height: itineraryTable.height() + 30 });

                    var logTable = $('#log');
                    logTable.bootstrapTable({ data: response.log });

                    if (response.logHasMore) {
                        logTable.bootstrapTable('insertRow', { index: 0,
                            row: {
                                dt: '',
                                msg: '<button type="button" class="btn btn-secondary" id="showAllEvents">Show all events</button>'
                            }
                        });

                        $('#showAllEvents').click( function () {
                            $.ajax({
                                url: '<%=backendURL%>/misc/get-full-log?primary_id=journey:' + id,
                                dataType: 'json',
                                success: function (response) {
                                    var logTable = $('#log');
                                    logTable.bootstrapTable('load', response.log);
                                    logTable.bootstrapTable('resetView', { height: logTable.height() + 30 });
                                },
                                error: function (e) {
                                    console.log(e.responseText);
                                }
                            });
                        });
                    }
                    logTable.bootstrapTable('resetView', { height: logTable.height() + 30 });
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        });

        function actionsCell(value, row) {
            return '<a class="btn btn-outline-info btn-sm" href="person.jsp?id=' + row.id + '">Details</a>';
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
    <h1>Airways - Journey <span id="journey-info"></span></h1>
    <a class="btn btn-outline-primary btn-sm" href="javascript:alert('TODO')" role="button">Flow</a>

    <form>
        <div class="form-group row">
            <label for="journey-fromCity" class="col-sm-2 col-form-label">From</label>
            <span class="col-sm-4 col-form-label" id="journey-fromCity"></span>
            <label for="journey-toCity" class="col-sm-2 col-form-label">To</label>
            <span class="col-sm-4 col-form-label" id="journey-toCity"></span>
        </div>
        <div class="form-group row">
            <label for="journey-groupSize" class="col-sm-2 col-form-label">Group Size</label>
            <span class="col-sm-4 col-form-label" id="journey-groupSize"></span>
            <label for="journey-itineraryCheck" class="col-sm-2 col-form-label">Itinerary</label>
            <span class="col-sm-4 col-form-label" id="journey-itineraryCheck"></span>
        </div>
        <div class="form-group row">
            <label for="journey-status" class="col-sm-2 col-form-label">Status</label>
            <span class="col-sm-4 col-form-label" id="journey-status"></span>
            <span class="col-sm-6 col-form-label"></span>
        </div>
    </form>

    <h3>Persons</h3>
    <table id="persons" class="table table-no-bordered">
        <thead>
        <tr>
            <th data-field="name">Name</th>
            <th data-field="sex">Sex</th>
            <th data-field="type">Type</th>
            <th data-field="status">Status</th>
            <th data-field="origin">Origin</th>
            <th data-field="location">Location</th>
            <th data-formatter="actionsCell"></th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>

    <h3>Itinerary</h3>
    <table id="itinerary" class="table table-no-bordered">
        <thead>
        <tr>
            <th data-field="itemOrder">#</th>
            <th data-field="flight-dateOfFlight">DOF</th>
            <th data-field="flight-number">Flight #</th>
            <th data-field="flight-fromAirport">From</th>
            <th data-field="flight-toAirport">To</th>
            <th data-field="flight-status">Status</th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>

    <h3>Event log</h3>
    <table id="log" class="table table-no-bordered">
        <thead>
        <tr>
            <th data-field="dt">Date/Time</th>
            <th data-field="msg">Message</th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
</div>

</body>

</html>

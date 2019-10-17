<%--
  ~ Airways Project (c) Alexey Kornev, 2015-2019
  --%>

<!--
~ Airways Project (c) Alexey Kornev, 2015-2019
-->

<%
    String backendURL = application.getInitParameter("BackendURL");

%>

<html>

<head>

    <title>Airways - Ticket Sales</title>

    <link rel="stylesheet" href="css/bootstrap.min.css">
    <link rel="stylesheet" href="css/bootstrap-table.min.css">
    <script src="js/jquery-3.3.1.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/bootstrap-table.min.js"></script>

    <script>
        $(document).ready(function () {
            $.ajax({
                url: '<%=backendURL%>/misc/ticket-sales',
                //method: 'POST',
                dataType: 'json',
                success: function (response) {
                    $('#ticket-sales').bootstrapTable({
                        data: response
                    });
                    $('#ticket-sales').bootstrapTable('resetView', {
                        height: $('#ticket-sales').height() + 30
                    });
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        });

        function actionsCell(value, row) {
            return '<a href="transport-flight.jsp?id=' + row.id + '">Details</a>';
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
    <h1>Airways - Ticket Sales</h1>

    <table id="ticket-sales" class="table table-no-bordered">
        <thead>
        <tr>
            <th data-field="dateOfFlight">DOF</th>
            <th data-field="departureTime">Time</th>
            <th data-field="flightNumber">Flight #</th>
            <th data-field="fromIcao">From</th>
            <th data-field="toIcao">To</th>
            <th data-field="status">Status</th>
            <th data-field="soldTickets">Sold</th>
            <th data-field="freeTickets">Available</th>
            <th data-field="totalTickets">Total</th>
            <th data-formatter="actionsCell"></th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
</div>

</body>

</html>

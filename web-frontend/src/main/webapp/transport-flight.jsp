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

    <title>Airways - Transport Flight ....</title>

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
                //method: 'POST',
                dataType: 'json',
                success: function (response) {
                    $('#journeys').bootstrapTable({
                        data: response.journeys
                    });
                    $('#journeys').bootstrapTable('resetView', {
                        height: $('#journeys').height() + 30
                    });
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        });

        function actionsCell(value, row) {
            return '<a href="journey.jsp?id=' + row.id + '">Details</a>';
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
    <h1>Airways - Transport Flight ....</h1>

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
</div>

</body>

</html>
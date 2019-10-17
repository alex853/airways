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

    <title>Airways - Journey ....</title>

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
                //method: 'POST',
                dataType: 'json',
                success: function (response) {
                    $('#persons').bootstrapTable({
                        data: response.persons
                    });
                    $('#persons').bootstrapTable('resetView', {
                        height: $('#persons').height() + 30
                    });
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        });

        function actionsCell(value, row) {
            return '<a href="person.jsp?id=' + row.id + '">Details</a>';
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
    <h1>Airways - Journey ....</h1>

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
</div>

</body>

</html>

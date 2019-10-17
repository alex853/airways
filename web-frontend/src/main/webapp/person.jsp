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

    <title>Airways - Person ##person-id - #person-name</title>

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
                url: '<%=backendURL%>/misc/person?id=' + id,
                dataType: 'json',
                success: function (response) {
                    $.each(response.person, function (field, value) {
                        $("#person-" + field).text(value);
                    });

                    document.title = document.title
                        .replace('#person-id', response.person.id)
                        .replace('#person-name', response.person.name);

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
                                url: '<%=backendURL%>/misc/get-full-log?primary_id=person:' + id,
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

    </script>

    <style>
        .fixed-table-container { /* lets disable frame around table */
            border-width: 0px 0px 0px 0px;
        }
    </style>
</head>

<body>

<div class="container">
    <h1>Airways - Person #<span id="person-id"></span></h1>
    <form>
        <div class="form-group row">
            <label for="person-name" class="col-sm-2 col-form-label">Name</label>
            <span class="col-sm-4 col-form-label" id="person-name"></span>
            <label for="person-sex" class="col-sm-2 col-form-label">Sex</label>
            <span class="col-sm-4 col-form-label" id="person-sex"></span>
        </div>
        <div class="form-group row">
            <label for="person-type" class="col-sm-2 col-form-label">Type</label>
            <span class="col-sm-4 col-form-label" id="person-type"></span>
            <label for="person-status" class="col-sm-2 col-form-label">Status</label>
            <span class="col-sm-4 col-form-label" id="person-status"></span>
        </div>
        <div class="form-group row">
            <label for="person-origin" class="col-sm-2 col-form-label">Origin</label>
            <span class="col-sm-4 col-form-label" id="person-origin"></span>
            <label for="person-location" class="col-sm-2 col-form-label">Location</label>
            <span class="col-sm-4 col-form-label" id="person-location"></span>
        </div>
    </form>

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

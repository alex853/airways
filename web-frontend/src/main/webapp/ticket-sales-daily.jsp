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

    <script>

        $(document).ready(function () {
            loadTableData();

            $('.navbar li').each(function() {
                let date = dateByElement(this);
                $(this).html($(this).html().replace('$date$', date));
            });

            $('.navbar li').click(function() {
                $('.navbar li').removeClass('active');
                $(this).addClass('active');

                shownDate = dateByElement(this);
                loadTableData();
            });
        });

        let today = new Date().toISOString().substr(0, 10);
        let shownDate = today;

        function dateByElement(element) {
            let navId = element.id;
            let dayIndexStr = navId.substr(5);
            let dayIndexSign = dayIndexStr.charAt(0);
            let dayIndexValue = parseInt(dayIndexStr.substr(1));
            let dayIndex = dayIndexSign === '0' ? 0 : ((dayIndexSign === 'm' ? -1 : 1) * dayIndexValue);

            let todayDate = new Date(Date.parse(today));
            let selectedDate = new Date(todayDate);
            selectedDate.setUTCDate(selectedDate.getUTCDate() + dayIndex);
            return selectedDate.toISOString().substr(0, 10);
        }

        function loadTableData() {
            $.ajax({
                url: '<%=backendURL%>/misc/ticket-sales/date',
                data: {
                    date: shownDate
                },
                success: function (response) {
                    let records = response;
                    let tableDataContainer = $("#tableData");
                    tableDataContainer.empty();
                    for (let i = 0; i < records.length; i++) {
                        let record = records[i];
                        let rowHtml = $("#tableRowTemplate").html();
                        rowHtml = formatTableRow(rowHtml, record);
                        tableDataContainer.append(rowHtml);
                    }
                },
                error: function (e) {
                    console.log(e.responseText);
                }
            });
        }

        function formatTableRow(dataHtml, record) {
            dataHtml = dataHtml
                //.replace("$uid$", uid)
                .replace("$departureTime$", record.departureTime)
                .replace("$flightNumber$", record.flightNumber)
                .replace("$fromIcao$", record.fromIcao)
                .replace("$toIcao$", record.toIcao)
                .replace("$status$", record.status)
                .replace("$soldTickets$", record.soldTickets)
                .replace("$freeTickets$", record.freeTickets)
                .replace("$totalTickets$", record.totalTickets)
                .replace("$actions$", actionsCell(record));
            return dataHtml;
        }

        function actionsCell(row) {
            return '<a class="btn btn-outline-info btn-sm" href="transport-flight.jsp?id=' + row.id + '">Details</a>';
        }

    </script>
</head>

<body>


<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
    <div class="collapse navbar-collapse justify-content-md-center" id="dateNavbar">
        <ul class="navbar-nav">
            <li class="nav-item" id="date-m4">
                <a class="nav-link" href="#">$date$</a>
            </li>
            <li class="nav-item" id="date-m3">
                <a class="nav-link" href="#">$date$</a>
            </li>
            <li class="nav-item" id="date-m2">
                <a class="nav-link" href="#">$date$</a>
            </li>
            <li class="nav-item" id="date-m1">
                <a class="nav-link" href="#">$date$<br>Yesterday</a>
            </li>
            <li class="nav-item active" id="date-0">
                <a class="nav-link" href="#">$date$<br>TODAY</a>
            </li>
            <li class="nav-item" id="date-p1">
                <a class="nav-link" href="#">$date$<br>Tomorrow</a>
            </li>
            <li class="nav-item" id="date-p2">
                <a class="nav-link" href="#">$date$</a>
            </li>
            <li class="nav-item" id="date-p3">
                <a class="nav-link" href="#">$date$</a>
            </li>
            <li class="nav-item" id="date-p4">
                <a class="nav-link" href="#">$date$</a>
            </li>
        </ul>
    </div>
</nav>


<div class="container">
    <h1>Airways - Ticket Sales</h1>

    <div class="container" id="tableHeader">
        <div class="row border-top p-1">
            <div class="col-1 font-weight-bold">Time</div>
            <div class="col-1 font-weight-bold">Flight #</div>
            <div class="col-1 font-weight-bold">From</div>
            <div class="col-1 font-weight-bold">To</div>
            <div class="col-3 font-weight-bold">Status</div>
            <div class="col-1 font-weight-bold">Sold</div>
            <div class="col-1 font-weight-bold">Free</div>
            <div class="col-1 font-weight-bold">Total</div>
            <div class="col-2 font-weight-bold"></div>
        </div>
    </div> <!-- /tableHeader -->

    <div class="container" id="tableData">
    </div>

    <div style="display: none">
        <div id="tableRowTemplate">
            <div class="row border-top p-1">
                <div class="col-1">$departureTime$</div>
                <div class="col-1">$flightNumber$</div>
                <div class="col-1">$fromIcao$</div>
                <div class="col-1">$toIcao$</div>
                <div class="col-3">$status$</div>
                <div class="col-1">$soldTickets$</div>
                <div class="col-1">$freeTickets$</div>
                <div class="col-1">$totalTickets$</div>
                <div class="col-2">$actions$</div>
            </div>
        </div>
    </div>
</div>


</body>

</html>

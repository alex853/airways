
function showAlert(message, type, closeDelay) {
    var $container = $("#alerts-container");

    if ($container.length === 0) {
        // alerts-container does not exist, create it
        $container = $('<div id="alerts-container">')
            .css({
                position: "fixed"
                ,width: "50%"
                ,left: "25%"
                ,top: "10%"
            })
            .appendTo($("body"));
    }

    // default to alert-info; other options include success, warning, danger
    type = type || "info";

    // create the alert div
    var alert = $('<div>')
        .addClass("fade in show alert alert-" + type)
        .append(
            $('<button type="button" class="close" data-dismiss="alert">')
                .append("&times;")
        )
        .append(message);

    // add the alert div to top of alerts-container, use append() to add to bottom
    $container.prepend(alert);

    // if closeDelay was passed - set a timeout to close the alert
    if (closeDelay)
        window.setTimeout(function() { alert.alert("close") }, closeDelay);
}

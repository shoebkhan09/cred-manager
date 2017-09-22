//Special widget to send data to server directly
var widget;
//Store original value of object gluu_auth.progress
var progress = gluu_auth.progress;
//Timer reference if polling is performed
var timerID;

//This is called when the ready button is pushed
function initialize(wgt){
    if (!widget)
        widget=wgt;
    zk.Widget.$('$throbber').show();
}

//Displays a QR code and associated progress bar
function startQR(request, label, qr_options, timeout, poll) {
    zk.Widget.$('$throbber').hide();
    gluu_auth.renderQrCode('#container', request, qr_options, label);
    gluu_auth.startProgressBar('#progressbar', timeout, callback);
    if (poll)
        timerID=setInterval(tellServer, 4500, "poll");
        /*
        //Starts the session checker with the given timeout
        gluu_auth.startSessionChecker(callback, timeout);
        If this is executed, it immediately ends up calling the callback function with authResult=error. Even if the
        call is delayed with a setTimeout, it always gets authResult=error. Thus, a custom polling mechanism was employed
        for super gluu (see server code)
        */
}

//Notify server about timeout (progress bar reached 100%)
function callback(authResult) {
    tellServer(authResult);
    clean();
}

function tellServer(msg){
    zAu.send(new zk.Event(widget, "onData", msg, {toServer:true}));
}

//Restore the container of QR code and resets progress bar
function clean(){
    $('#container').html('');
    gluu_auth.progress = progress;
    //clearInterval(gluu_auth.progress.timer);
}

function stopPolling(){
    clearInterval(timerID);
    clean();
}
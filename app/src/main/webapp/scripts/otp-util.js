//Special widget to send data to server directly
var widget;
//Store original value of object gluu_auth.progress
var progress = gluu_auth.progress;

//This is called when the ready button is pushed
function initialize(wgt){
    if (!widget)
        widget=wgt;
}

//Notify server about timeout
function callback(authResult) {
    zAu.send(new zk.Event(widget, "onData", authResult, {toServer:true}));
    clean();
}

function clean(){
    $('#container').html('');
    gluu_auth.progress = progress;
}

function startQR(request, label, qr_options, timeout) {
    gluu_auth.renderQrCode('#container', request, qr_options, label);
    gluu_auth.startProgressBar('#progressbar', timeout, callback);
}
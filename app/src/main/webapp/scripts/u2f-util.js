var register_request = null;
var register_timeout;

//Special widget to send data to server directly
var widget;

//This is called when the ready button is pushed
function initialize(wgt){
    if (!widget)
        widget=wgt;
}

function sendBack(obj){
    //zk.log("Sending to server " + JSON.stringify(obj));
    zAu.send(new zk.Event(widget, "onData", obj, {toServer:true}));
}

function triggerU2fRegistration(req, timeout, wait_start){
//alert('trigger ' + JSON.stringify(req));

    register_request=req;
    register_timeout=timeout;
    //Wait ~1 second to start registration
    setTimeout(startRegistration, wait_start);
}

function startRegistration() {
    u2f.register(register_request.registerRequests, register_request.authenticateRequests,
            function (data) {
                sendBack(data);
            }, Math.floor(register_timeout/1000)    //expects seconds
    );
}
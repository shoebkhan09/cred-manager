//Special widget to send data to server directly
var widget;

//This is called when the ready button is pushed
function initialize(wgt, timeout){
    if (!widget)
        widget=wgt;
    setTimeout(notifyBack, timeout);
}

function notifyBack(){
    zAu.send(new zk.Event(widget, "onData", null, {toServer:true}));
}

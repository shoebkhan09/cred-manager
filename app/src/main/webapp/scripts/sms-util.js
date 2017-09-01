//Special widget to send data to server directly
var widget;

//This is called when the send button is pushed
function initialize(wgt, timeout){
    if (!widget)
        widget=wgt;
    setTimeout(notifyBack, timeout);
}

//Send dummy event to server once some time passed (this is a trick to make the button disabled for a while)
function notifyBack(){
    zAu.send(new zk.Event(widget, "onData", null, {toServer:true}));
}

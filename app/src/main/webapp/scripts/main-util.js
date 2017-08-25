function updateStrength(widget){
    var strength=zxcvbn(widget.getValue());
    zAu.send(new zk.Event(widget, "onData", strength.score, {toServer:true}));
}

function getOffset(){
    setTimeout(sendOffset,500);
}

function sendOffset(){
    var offset=new Date().getTimezoneOffset()*60;   //a value in seconds
    //offset=21600;     Set a fix value for testing if dates are correctly displayed
    //offset in Javascript is GMT - localtime, but the converse makes more sense
    zAu.send(new zk.Event(null, "onAfterLoad", {offset: -offset}, {toServer:true}));
}

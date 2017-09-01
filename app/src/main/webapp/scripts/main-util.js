//Computes the strength of password entered in the widget passed and sends the score back to server
function updateStrength(widget){
    var strength=zxcvbn(widget.getValue());
    zAu.send(new zk.Event(widget, "onData", strength.score, {toServer:true}));
}

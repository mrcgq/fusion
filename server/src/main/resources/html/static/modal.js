// Get the modal
var modal = document.getElementById("player-window");

// Get the <span> element that closes the modal
$('#player-window .close').click(function() {
	$('#player-window').css('display', 'none');
	document.getElementById("player").pause();
})

// When the user clicks anywhere outside of the modal, close it
window.onclick = function(event) {
  if (event.target == modal) {
    modal.style.display = "none";
    document.getElementById("player").pause();
  }
}
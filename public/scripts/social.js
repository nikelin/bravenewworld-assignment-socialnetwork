/**
 * Logic implementation for /social/* pages
 */

$(window).ready(function() {
    $(".Social__invite").click(function (event) {
        FB.ui({
            method: 'apprequests',
            message: 'Hey, mate! This app is so awesome, why not to install it today and join me and all my friends here?'
        }, function(response){
            console.log(response);
        });
    })
});
' Create Shell object
Set objShell = WScript.CreateObject("WScript.Shell")

' Test YouTube Search API
objShell.Run("cmd.exe /C start /B java -Dname=youtube_search -jar restest-full.jar data\YouTube\youtube_search.properties > logs/log_youtube_search.log"), 0, True

' Test YouTube Videos API
objShell.Run("cmd.exe /C start /B java -Dname=youtube_getVideos -jar restest-full.jar data\YouTube\youtube_getVideos.properties > logs/log_youtube_getVideos.log"), 0, True

' Test YouTube Comments API
objShell.Run("cmd.exe /C start /B java -Dname=youtube_getCommentThreads -jar restest-full.jar data\YouTube\youtube_getCommentThreads.properties > logs/log_youtube_getCommentThreads.log"), 0, True

' Test Stripe Coupons API
objShell.Run("cmd.exe /C start /B java -Dname=stripe_createCoupon -jar restest-full.jar data\Stripe\stripe_createCoupon.properties > logs/log_stripe_createCoupon.log"), 0, True

' Test Stripe Products API
objShell.Run("cmd.exe /C start /B java -Dname=stripe_createProduct -jar restest-full.jar data\Stripe\stripe_createProduct.properties > logs/log_stripe_createProduct.log"), 0, True

' Test GitHub API
objShell.Run("cmd.exe /C start /B java -Dname=github_getUserRepos -jar restest-full.jar data\GitHub\github_getUserRepos.properties > logs/log_github.log"), 0, True

' Test Tumblr API
objShell.Run("cmd.exe /C start /B java -Dname=tumblr -jar restest-full.jar data\Tumblr\tumblr.properties > logs/log_tumblr.log"), 0, True

' Test Foursquare API
objShell.Run("cmd.exe /C start /B java -Dname=foursquare -jar restest-full.jar data\Foursquare\foursquare.properties > logs/log_foursquare.log"), 0, True

' Test Yelp API
objShell.Run("cmd.exe /C start /B java -Dname=yelp_businessesSearch -jar restest-full.jar data\Yelp\yelp_businessesSearch.properties > logs/yelp_businessesSearch.log"), 0, True

WScript.Echo("The showcase is running in background. You can close this window.")
' Create Shell object
Set objShell = WScript.CreateObject("WScript.Shell")

' Test YouTube Search API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/YouTube/youtube_search.properties > target/logs/log_youtube_search.txt"), 0, True

' Test YouTube Videos API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/YouTube/youtube_getVideos.properties > target/logs/log_youtube_getVideos.txt"), 0, True

' Test YouTube Comments API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/YouTube/youtube_getCommentThreads.properties > target/logs/log_youtube_getCommentThreads.txt"), 0, True

' Test Stripe Coupons API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/Stripe/stripe_createCoupon.properties > target/logs/log_stripe_createCoupon.txt"), 0, True

' Test Stripe Products API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/Stripe/stripe_createProduct.properties > target/logs/log_stripe_createProduct.txt"), 0, True

' Test GitHub API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/GitHub/github_getUserRepos.properties > target/logs/log_github.txt"), 0, True

' Test Tumblr API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/Tumblr/tumblr.properties > target/logs/log_tumblr.txt"), 0, True

' Test Foursquare API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/Foursquare/foursquare.properties > target/logs/log_foursquare.txt"), 0, True

' Test Yelp API
objShell.Run("cmd.exe /C start /B java -jar target/restest-full.jar src/test/resources/Yelp/yelp_businessesSearch.properties > target/logs/yelp_businessesSearch.txt"), 0, True

WScript.Echo("The showcase is running in background. You can close this window.")
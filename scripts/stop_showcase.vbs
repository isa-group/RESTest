dNames = Array("youtube_search", "youtube_getVideos", "youtube_getCommentThreads", "stripe_createCoupon", "stripe_createProduct", "github_getUserRepos", "tumblr", "foursquare", "yelp_businessesSearch")
Set objShell = CreateObject("WScript.Shell")
Set objExec = objShell.Exec("jps -v")

Do While objExec.Status = 0
    WScript.Sleep 100
Loop

results = objExec.StdOut.ReadAll
arrResults = Split(results, vbCrLf)

i = 0
ReDim stoppedTests(0)


For Each result In arrResults
    sp = Split(result)
    If Ubound(sp) > 1 Then
        dName = Split(sp(2), "=")
	If Ubound(dName) > 0 Then
            If Ubound(Filter(dNames, dName(1))) > -1 Then
                objShell.Exec("taskkill /PID " & sp(0) & " /F")
                i = i + 1
		ReDim Preserve stoppedTests(i)
                stoppedTests(i) = dName(1)
            End If
	 End If
    End If
Next

msg = "The following experiment tests have been stopped:"
For Each experiment In stoppedTests
    msg = msg & vbCrLf & experiment
Next

Wscript.Echo(msg)
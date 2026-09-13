$job1 = Start-Job { docker exec ticketrush-postgres psql -U ticketrush -d ticketrush -c "DO `$`$ BEGIN PERFORM id FROM seats WHERE id=5 FOR UPDATE; PERFORM pg_sleep(2); PERFORM id FROM seats WHERE id=9 FOR UPDATE; END `$`$;" 2>&1 }
$job2 = Start-Job { docker exec ticketrush-postgres psql -U ticketrush -d ticketrush -c "DO `$`$ BEGIN PERFORM id FROM seats WHERE id=9 FOR UPDATE; PERFORM pg_sleep(2); PERFORM id FROM seats WHERE id=5 FOR UPDATE; END `$`$;" 2>&1 }
Wait-Job $job1, $job2 | Out-Null
Write-Host "--- Transaction A ---"
Receive-Job $job1
Write-Host "--- Transaction B ---"
Receive-Job $job2

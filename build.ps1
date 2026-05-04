$env:JAVA_HOME = 'E:\Program Files\Android\Android Studio\jbr'
New-Item -ItemType Directory -Force -Path '.tmp\gradle' | Out-Null
& '.\gradlew' ':androidApp:assembleDebug' *>&1 | Out-File -FilePath '.tmp\gradle\log.txt' -Encoding utf8

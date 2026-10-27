# Canlı geliştirme döngüsü: her kaydettiğin değişiklikte uygulamayı derler, bağlı telefona kurar ve yeniden açar.
# Durdurmak için Ctrl+C.
Set-Location $PSScriptRoot
.\gradlew.bat :app:runDebug --continuous --console=plain

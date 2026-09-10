# LHDC QTI software-session route probe v0.2

Этот тест предназначен для текущего PixelOS/garnet и проверяет одну конкретную границу: btaudio_offload_if.so передаёт в AIDL SessionType=2, хотя com.android.bluetooth создаёт software A2DP-сессию (SessionType=1).

Скрипт патчит девять инструкций только в памяти процесса android.hardware.audio.service. Файлы на телефоне не заменяются. После перезапуска audio service исходные байты возвращаются.

## Запуск

Не запускайте старый offload-adapter одновременно с этим тестом.

В Windows CMD:

~~~bat
adb shell pidof android.hardware.audio.service
frida -U -p PID -l x17_qti_software_session_route_v01.js -o lhdc_route_result_v02.txt
~~~

Если btaudio_offload_if.so ещё не загружен, скрипт подождёт его появления. После сообщения [ROUTE:INSTALLED] подключите наушники, выберите LHDC и проиграйте 15–20 секунд.

Для чистого повторения завершите Frida и перезапустите только аудиосервис:

~~~bat
adb shell su -c "killall android.hardware.audio.service"
~~~

Сначала запускайте этот route-тест отдельно. Если LHDC не появляется в переговорах, значит отдельный Bluetooth converter bridge всё ещё нужен; его можно подключать вторым этапом после подтверждения маршрута.

## Что считать результатом

Успешная установка должна показать девять строк [ROUTE:PATCH:OK], затем [ROUTE:INSTALLED]. При открытии потока ожидается:

~~~text
[ROUTE:CALL] audio_stream_open session_arg=1
[ROUTE:RET] audio_stream_open session_arg=1 result=...
[ROUTE:CALL] audio_stream_start session_arg=1
~~~

Одновременно сохраните:

~~~bat
adb logcat -b all -v threadtime -d > lhdc_route_logcat.txt
~~~

Признак полезного результата — исчезновение для этого запуска сообщения bluetooth provider session is not available и появление PCM/read activity. Если сервис перезапустился или Frida отсоединилась, это не постоянное изменение.

## Почему здесь нет Gradle

Это не Android-приложение и не Gradle-проект. Скрипт проверяется через node --check, а архивируется GitHub Actions. Пересборка btaudio_offload_if.so из одного stripped ELF невозможна: для неё нужны исходники Qualcomm/PAL и точный vendor toolchain. Поэтому сначала проверяется live-маршрут, а нативную сборку имеет смысл делать только после подтверждения, что SessionType=1 устраняет разрыв.

Ветка: codex/lhdc-software-session-route-v01.

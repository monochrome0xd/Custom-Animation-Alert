@echo off
REM ============================================================
REM  Upload keystore 생성 스크립트 (Custom Animation Alert)
REM ============================================================
REM
REM  이 스크립트가 만드는 것:
REM   - Upload keystore 파일 (.jks) — Play Store에 첫 업로드 시 사용
REM   - 위치: ../../keystores/custom-animation-alert-upload.jks
REM     (프로젝트 루트의 부모의 부모 폴더 = C:\Users\<유저>\keystores\)
REM
REM  실행 후 할 일:
REM   1. keystore.properties.example 을 keystore.properties로 복사
REM   2. 비밀번호 같이 입력한 값을 keystore.properties 에 그대로 적기
REM   3. 비밀번호는 어디든 안전한 곳에 백업 (1Password, Bitwarden 등)
REM
REM  ⚠️ 경고: 이 키스토어를 잃거나 비밀번호 까먹으면
REM           Play Store에서 이 앱을 영영 업데이트할 수 없습니다.
REM           반드시 백업하세요.
REM
REM  Google Play App Signing 사용 시:
REM   - 이 키스토어는 "Upload key"가 됨 (잃어도 Google에 복구 요청 가능)
REM   - Google이 실제 서명 키를 따로 관리
REM   - Play Console 첫 출시 시 자동 활성화됨 (옵트인 권장)
REM ============================================================

setlocal

set KEYSTORE_DIR=%USERPROFILE%\keystores
set KEYSTORE_FILE=%KEYSTORE_DIR%\custom-animation-alert-upload.jks
set KEY_ALIAS=upload

if not exist "%KEYSTORE_DIR%" (
    echo Creating keystore directory: %KEYSTORE_DIR%
    mkdir "%KEYSTORE_DIR%"
)

if exist "%KEYSTORE_FILE%" (
    echo.
    echo ⚠️ 이미 키스토어가 존재합니다: %KEYSTORE_FILE%
    echo 덮어쓰면 기존 키 영영 사라집니다 ^(Play Store 업데이트 불가^).
    echo 정말 새로 만들려면 기존 파일 먼저 백업한 뒤 수동으로 삭제하세요.
    echo.
    pause
    exit /b 1
)

REM JAVA_HOME 안 잡혀있으면 Android Studio JBR 시도
if "%JAVA_HOME%"=="" (
    set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
)

if not exist "%JAVA_HOME%\bin\keytool.exe" (
    echo ❌ keytool을 찾을 수 없습니다. JAVA_HOME 설정하세요.
    echo    예: set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  Upload keystore 생성
echo ============================================================
echo  저장 위치: %KEYSTORE_FILE%
echo  키 별칭:  %KEY_ALIAS%
echo  유효기간: 27년 ^(10000일^) - Play Store 요구사항 충족
echo.
echo  지금부터 다음 정보를 입력하라는 프롬프트가 뜹니다:
echo   1. 키스토어 비밀번호 ^(strong! 최소 8자, 외우거나 백업하세요^)
echo   2. 이름^(Common Name^): 실명 또는 닉네임
echo   3. 조직 단위/조직: 비워도 됨 ^(Enter^)
echo   4. 도시/주/국가 코드: 비워도 됨
echo   5. 키 비밀번호: 위 키스토어 비밀번호와 같게 하려면 Enter
echo.
pause

"%JAVA_HOME%\bin\keytool.exe" -genkeypair -v ^
    -keystore "%KEYSTORE_FILE%" ^
    -alias %KEY_ALIAS% ^
    -keyalg RSA -keysize 2048 ^
    -validity 10000

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 키스토어 생성 실패
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ============================================================
echo  ✅ 키스토어 생성 완료
echo ============================================================
echo  파일: %KEYSTORE_FILE%
echo.
echo  다음 단계:
echo   1. 이 파일을 다른 위치에도 백업 ^(클라우드 드라이브 등^)
echo   2. 비밀번호를 안전한 곳에 저장 ^(1Password, 노트 앱 등^)
echo   3. 프로젝트 루트의 keystore.properties.example을 keystore.properties로 복사
echo   4. keystore.properties 안의 비밀번호 자리에 입력한 값 적기
echo   5. gradlew bundleRelease 실행해서 AAB 빌드
echo.
pause

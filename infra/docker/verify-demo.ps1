param(
    [string]$BaseUrl = 'http://localhost:13000'
)

$ErrorActionPreference = 'Stop'

$captcha = Invoke-RestMethod "$BaseUrl/prod-api/auth/code"
if ($captcha.code -ne 200 -or $captcha.data.captchaEnabled -ne $false) {
    throw 'The demo captcha endpoint did not return the expected disabled state.'
}

$loginBody = @{
    username = 'demo-admin'
    password = 'certmuse-demo'
    clientId = 'certmuse-demo-web'
    grantType = 'password'
} | ConvertTo-Json

$login = Invoke-RestMethod "$BaseUrl/prod-api/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
if ($login.code -ne 200 -or [string]::IsNullOrWhiteSpace($login.data.access_token)) {
    throw 'The demo administrator login did not return an access token.'
}

$headers = @{ Authorization = "Bearer $($login.data.access_token)"; clientid = 'certmuse-demo-web' }
$userInfo = Invoke-RestMethod "$BaseUrl/prod-api/system/user/getInfo" -Headers $headers
if ($userInfo.code -ne 200 -or $userInfo.data.entryType -ne 'ADMIN' -or $userInfo.data.user.userName -ne 'demo-admin') {
    throw 'The authenticated administrator identity is not valid.'
}

$routers = Invoke-RestMethod "$BaseUrl/prod-api/system/menu/getRouters" -Headers $headers
if ($routers.code -ne 200 -or $routers.data.Count -eq 0) {
    throw 'The administrator route list is empty.'
}

Write-Host 'CertMuse local demo checks passed.'

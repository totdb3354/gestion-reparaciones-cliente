# ──────────────────────────────────────────────────────────────────────────────
# smoke-glass.ps1 — Prueba de humo del backend de Glass (AG/G)
#
# Ejecuta:
#   powershell -ExecutionPolicy Bypass -File smoke-glass.ps1
#
# Rellena las 4 variables de abajo. Crea una asignacion de glass de prueba,
# comprueba la separacion de reparaciones, la completa (AG->G) y limpia todo.
# Compatible con Windows PowerShell 5.1.
# ──────────────────────────────────────────────────────────────────────────────

$base = "http://localhost:8080"          # host:puerto del servidor (preproduccion)
$user = "TU_USUARIO_SUPERTECNICO"        # usuario con rol SUPERTECNICO
$pass = "TU_CONTRASENA"
$imei = "350000000000001"                # IMEI de prueba (15 digitos, ficticio)

$ErrorActionPreference = "Stop"
function ToJson($o) { $o | ConvertTo-Json -Depth 6 }
$pass_count = 0; $fail_count = 0
function Check($cond, $msg) {
    if ($cond) { Write-Host "  OK   - $msg"; $script:pass_count++ }
    else       { Write-Host "  FALLO- $msg" -ForegroundColor Red; $script:fail_count++ }
}

Write-Host "== Smoke test Glass contra $base =="

# 1) Login ----------------------------------------------------------------------
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
    -ContentType "application/json" -Body (ToJson @{ usuario = $user; password = $pass })
$token = $login.token
$H = @{ Authorization = "Bearer $token" }
Check ($token -ne $null) "Login como $($login.nombreUsuario) ($($login.rol))"

# 2) Elegir un tecnico activo ---------------------------------------------------
$tecnicos = Invoke-RestMethod -Method Get -Uri "$base/api/tecnicos/activos" -Headers $H
$idTec = $tecnicos[0].idTec
Write-Host "  -> Tecnico de prueba: $($tecnicos[0].nombre) (idTec=$idTec)"

# 3) Crear asignacion de glass (AG) ---------------------------------------------
$crear = Invoke-RestMethod -Method Post -Uri "$base/api/glass/asignaciones" -Headers $H `
    -ContentType "application/json" `
    -Body (ToJson @{ imei = $imei; idTec = $idTec; comentario = "smoke test glass"; urgente = $false })
$idAG = $crear.value
Check ($idAG -like "AG*") "Creada asignacion de glass con ID '$idAG' (empieza por AG)"

# 4) Aparece en glass y NO en reparaciones (separacion) -------------------------
$glass = Invoke-RestMethod -Method Get -Uri "$base/api/glass/asignaciones" -Headers $H
Check (@($glass | Where-Object { $_.idRep -eq $idAG }).Count -gt 0) "Aparece en /api/glass/asignaciones"

$rep = Invoke-RestMethod -Method Get -Uri "$base/api/reparaciones/asignaciones" -Headers $H
Check (@($rep | Where-Object { $_.idRep -eq $idAG }).Count -eq 0) "NO se cuela en /api/reparaciones/asignaciones"

# 5) Completar AG -> G (con una pieza de glass, reutilizada para no tocar stock) -
$comps = Invoke-RestMethod -Method Get -Uri "$base/api/componentes" -Headers $H
$glassComp = $comps | Where-Object { $_.tipo -match '^[gG]' } | Select-Object -First 1
if ($glassComp -ne $null) {
    $fila = @{ idCom = $glassComp.idCom; cantidad = 1; reutilizado = $true; observacion = "smoke"; esSolicitud = $false; prefijo = "g" }
    Invoke-RestMethod -Method Post -Uri "$base/api/reparaciones/completa" -Headers $H `
        -ContentType "application/json" `
        -Body (ToJson @{ filas = @($fila); imei = $imei; idTec = $idTec; idRepAnterior = $null; idAsignacion = $idAG }) | Out-Null

    $hist = Invoke-RestMethod -Method Get -Uri "$base/api/glass/historial" -Headers $H
    $g = @($hist | Where-Object { $_.imei -eq $imei -and $_.idRep -like "G*" })
    Check ($g.Count -gt 0) "Completada: aparece un 'G' en /api/glass/historial (idRep=$($g[0].idRep))"

    $glass2 = Invoke-RestMethod -Method Get -Uri "$base/api/glass/asignaciones" -Headers $H
    Check (@($glass2 | Where-Object { $_.idRep -eq $idAG }).Count -eq 0) "La AG ya no esta pendiente (se cerro al completar)"

    # 7) Limpieza: borrar la G y la AG de prueba
    foreach ($row in $g) { Invoke-RestMethod -Method Delete -Uri "$base/api/reparaciones/$($row.idRep)" -Headers $H | Out-Null }
    Invoke-RestMethod -Method Delete -Uri "$base/api/reparaciones/asignaciones/$idAG" -Headers $H | Out-Null
    Write-Host "  -> Limpieza: G y AG de prueba borradas"
} else {
    Write-Host "  (No hay ningun componente de glass; salto la prueba de completar. Borro solo la AG.)" -ForegroundColor Yellow
    Invoke-RestMethod -Method Delete -Uri "$base/api/reparaciones/asignaciones/$idAG" -Headers $H | Out-Null
}

Write-Host ""
Write-Host "== RESULTADO: $pass_count OK, $fail_count FALLO =="
if ($fail_count -gt 0) { exit 1 }

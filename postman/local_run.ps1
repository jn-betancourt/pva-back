# local_run.ps1
$baseUrl = "http://localhost:8080"
$results = @()

function Test-Endpoint {
    param(
        [string]$Modulo,
        [string]$Metodo,
        [string]$Ruta,
        [string]$Caso,
        [int]$EsperadoStatus,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [scriptblock]$ExtraAssert = $null
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $status = 0
    $responseObj = $null
    $errorObj = $null

    try {
        $params = @{
            Uri = "$baseUrl$Ruta"
            Method = $Metodo
            Headers = $Headers
            ErrorAction = "Stop"
        }
        if ($Body) {
            $params["Body"] = $Body
            if (-not $Headers.ContainsKey("Content-Type")) {
                $Headers["Content-Type"] = "application/json"
            }
        }
        $raw = Invoke-WebRequest @params
        $sw.Stop()
        $status = [int]$raw.StatusCode
        if ($raw.Content) {
            $responseObj = $raw.Content | ConvertFrom-Json
        }
    }
    catch {
        $sw.Stop()
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errContent = $reader.ReadToEnd()
            try { $errorObj = $errContent | ConvertFrom-Json } catch { $errorObj = $errContent }
        } else {
            $status = 500
            $errorObj = $_.Exception.Message
        }
    }

    $timeMs = $sw.ElapsedMilliseconds
    $ok = ($status -eq $EsperadoStatus) -and ($timeMs -lt 1500)
    $detalles = "Status $status ($timeMs ms)"

    if ($ExtraAssert) {
        try {
            $assertResult = & $ExtraAssert $responseObj $errorObj
            if ($assertResult -ne $true) {
                $ok = $false
                $detalles += " - ExtraAssert fallo: $assertResult"
            }
        } catch {
            $ok = $false
            $detalles += " - ExtraAssert error: $_"
        }
    }

    $resultadoStr = if ($ok) { "OK" } else { "FALLA" }
    
    $global:results += [PSCustomObject]@{
        Modulo = $Modulo
        Endpoint = "$Metodo $Ruta"
        Caso = $Caso
        Esperado = "$EsperadoStatus (<1500ms)"
        Obtenido = "$status (${timeMs}ms)"
        Estado = $resultadoStr
        Response = if ($responseObj) { $responseObj } else { $errorObj }
    }
}

# 1. Auth - Login exitoso
$loginBody = '{"nombre_usuario":"admin","pin":"1234"}'
Test-Endpoint -Modulo "Auth" -Metodo "POST" -Ruta "/api/v1/auth/login" -Caso "Admin exitoso" -EsperadoStatus 200 -Body $loginBody -ExtraAssert {
    param($res, $err)
    if ($res.token -and $res.rol -eq "ADMIN") { $global:token = $res.token; return $true }
    return "Token no encontrado o rol incorrecto"
}

$authHeader = @{ "Authorization" = "Bearer $global:token" }

# 1. Auth - PIN incorrecto (401)
$pinErrBody = '{"nombre_usuario":"admin","pin":"9999"}'
Test-Endpoint -Modulo "Auth" -Metodo "POST" -Ruta "/api/v1/auth/login" -Caso "PIN incorrecto - 401" -EsperadoStatus 401 -Body $pinErrBody -ExtraAssert {
    param($res, $err)
    if ($err.codigo -eq "AUTH-PIN-INCORRECTO") { return $true }
    return "Codigo de error no coincide: $($err.codigo)"
}

# 1. Auth - Operario bloqueado (423)
# Crear operario temporal y bloquearlo
$rnd = Get-Random -Minimum 100000 -Maximum 999999
$cajeroBloqUser = "cajero_bloq_$rnd"
$crearBloqBody = "{`"nombre_completo`":`"Cajero Bloqueo`",`"numero_documento`":`"99$rnd`",`"nombre_usuario`":`"$cajeroBloqUser`",`"pin`":`"1111`",`"rol`":`"CAJERO`"}"
Invoke-RestMethod -Uri "$baseUrl/api/v1/operarios" -Method POST -Headers $authHeader -ContentType "application/json" -Body $crearBloqBody | Out-Null
for ($i=0; $i -lt 3; $i++) {
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method POST -ContentType "application/json" -Body "{`"nombre_usuario`":`"$cajeroBloqUser`",`"pin`":`"0000`"}" | Out-Null
    } catch {}
}

$loginBloqBody = "{`"nombre_usuario`":`"$cajeroBloqUser`",`"pin`":`"0000`"}"
Test-Endpoint -Modulo "Auth" -Metodo "POST" -Ruta "/api/v1/auth/login" -Caso "Operario bloqueado - 423" -EsperadoStatus 423 -Body $loginBloqBody -ExtraAssert {
    param($res, $err)
    if ($err.codigo -eq "AUTH-OPERARIO-BLOQUEADO") { return $true }
    return "Codigo de error no coincide: $($err.codigo)"
}

# 1. Auth - Logout (204)
$tempLogin = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
$tempTokenHeader = @{ "Authorization" = "Bearer $($tempLogin.token)" }
Test-Endpoint -Modulo "Auth" -Metodo "POST" -Ruta "/api/v1/auth/logout" -Caso "Logout exitoso" -EsperadoStatus 204 -Headers $tempTokenHeader

# 2. Operarios - GET /operarios
Test-Endpoint -Modulo "Operarios" -Metodo "GET" -Ruta "/api/v1/operarios" -Caso "Listar operarios" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res -is [array])
}

# 2. Operarios - POST /operarios
$rndDoc = Get-Random -Minimum 100000 -Maximum 999999
$cajeroUser = "cajero_$rndDoc"
$crearOpeBody = "{`"nombre_completo`":`"Cajero Test Script`",`"numero_documento`":`"10$rndDoc`",`"telefono`":`"3001112233`",`"nombre_usuario`":`"$cajeroUser`",`"pin`":`"4321`",`"rol`":`"CAJERO`"}"
Test-Endpoint -Modulo "Operarios" -Metodo "POST" -Ruta "/api/v1/operarios" -Caso "Crear operario" -EsperadoStatus 201 -Headers $authHeader -Body $crearOpeBody -ExtraAssert {
    param($res, $err)
    if ($res.id_operario) { $global:id_operario = $res.id_operario; return $true }
    return "id_operario no retornado"
}

# 2. Operarios - GET /operarios/{id}
Test-Endpoint -Modulo "Operarios" -Metodo "GET" -Ruta "/api/v1/operarios/$global:id_operario" -Caso "Obtener operario por ID" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.id_operario -eq $global:id_operario)
}

# 2. Operarios - PUT /operarios/{id}
$putOpeBody = "{`"nombre_completo`":`"Cajero Nombre Modificado`",`"telefono`":`"3112223344`",`"nombre_usuario`":null,`"rol`":`"CAJERO`"}"
Test-Endpoint -Modulo "Operarios" -Metodo "PUT" -Ruta "/api/v1/operarios/$global:id_operario" -Caso "Actualizar operario" -EsperadoStatus 200 -Headers $authHeader -Body $putOpeBody -ExtraAssert {
    param($res, $err)
    return ($res.nombre_completo -eq "Cajero Nombre Modificado")
}

# 2. Operarios - PATCH /operarios/{id}/pin
$pinOpeBody = '{"pin_nuevo":"5678"}'
Test-Endpoint -Modulo "Operarios" -Metodo "PATCH" -Ruta "/api/v1/operarios/$global:id_operario/pin" -Caso "Actualizar PIN" -EsperadoStatus 200 -Headers $authHeader -Body $pinOpeBody

# 2. Operarios - PATCH /operarios/{id}/desbloquear
Test-Endpoint -Modulo "Operarios" -Metodo "PATCH" -Ruta "/api/v1/operarios/$global:id_operario/desbloquear" -Caso "Desbloquear operario" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.intentos_fallidos -eq 0)
}

# 2. Operarios - PATCH /operarios/{id}/inactivar
Test-Endpoint -Modulo "Operarios" -Metodo "PATCH" -Ruta "/api/v1/operarios/$global:id_operario/inactivar" -Caso "Inactivar operario" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.activo -eq $false)
}

# 3. Turnos - Cerrar turno activo previo si existe
try {
    $act = Invoke-RestMethod -Uri "$baseUrl/api/v1/turnos/activo" -Method GET -Headers $authHeader
    if ($act.id_turno) {
        Invoke-RestMethod -Uri "$baseUrl/api/v1/turnos/$($act.id_turno)/cerrar" -Method POST -Headers $authHeader -ContentType "application/json" -Body '{"monto_fisico_arqueo":50000.00,"observaciones_cierre":"Limpieza previa"}' | Out-Null
    }
} catch {}

# 3. Turnos - POST /turnos/abrir
$abrirTurnoBody = '{"base_efectivo_inicial":50000.00}'
Test-Endpoint -Modulo "Turnos de Caja" -Metodo "POST" -Ruta "/api/v1/turnos/abrir" -Caso "Abrir turno" -EsperadoStatus 201 -Headers $authHeader -Body $abrirTurnoBody -ExtraAssert {
    param($res, $err)
    if ($res.id_turno -and $res.estado -eq "ABIERTO") { $global:id_turno = $res.id_turno; return $true }
    return "id_turno no generado o estado no ABIERTO"
}

# 3. Turnos - GET /turnos/activo
Test-Endpoint -Modulo "Turnos de Caja" -Metodo "GET" -Ruta "/api/v1/turnos/activo" -Caso "Consultar turno activo" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.id_turno -eq $global:id_turno -and $res.estado -eq "ABIERTO")
}

# 3. Turnos - GET /turnos/{id}
Test-Endpoint -Modulo "Turnos de Caja" -Metodo "GET" -Ruta "/api/v1/turnos/$global:id_turno" -Caso "Consultar turno por ID" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.id_turno -eq $global:id_turno)
}

# 3. Turnos - POST /turnos/{id}/cerrar
$cerrarTurnoBody = '{"monto_fisico_arqueo":50000.00,"observaciones_cierre":"Cierre de prueba"}'
Test-Endpoint -Modulo "Turnos de Caja" -Metodo "POST" -Ruta "/api/v1/turnos/$global:id_turno/cerrar" -Caso "Cerrar turno con arqueo" -EsperadoStatus 200 -Headers $authHeader -Body $cerrarTurnoBody -ExtraAssert {
    param($res, $err)
    return ($res.estado -eq "CERRADO")
}

# 4. Categorías - POST /categorias
$rndCat = Get-Random -Minimum 100000 -Maximum 999999
$crearCatBody = "{`"nombre`":`"Bebidas $rndCat`",`"descripcion`":`"Refrescos`"}"
Test-Endpoint -Modulo "Categorias" -Metodo "POST" -Ruta "/api/v1/categorias" -Caso "Crear categoría" -EsperadoStatus 201 -Headers $authHeader -Body $crearCatBody -ExtraAssert {
    param($res, $err)
    if ($res.id_categoria) { $global:id_categoria = $res.id_categoria; return $true }
    return "id_categoria no generado"
}

# 4. Categorías - GET /categorias
Test-Endpoint -Modulo "Categorias" -Metodo "GET" -Ruta "/api/v1/categorias" -Caso "Listar categorías" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res -is [array])
}

# 4. Categorías - PUT /categorias/{id}
$putCatBody = "{`"nombre`":`"Bebidas Modificadas $rndCat`",`"descripcion`":`"Actualizada`"}"
Test-Endpoint -Modulo "Categorias" -Metodo "PUT" -Ruta "/api/v1/categorias/$global:id_categoria" -Caso "Actualizar categoría" -EsperadoStatus 200 -Headers $authHeader -Body $putCatBody

# 4. Categorías - PATCH /categorias/{id}/inactivar (usando categoria temporal)
$catInactivar = Invoke-RestMethod -Uri "$baseUrl/api/v1/categorias" -Method POST -Headers $authHeader -ContentType "application/json" -Body "{`"nombre`":`"Cat Inact $rndCat`",`"descripcion`":`"desc`"}"
Test-Endpoint -Modulo "Categorias" -Metodo "PATCH" -Ruta "/api/v1/categorias/$($catInactivar.id_categoria)/inactivar" -Caso "Inactivar categoría" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.activo -eq $false)
}

# 5. Productos - POST /productos
$rndProd = Get-Random -Minimum 100000 -Maximum 999999
$barcode = "770" + $rndProd.ToString().PadLeft(10, '0')
$crearProdBody = "{`"codigo_barras`":`"$barcode`",`"nombre`":`"Gaseosa $rndProd`",`"descripcion`":`"500ml`",`"id_categoria`":$global:id_categoria,`"precio_venta`":4500.00,`"costo_inicial`":2500.00,`"stock_inicial`":50,`"aplica_iva`":true,`"porcentaje_iva`":19.00,`"stock_minimo`":10,`"unidad_medida`":`"UNIDAD`"}"
Test-Endpoint -Modulo "Productos" -Metodo "POST" -Ruta "/api/v1/productos" -Caso "Crear producto" -EsperadoStatus 201 -Headers $authHeader -Body $crearProdBody -ExtraAssert {
    param($res, $err)
    if ($res.id_producto) {
        $global:id_producto = $res.id_producto
        $global:codigo_barras = $barcode
        return $true
    }
    return "id_producto no generado"
}

# 5. Productos - GET /productos
Test-Endpoint -Modulo "Productos" -Metodo "GET" -Ruta "/api/v1/productos" -Caso "Listar productos" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res -is [array])
}

# 5. Productos - GET /productos/{id}
Test-Endpoint -Modulo "Productos" -Metodo "GET" -Ruta "/api/v1/productos/$global:id_producto" -Caso "Obtener producto por ID" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.id_producto -eq $global:id_producto)
}

# 5. Productos - GET /productos/barcode/{codigo}
Test-Endpoint -Modulo "Productos" -Metodo "GET" -Ruta "/api/v1/productos/barcode/$global:codigo_barras" -Caso "Buscar por código de barras" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.codigo_barras -eq $global:codigo_barras)
}

# 5. Productos - PUT /productos/{id}
$putProdBody = "{`"codigo_barras`":`"$global:codigo_barras`",`"nombre`":`"Gaseosa Modificada $rndProd`",`"descripcion`":`"600ml`",`"id_categoria`":$global:id_categoria,`"precio_venta`":4800.00,`"aplica_iva`":true,`"porcentaje_iva`":19.00,`"stock_minimo`":8,`"unidad_medida`":`"UNIDAD`"}"
Test-Endpoint -Modulo "Productos" -Metodo "PUT" -Ruta "/api/v1/productos/$global:id_producto" -Caso "Actualizar producto" -EsperadoStatus 200 -Headers $authHeader -Body $putProdBody

# 5. Productos - PATCH /productos/{id}/inactivar (usando producto temporal)
$prodInact = Invoke-RestMethod -Uri "$baseUrl/api/v1/productos" -Method POST -Headers $authHeader -ContentType "application/json" -Body "{`"codigo_barras`":`"99$rndProd`",`"nombre`":`"Prod Inact $rndProd`",`"descripcion`":`"desc`",`"id_categoria`":$global:id_categoria,`"precio_venta`":2000.00,`"costo_inicial`":1000.00,`"stock_inicial`":5,`"aplica_iva`":false,`"stock_minimo`":1,`"unidad_medida`":`"UNIDAD`"}"
Test-Endpoint -Modulo "Productos" -Metodo "PATCH" -Ruta "/api/v1/productos/$($prodInact.id_producto)/inactivar" -Caso "Inactivar producto" -EsperadoStatus 200 -Headers $authHeader -ExtraAssert {
    param($res, $err)
    return ($res.activo -eq $false)
}

# 6. Inventario - POST /inventario/compra
$compraBody = "{`"id_producto`":$global:id_producto,`"cantidad`":20,`"costo_unitario`":2800.00,`"id_referencia`":`"FAC-PROV-9988`",`"motivo_ajuste`":null}"
Test-Endpoint -Modulo "Inventario" -Metodo "POST" -Ruta "/api/v1/inventario/compra" -Caso "Registrar compra y recalcular CPP" -EsperadoStatus 201 -Headers $authHeader -Body $compraBody -ExtraAssert {
    param($res, $err)
    return ($res.tipo_movimiento -eq "COMPRA" -and $res.saldo_resultante -eq 70)
}

# Exportar reporte en JSON
$global:results | Select-Object Modulo, Endpoint, Caso, Esperado, Obtenido, Estado | Format-Table -AutoSize
$global:results | ConvertTo-Json -Depth 3 | Out-File -FilePath "postman/test_results.json" -Encoding utf8

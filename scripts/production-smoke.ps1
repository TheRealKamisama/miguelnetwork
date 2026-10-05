param(
    [ValidateSet('CLIENT','SERVER')][string]$Side='CLIENT',
    [string]$ZstdJar='',
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$GradleHome=$env:GRADLE_USER_HOME
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if(-not $GradleHome){$GradleHome=Join-Path $env:USERPROFILE '.gradle'}
if(-not $JavaHome){throw 'Pass -JavaHome for the target JDK.'}
$properties=ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)
$version=$properties.minecraft_version
$classpathFile=Join-Path $repo 'build/smoke-runtime-classpath.txt'
if(-not (Test-Path -LiteralPath $classpathFile)){throw 'Run the Gradle build and exportSmokeClasspath tasks first.'}
$distribution=Join-Path $repo "build/libs/miguelnetwork-fabric-$version-$($properties.mod_version).jar"
if(-not (Test-Path -LiteralPath $distribution)){throw 'Build the production remapJar first.'}
$classes=Join-Path $repo 'build/smoke-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& "$JavaHome/bin/javac.exe" --release $properties.java_version -d $classes (Join-Path $PSScriptRoot 'ProductionSmoke.java')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$paths=Get-Content $classpathFile | Where-Object { $_ -notmatch 'fabric-loom|remapped_mods|night-config|fabric-api' }
$game=if($Side -eq 'CLIENT'){'minecraft-client.jar'}else{'minecraft-extracted_server.jar'}
$gamePath=Join-Path $GradleHome "caches/fabric-loom/$version/$game"
$intermediary=Get-ChildItem (Join-Path $GradleHome "caches/modules-2/files-2.1/net.fabricmc/intermediary/$version") -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$api=Get-ChildItem (Join-Path $GradleHome "caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/$($properties.fabric_api_version)") -Recurse -Filter '*.jar' | Where-Object Name -NotMatch 'sources' | Select-Object -First 1 -ExpandProperty FullName
if(-not $api -or -not $intermediary -or -not (Test-Path -LiteralPath $gamePath)){throw 'Production game/API libraries are missing; complete the Loom build first.'}
$mods=@($distribution,$api)
if($ZstdJar){$mods+=(Resolve-Path -LiteralPath $ZstdJar).Path}
# Every invocation owns a fresh instance; never overwrite a previous run's logs/configuration.
$instance=Join-Path $repo ('run/production-smoke-'+$Side+'-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $instance | Out-Null
$classpath=(@($classes,$gamePath,$intermediary)+$paths) -join [IO.Path]::PathSeparator
$arguments=@('-Xmx1G','-Dfabric.development=false',"-Dfabric.gameVersion=$version", "-Dfabric.gameJarPath=$gamePath", ('-Dfabric.addMods='+($mods -join [IO.Path]::PathSeparator)), '-cp',$classpath,'ProductionSmoke',$Side,([bool]$ZstdJar).ToString().ToLower(),$version,$instance)
$argumentFile=Join-Path $instance 'java-arguments.txt'
[IO.File]::WriteAllLines($argumentFile,($arguments | ForEach-Object {'"'+$_.Replace('\','/').Replace('"','\"')+'"'}),[Text.UTF8Encoding]::new($false))
Push-Location $instance
try {
    & "$JavaHome/bin/java.exe" "@$argumentFile" *> (Join-Path $instance 'smoke.log')
    $code=$LASTEXITCODE
    Get-Content (Join-Path $instance 'smoke.log') -Tail 20
    Write-Output "Smoke evidence: $instance"
    exit $code
} finally {Pop-Location}

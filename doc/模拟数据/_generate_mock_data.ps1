$ErrorActionPreference='Stop'
function F([datetime]$d){$d.ToString('yyyy-MM-dd HH:mm:ss')}
function FD([datetime]$d){$d.ToString('yyyy-MM-dd')}
$out=Join-Path (Get-Location) 'doc\模拟数据'
New-Item -Path $out -ItemType Directory -Force|Out-Null
function W([string]$n,[array]$r){$r|Export-Csv -Path (Join-Path $out ($n+'.csv')) -NoTypeInformation -Encoding UTF8}
$rand=[System.Random]::new(20260322)

$p=@(
[pscustomobject][ordered]@{product_code='P-ANGIO-001';product_name='ANGIO_CATHETER';spec_model='6F_100CM';uom='PCS';active_flag=1;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{product_code='P-BALLOON-001';product_name='BALLOON_CATHETER';spec_model='2.5_20MM';uom='PCS';active_flag=1;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{product_code='P-STENT-001';product_name='CARDIAC_STENT';spec_model='3.0_18MM';uom='PCS';active_flag=1;updated_at='2026-03-22 09:00:00'})
W 'product' $p

$proc=@('010,RAW_PREP,0','020,EXTRUSION,1','030,CUTTING,1','040,HEAT_TREAT,1','050,COATING,1','060,BRAIDING,1','070,ASSEMBLY,1','080,CLEANING,0','090,STERILIZATION,1','100,FINAL_QC,1','110,PACKING,0')
$prows=@();foreach($x in $proc){$a=$x.Split(',');$prows+=[pscustomobject][ordered]@{process_code='PROC-'+$a[0];process_name=$a[1];critical_flag=[int]$a[2];active_flag=1;updated_at='2026-03-22 09:00:00'}}
W 'process' $prows

$rhead=@(
[pscustomobject][ordered]@{route_no='R-ANGIO-V3';product_code='P-ANGIO-001';route_version='V3';status='ACTIVE';effective_from='2026-03-20 00:00:00';updated_at='2026-03-22 09:30:00'},
[pscustomobject][ordered]@{route_no='R-BALLOON-V2';product_code='P-BALLOON-001';route_version='V2';status='ACTIVE';effective_from='2026-03-20 00:00:00';updated_at='2026-03-22 09:30:00'},
[pscustomobject][ordered]@{route_no='R-STENT-V5';product_code='P-STENT-001';route_version='V5';status='ACTIVE';effective_from='2026-03-20 00:00:00';updated_at='2026-03-22 09:30:00'})
W 'product_route_header' $rhead

$rm=@{
'R-ANGIO-V3'=@('PROC-010','PROC-020','PROC-030','PROC-050','PROC-070','PROC-080','PROC-090','PROC-100','PROC-110');
'R-BALLOON-V2'=@('PROC-010','PROC-020','PROC-040','PROC-050','PROC-070','PROC-080','PROC-100','PROC-110');
'R-STENT-V5'=@('PROC-010','PROC-060','PROC-030','PROC-040','PROC-070','PROC-080','PROC-090','PROC-100','PROC-110')}
$rb=@{'R-ANGIO-V3'='P-ANGIO-001';'R-BALLOON-V2'='P-BALLOON-001';'R-STENT-V5'='P-STENT-001'}
$std=@{'PROC-010'=@{c=1800;m=2;e=1;s=20};'PROC-020'=@{c=1200;m=2;e=1;s=25};'PROC-030'=@{c=1500;m=1;e=1;s=15};'PROC-040'=@{c=1100;m=2;e=1;s=30};'PROC-050'=@{c=1000;m=2;e=1;s=35};'PROC-060'=@{c=900;m=2;e=1;s=30};'PROC-070'=@{c=950;m=3;e=1;s=25};'PROC-080'=@{c=1400;m=1;e=1;s=10};'PROC-090'=@{c=1000;m=1;e=1;s=20};'PROC-100'=@{c=1600;m=2;e=1;s=15};'PROC-110'=@{c=1700;m=2;e=1;s=10}}
$pr=@();$dep=@()
foreach($r in $rm.Keys){$seq=10;$st=$rm[$r];for($i=0;$i -lt $st.Count;$i++){$p0=$st[$i];$v=$std[$p0];$pr+=[pscustomobject][ordered]@{route_no=$r;product_code=$rb[$r];process_code=$p0;sequence_no=$seq;capacity_per_shift=[math]::Round($v.c*(0.92+$rand.NextDouble()*0.16),0);required_manpower_per_group=$v.m;required_equipment_count=$v.e;enabled_flag=1;updated_at='2026-03-22 09:35:00'};if($i -lt $st.Count-1){$dep+=[pscustomobject][ordered]@{route_no=$r;from_process_code=$st[$i];to_process_code=$st[$i+1];dependency_type='FS';lag_minutes=0;updated_at='2026-03-22 09:40:00'}};$seq+=10}}
W 'process_route' $pr
W 'route_step_dependency' $dep

$prr=@();foreach($k in $std.Keys){$v=$std[$k];$prr+=[pscustomobject][ordered]@{process_code=$k;required_manpower_per_group=$v.m;required_equipment_count=$v.e;std_output_per_shift=$v.c;setup_time_minutes=$v.s;updated_at='2026-03-22 09:45:00'}}
W 'process_resource_requirement' ($prr|Sort-Object process_code)
$org=@(
[pscustomobject][ordered]@{unit_code='FAC-01';unit_name='FACTORY_MAIN';unit_type='FACTORY';parent_unit_code='';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='WS-A';unit_name='WORKSHOP_A';unit_type='WORKSHOP';parent_unit_code='FAC-01';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='WS-B';unit_name='WORKSHOP_B';unit_type='WORKSHOP';parent_unit_code='FAC-01';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-A01';unit_name='LINE_A01';unit_type='LINE';parent_unit_code='WS-A';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-A02';unit_name='LINE_A02';unit_type='LINE';parent_unit_code='WS-A';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-A03';unit_name='LINE_A03';unit_type='LINE';parent_unit_code='WS-A';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-A04';unit_name='LINE_A04';unit_type='LINE';parent_unit_code='WS-A';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-B01';unit_name='LINE_B01';unit_type='LINE';parent_unit_code='WS-B';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-B02';unit_name='LINE_B02';unit_type='LINE';parent_unit_code='WS-B';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-B03';unit_name='LINE_B03';unit_type='LINE';parent_unit_code='WS-B';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='LINE-B04';unit_name='LINE_B04';unit_type='LINE';parent_unit_code='WS-B';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-A01';unit_name='TEAM_A01';unit_type='TEAM';parent_unit_code='LINE-A01';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-A02';unit_name='TEAM_A02';unit_type='TEAM';parent_unit_code='LINE-A02';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-A03';unit_name='TEAM_A03';unit_type='TEAM';parent_unit_code='LINE-A03';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-A04';unit_name='TEAM_A04';unit_type='TEAM';parent_unit_code='LINE-A04';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-B01';unit_name='TEAM_B01';unit_type='TEAM';parent_unit_code='LINE-B01';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-B02';unit_name='TEAM_B02';unit_type='TEAM';parent_unit_code='LINE-B02';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-B03';unit_name='TEAM_B03';unit_type='TEAM';parent_unit_code='LINE-B03';active_flag=1;updated_at='2026-03-22 08:40:00'},
[pscustomobject][ordered]@{unit_code='TEAM-B04';unit_name='TEAM_B04';unit_type='TEAM';parent_unit_code='LINE-B04';active_flag=1;updated_at='2026-03-22 08:40:00'})
W 'organization_unit' $org

$eq=@(
[pscustomobject][ordered]@{equipment_code='EQ-PREP-01';workshop_code='WS-A';line_code='LINE-A01';status='RUNNING';capacity_per_shift=1800;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-EXT-01';workshop_code='WS-A';line_code='LINE-A01';status='RUNNING';capacity_per_shift=1200;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-EXT-02';workshop_code='WS-A';line_code='LINE-A02';status='RUNNING';capacity_per_shift=1100;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-CUT-01';workshop_code='WS-A';line_code='LINE-A02';status='RUNNING';capacity_per_shift=1500;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-CUT-02';workshop_code='WS-B';line_code='LINE-B01';status='RUNNING';capacity_per_shift=1300;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-HEAT-01';workshop_code='WS-B';line_code='LINE-B01';status='RUNNING';capacity_per_shift=1100;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-HEAT-02';workshop_code='WS-B';line_code='LINE-B01';status='MAINTENANCE';capacity_per_shift=900;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-COAT-01';workshop_code='WS-A';line_code='LINE-A03';status='RUNNING';capacity_per_shift=1000;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-COAT-02';workshop_code='WS-A';line_code='LINE-A03';status='RUNNING';capacity_per_shift=950;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-BRAID-01';workshop_code='WS-B';line_code='LINE-B02';status='RUNNING';capacity_per_shift=820;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-BRAID-02';workshop_code='WS-B';line_code='LINE-B02';status='RUNNING';capacity_per_shift=780;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-ASM-01';workshop_code='WS-A';line_code='LINE-A04';status='RUNNING';capacity_per_shift=930;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-ASM-02';workshop_code='WS-B';line_code='LINE-B03';status='RUNNING';capacity_per_shift=880;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-WASH-01';workshop_code='WS-A';line_code='LINE-A04';status='RUNNING';capacity_per_shift=1400;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-WASH-02';workshop_code='WS-B';line_code='LINE-B03';status='RUNNING';capacity_per_shift=1300;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-STER-01';workshop_code='WS-A';line_code='LINE-A03';status='RUNNING';capacity_per_shift=1020;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-STER-02';workshop_code='WS-B';line_code='LINE-B04';status='RUNNING';capacity_per_shift=980;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-QC-01';workshop_code='WS-A';line_code='LINE-A04';status='RUNNING';capacity_per_shift=1600;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-PACK-01';workshop_code='WS-A';line_code='LINE-A04';status='RUNNING';capacity_per_shift=1700;updated_at='2026-03-22 09:00:00'},
[pscustomobject][ordered]@{equipment_code='EQ-PACK-02';workshop_code='WS-B';line_code='LINE-B04';status='RUNNING';capacity_per_shift=1650;updated_at='2026-03-22 09:00:00'})
W 'equipment_capacity' $eq

$ep=@(
[pscustomobject][ordered]@{equipment_code='EQ-PREP-01';process_code='PROC-010';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-EXT-01';process_code='PROC-020';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-EXT-02';process_code='PROC-020';enabled_flag=1;capacity_factor=0.95;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-CUT-01';process_code='PROC-030';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-CUT-02';process_code='PROC-030';enabled_flag=1;capacity_factor=0.90;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-HEAT-01';process_code='PROC-040';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-HEAT-02';process_code='PROC-040';enabled_flag=0;capacity_factor=0.80;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-COAT-01';process_code='PROC-050';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-COAT-02';process_code='PROC-050';enabled_flag=1;capacity_factor=0.92;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-BRAID-01';process_code='PROC-060';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-BRAID-02';process_code='PROC-060';enabled_flag=1;capacity_factor=0.95;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-ASM-01';process_code='PROC-070';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-ASM-02';process_code='PROC-070';enabled_flag=1;capacity_factor=0.95;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-WASH-01';process_code='PROC-080';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-WASH-02';process_code='PROC-080';enabled_flag=1;capacity_factor=0.93;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-STER-01';process_code='PROC-090';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-STER-02';process_code='PROC-090';enabled_flag=1;capacity_factor=0.96;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-QC-01';process_code='PROC-100';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-PACK-01';process_code='PROC-110';enabled_flag=1;capacity_factor=1.00;updated_at='2026-03-22 09:05:00'},
[pscustomobject][ordered]@{equipment_code='EQ-PACK-02';process_code='PROC-110';enabled_flag=1;capacity_factor=0.97;updated_at='2026-03-22 09:05:00'})
W 'equipment_process_capability' $ep
$teams=@('TEAM-A01','TEAM-A02','TEAM-A03','TEAM-A04','TEAM-B01','TEAM-B02','TEAM-B03','TEAM-B04')
$emp=@();for($i=1;$i -le 63;$i++){$id='E'+(10000+$i);$tm=$teams[($i-1)%$teams.Count];$st='ACTIVE';if($i%29 -eq 0){$st='LEAVE'}elseif($i%37 -eq 0){$st='DISABLED'};$emp+=[pscustomobject][ordered]@{employee_id=$id;employee_name='EMP_'+$i;team_code=$tm;status=$st;updated_at='2026-03-22 08:55:00'}}
W 'employee' $emp

$ts=@{'TEAM-A01'=@('PROC-010','PROC-020','PROC-030');'TEAM-A02'=@('PROC-020','PROC-050','PROC-070');'TEAM-A03'=@('PROC-070','PROC-080','PROC-100');'TEAM-A04'=@('PROC-080','PROC-090','PROC-110');'TEAM-B01'=@('PROC-010','PROC-060','PROC-030');'TEAM-B02'=@('PROC-040','PROC-060','PROC-070');'TEAM-B03'=@('PROC-070','PROC-080','PROC-100');'TEAM-B04'=@('PROC-090','PROC-100','PROC-110')}
$es=@();foreach($e in $emp){$sk=@($ts[$e.team_code]);if(([int]$e.employee_id.Substring(1)%5)-eq 0){$sk+='PROC-110'};$sk=$sk|Select-Object -Unique;for($j=0;$j -lt $sk.Count;$j++){$lv=if($j -le 1){'INDEPENDENT'}else{'ASSIST'};$af=if($e.status -eq 'ACTIVE'){1}else{0};$es+=[pscustomobject][ordered]@{employee_id=$e.employee_id;process_code=$sk[$j];skill_level=$lv;efficiency_factor=[math]::Round((0.85+$rand.NextDouble()*0.35),2);active_flag=$af;updated_at='2026-03-22 09:10:00'}}}
W 'employee_skill' $es

$cal=@();$cs=[datetime]'2026-03-23 00:00:00'
for($d=0;$d -lt 14;$d++){$dt=$cs.AddDays($d);foreach($ws in @('WS-A','WS-B')){$sun=($dt.DayOfWeek -eq [System.DayOfWeek]::Sunday);$do=if($sun){0}else{1};$no=0;if(-not $sun){if($ws -eq 'WS-A' -and $dt.DayOfWeek -ne [System.DayOfWeek]::Saturday){$no=1};if($ws -eq 'WS-B' -and ($d%2 -eq 0)){$no=1}};$ds=[datetime]::Parse((FD $dt)+' 08:00:00');$de=$ds.AddHours(12);$ns=[datetime]::Parse((FD $dt)+' 20:00:00');$ne=$ns.AddHours(12);$cal+=[pscustomobject][ordered]@{calendar_date=FD $dt;shift_code='DAY';shift_start_time=F $ds;shift_end_time=F $de;open_flag=$do;workshop_code=$ws;updated_at='2026-03-22 17:00:00'};$cal+=[pscustomobject][ordered]@{calendar_date=FD $dt;shift_code='NIGHT';shift_start_time=F $ns;shift_end_time=F $ne;open_flag=$no;workshop_code=$ws;updated_at='2026-03-22 17:00:00'}}}
W 'shift_calendar' $cal

$av=@();for($d=0;$d -lt 7;$d++){$dt=$cs.AddDays($d);foreach($e in $emp){foreach($sh in @('DAY','NIGHT')){$sun=($dt.DayOfWeek -eq [System.DayOfWeek]::Sunday);$af=0;$st='OVERTIME_OFF';if($sh -eq 'DAY'){if(-not $sun){$af=1;$st='AVAILABLE'}}else{$ok=(([int]$e.employee_id.Substring(1))%3 -eq 0);if((-not $sun)-and $ok){$af=1;$st='AVAILABLE'}};if($e.status -ne 'ACTIVE'){$af=0;$st=if($e.status -eq 'LEAVE'){'LEAVE'}else{'OVERTIME_OFF'}};if((FD $dt)-eq '2026-03-25' -and (([int]$e.employee_id.Substring(1))%17 -eq 0)){$af=0;$st='LEAVE'};if((FD $dt)-eq '2026-03-26' -and (([int]$e.employee_id.Substring(1))%19 -eq 0)){$af=0;$st='SICK'};if((FD $dt)-eq '2026-03-27' -and (([int]$e.employee_id.Substring(1))%13 -eq 0)-and $sh -eq 'DAY'){$af=0;$st='TRAINING'};$stt=if($sh -eq 'DAY'){[datetime]::Parse((FD $dt)+' 08:00:00')}else{[datetime]::Parse((FD $dt)+' 20:00:00')};$av+=[pscustomobject][ordered]@{employee_id=$e.employee_id;calendar_date=FD $dt;shift_code=$sh;available_flag=$af;availability_status=$st;effective_start_time=F $stt;effective_end_time=F ($stt.AddHours(12));updated_at='2026-03-22 17:05:00'}}}}
W 'employee_shift_availability' $av

$de=@();$ev=@(
@{eq='EQ-COAT-01';s='2026-03-23 13:10:00';e='2026-03-23 14:05:00';r='MAINT';k=1},
@{eq='EQ-EXT-02';s='2026-03-24 09:30:00';e='2026-03-24 10:20:00';r='FAULT';k=1},
@{eq='EQ-HEAT-02';s='2026-03-24 11:00:00';e='2026-03-24 15:30:00';r='MAINT';k=0},
@{eq='EQ-BRAID-01';s='2026-03-25 20:20:00';e='2026-03-25 21:00:00';r='FAULT';k=1},
@{eq='EQ-STER-01';s='2026-03-26 07:50:00';e='2026-03-26 08:35:00';r='CHANGEOVER';k=1},
@{eq='EQ-QC-01';s='2026-03-26 15:00:00';e='2026-03-26 15:25:00';r='CALIB';k=0},
@{eq='EQ-PACK-02';s='2026-03-27 10:10:00';e='2026-03-27 10:55:00';r='FAULT';k=0},
@{eq='EQ-WASH-02';s='2026-03-27 19:30:00';e='2026-03-27 20:10:00';r='MAINT';k=0},
@{eq='EQ-EXT-01';s='2026-03-28 08:20:00';e='2026-03-28 09:00:00';r='CHANGEOVER';k=1},
@{eq='EQ-COAT-02';s='2026-03-28 13:40:00';e='2026-03-28 14:45:00';r='FAULT';k=1},
@{eq='EQ-ASM-01';s='2026-03-29 09:15:00';e='2026-03-29 09:45:00';r='MAINT';k=0},
@{eq='EQ-STER-02';s='2026-03-29 23:10:00';e='2026-03-30 00:05:00';r='FAULT';k=1})
for($i=0;$i -lt $ev.Count;$i++){$s=[datetime]$ev[$i].s;$e=[datetime]$ev[$i].e;$de+=[pscustomobject][ordered]@{event_id=('DOWN{0:D5}' -f ($i+1));equipment_code=$ev[$i].eq;is_key_equipment=$ev[$i].k;downtime_start_time=F $s;downtime_end_time=F $e;duration_minutes=[int]($e-$s).TotalMinutes;reason_code=$ev[$i].r;source='mes';created_at=F ($e.AddMinutes(1))}}
W 'equipment_downtime_event' $de
$sol=@();$sl=@();$pc=@('P-ANGIO-001','P-BALLOON-001','P-STENT-001')
for($o=1;$o -le 15;$o++){$so=('SO20260322{0:D3}' -f $o);$ob=[datetime]::Parse('2026-03-18 08:00:00').AddDays(($o-1)%5).AddMinutes(20*$o);$ln=@('10','20','30');for($i=0;$i -lt $ln.Count;$i++){$l=$ln[$i];$pd=$pc[($o+$i)%3];$q=0;switch($pd){'P-ANGIO-001'{$q=250+$rand.Next(0,650)}'P-BALLOON-001'{$q=600+$rand.Next(0,1300)}'P-STENT-001'{$q=300+$rand.Next(0,900)}};$od=$ob.AddMinutes(15*$i);$dd=$od.AddDays(22+($o%9)+$i);$sd=$dd.AddDays(2);$u=if(($o%4 -eq 0)-or($q -ge 1200)){1}else{0};$sol+=[pscustomobject][ordered]@{sales_order_no=$so;line_no=$l;product_code=$pd;order_qty=$q;order_date=F $od;expected_due_date=F $dd;requested_ship_date=F $sd;urgent_flag=$u;order_status='APPROVED';last_update_time=F ($od.AddHours(1))};$sl+=[pscustomobject][ordered]@{sales_order_no=$so;line_no=$l;product_code=$pd;order_qty=$q;expected_due_date=F $dd;order_date=F $od}}}
W 'sales_order_line_fact' $sol

$po=@();$poMap=@{};$seq=1
foreach($x in $sl){$pn=('PO20260322{0:D4}' -f $seq);$k="$($x.sales_order_no)|$($x.line_no)";$poMap[$k]=$pn;$po+=[pscustomobject][ordered]@{plan_order_no=$pn;source_sales_order_no=$x.sales_order_no;source_line_no=$x.line_no;release_type='PRODUCTION';release_status='RELEASED';release_time=F(([datetime]$x.order_date).AddHours(4));last_update_time=F(([datetime]$x.order_date).AddHours(4).AddMinutes(10))};$seq++}
W 'plan_order' $po

$mo=@();$mos=@();$mseq=1
foreach($x in $sl){$pn=$poMap["$($x.sales_order_no)|$($x.line_no)"];$qty=[decimal]$x.order_qty;$parts=if($qty -ge 950){$a=[math]::Round($qty*0.6,0);@($a,($qty-$a))}else{@($qty)};foreach($pq in $parts){$mn=('MO20260322{0:D5}' -f $mseq);$ml=('ML20260322{0:D5}' -f $mseq);$st='OPEN';if($mseq%7 -eq 0){$st='IN_PROGRESS'};if($mseq%13 -eq 0){$st='DONE'};$upd=([datetime]$x.order_date).AddHours(6+($mseq%9));$mo+=[pscustomobject][ordered]@{production_order_no=$mn;source_sales_order_no=$x.sales_order_no;source_line_no=$x.line_no;source_plan_order_no=$pn;product_code=$x.product_code;plan_qty=[int]$pq;production_status=$st;last_update_time=F $upd};$mos+=[pscustomobject][ordered]@{production_order_no=$mn;source_sales_order_no=$x.sales_order_no;source_line_no=$x.line_no;source_plan_order_no=$pn;material_list_no=$ml;product_code=$x.product_code;plan_qty=[int]$pq;production_status=$st;due_date=$x.expected_due_date};$mseq++}}
W 'production_order_fact' $mo

$sc=@();foreach($m in $mos){$fr=if(([int]$m.production_order_no.Substring($m.production_order_no.Length-2))%17 -eq 0){1}else{0};$cl=if($m.production_status -eq 'DONE'){1}else{0};$sd=if($fr -eq 0 -and $cl -eq 0){1}else{0};$sc+=[pscustomobject][ordered]@{order_no=$m.production_order_no;order_type='production';review_passed_flag=1;frozen_flag=$fr;schedulable_flag=$sd;close_flag=$cl;promised_due_date=$m.due_date;last_update_time=F(([datetime]$m.due_date).AddDays(-5))}}
W 'schedule_control_fact' $sc

$mr=@();$rp=@('MRP20260322A','MRP20260322B','MRP20260322C','MRP20260323A')
for($i=0;$i -lt $mos.Count;$i++){$m=$mos[$i];$rt=[datetime]::Parse('2026-03-22 09:00:00').AddMinutes(18*$i);$mr+=[pscustomobject][ordered]@{order_no=$m.production_order_no;order_type='production';mrp_run_id=$rp[$i%$rp.Count];run_time=F $rt;last_update_time=F($rt.AddMinutes(5))};if($i%11 -eq 0){$rt2=$rt.AddDays(2);$mr+=[pscustomobject][ordered]@{order_no=$m.production_order_no;order_type='production';mrp_run_id='MRP20260324R';run_time=F $rt2;last_update_time=F($rt2.AddMinutes(8))}}}
W 'mrp_result_link' $mr
$mt=@{
'P-ANGIO-001'=@(@{c='M-RESIN-001';f=2.0;p='PROC-020'},@{c='M-WIRE-001';f=1.0;p='PROC-030'},@{c='M-COAT-001';f=0.5;p='PROC-050'},@{c='M-PACK-ANGIO';f=1.0;p='PROC-110'});
'P-BALLOON-001'=@(@{c='M-BAL-BASE';f=1.5;p='PROC-020'},@{c='M-CATH-TUBE';f=1.0;p='PROC-040'},@{c='M-COAT-002';f=0.4;p='PROC-050'},@{c='M-PACK-BAL';f=1.0;p='PROC-110'});
'P-STENT-001'=@(@{c='M-STENT-WIRE';f=1.2;p='PROC-060'},@{c='M-LASER-TUBE';f=1.0;p='PROC-030'},@{c='M-DRUG-COAT';f=0.3;p='PROC-090'},@{c='M-PACK-STENT';f=1.0;p='PROC-110'})}
$ml=@();$ma=@()
foreach($m in $mos){foreach($t in $mt[$m.product_code]){$rq=[math]::Round([decimal]$m.plan_qty*[decimal]$t.f,0);$ir=0.72+$rand.NextDouble()*0.35;if($ir -gt 1.08){$ir=1.08};$is=[math]::Round($rq*$ir,0);if($is -gt $rq){$is=$rq};$rd=if($is -ge $rq){1}else{0};$u=([datetime]$m.due_date).AddDays(-10).AddMinutes($rand.Next(10,800));$ml+=[pscustomobject][ordered]@{material_list_no=$m.material_list_no;production_order_no=$m.production_order_no;material_code=$t.c;required_qty=[int]$rq;issued_qty=[int]$is;ready_flag=$rd;last_update_time=F $u};$aq=if($rd -eq 1){[int]($rq+$rand.Next(0,80))}else{[int]([math]::Floor($rq*(0.45+$rand.NextDouble()*0.4)))};$at=$u.AddHours($rand.Next(2,48));$ma+=[pscustomobject][ordered]@{material_code=$t.c;order_no=$m.production_order_no;process_code=$t.p;available_qty=$aq;available_time=F $at;ready_flag=$rd;updated_at=F($at.AddMinutes(5))}}}
W 'production_material_list_item' $ml
W 'material_availability' $ma

$dp=@();foreach($m in $mos){$pl=[int]$m.plan_qty;$w=0;$s=0;if($m.production_status -eq 'DONE'){$w=$pl;$s=[int]([math]::Floor($pl*(0.75+$rand.NextDouble()*0.25)))}elseif($m.production_status -eq 'IN_PROGRESS'){$w=[int]([math]::Floor($pl*(0.25+$rand.NextDouble()*0.45)));$s=[int]([math]::Floor($w*(0.2+$rand.NextDouble()*0.6)))}else{$w=if($rand.NextDouble()-lt 0.2){[int]([math]::Floor($pl*0.1))}else{0};$s=if($w -gt 0){[int]([math]::Floor($w*0.5))}else{0}};if($s -gt $w){$s=$w};$ds='NOT_STARTED';if($s -ge $pl){$ds='SHIPPED'}elseif($w -gt 0 -or $s -gt 0){$ds='PARTIAL'};$dp+=[pscustomobject][ordered]@{order_no=$m.production_order_no;order_type='production';warehoused_qty=$w;shipped_qty=$s;delivery_status=$ds;last_update_time=F(([datetime]$m.due_date).AddDays(-2).AddHours($rand.Next(0,10)))}}
W 'delivery_progress' $dp

$rbp=@{'P-ANGIO-001'='R-ANGIO-V3';'P-BALLOON-001'='R-BALLOON-V2';'P-STENT-001'='R-STENT-V5'}
$rf=@();$rseq=1
foreach($m in $mos){$rt=$rbp[$m.product_code];$stp=$rm[$rt];$cnt=1;if($m.production_status -eq 'IN_PROGRESS'){$cnt=[math]::Min($stp.Count,3+$rand.Next(0,3))};if($m.production_status -eq 'DONE'){$cnt=$stp.Count};if($m.production_status -eq 'OPEN' -and $rand.NextDouble() -lt 0.45){$cnt=2};$st=[datetime]'2026-03-23 08:00:00';$st=$st.AddHours(($rseq%36));for($i=0;$i -lt $cnt;$i++){$ra=if($cnt -eq 1){0.2}else{[math]::Min(1.0,0.18+(($i+1)*0.16))};if($m.production_status -eq 'DONE'){$ra=1.0};$rq=[int][math]::Round([decimal]$m.plan_qty*[decimal]$ra,0);if($rq -le 0){$rq=[int][math]::Ceiling([decimal]$m.plan_qty*0.1)};if($rq -gt $m.plan_qty){$rq=[int]$m.plan_qty};$rtm=$st.AddHours(8*$i+$rand.Next(0,3));$sh=if($rtm.Hour -ge 8 -and $rtm.Hour -lt 20){'DAY'}else{'NIGHT'};$tm=if($m.product_code -eq 'P-STENT-001'){'TEAM-B03'}else{'TEAM-A02'};$rf+=[pscustomobject][ordered]@{report_id=('REP20260322{0:D6}' -f $rseq);order_no=$m.production_order_no;order_type='production';process_code=$stp[$i];report_qty=$rq;report_time=F $rtm;shift_code=$sh;team_code=$tm;created_at=F($rtm.AddMinutes(1))};$rseq++}}
W 'reporting_fact' $rf

$dc=@();$da=@();$ct=@('PRIORITY','LOCK','UNLOCK','FREEZE','UNFREEZE','INSERT');$pl=@('planner01','planner02','planner03');$ap=@('manager01','manager02')
for($i=1;$i -le 16;$i++){$tg=$mos[$rand.Next(0,$mos.Count)];$tm=[datetime]'2026-03-23 09:00:00';$tm=$tm.AddHours($i*3);$id=('CMD20260322{0:D4}' -f $i);$tp=$ct[($i-1)%$ct.Count];$rs=switch($tp){'PRIORITY'{'customer_urgent_request'}'LOCK'{'quality_hold_investigation'}'UNLOCK'{'quality_release'}'FREEZE'{'material_shortage_risk'}'UNFREEZE'{'material_replenished'}'INSERT'{'new_hot_order_insert'}default{'manual_adjustment'}};$dc+=[pscustomobject][ordered]@{command_id=$id;command_type=$tp;target_order_no=$tg.production_order_no;target_order_type='production';effective_time=F($tm.AddMinutes(20));reason=$rs;created_by=$pl[($i-1)%$pl.Count];created_at=F $tm};$ds=if($i%7 -eq 0){'REJECTED'}else{'APPROVED'};$da+=[pscustomobject][ordered]@{approval_id=('APR20260322{0:D4}' -f $i);command_id=$id;approver=$ap[($i-1)%$ap.Count];decision=$ds;decision_reason=if($ds -eq 'APPROVED'){'risk_assessed_and_accepted'}else{'insufficient_business_justification'};decision_time=F($tm.AddMinutes(35))}}
W 'dispatch_command' $dc
W 'dispatch_command_approval' $da

$sum=@();Get-ChildItem -Path $out -File -Filter '*.csv'|Sort-Object Name|ForEach-Object{$cnt=(Get-Content $_.FullName).Count-1;$sum+=[pscustomobject][ordered]@{file_name=$_.Name;row_count=$cnt}}
$sum|Export-Csv -Path (Join-Path $out '00_文件清单.csv') -NoTypeInformation -Encoding UTF8

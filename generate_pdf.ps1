$enc = [System.Text.Encoding]::ASCII

$p1 = "BT`r`n/F2 20 Tf`r`n50 750 Td`r`n(ValuePics - How To Guide) Tj`r`n/F1 11 Tf`r`n0 -35 Td`r`n/F2 14 Tf`n(1. Overview) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(ValuePics helps you photograph household items, estimate their value,) Tj`r`n0 -16 Td`r`n(and keep records in one place. Each item can be grouped into a) Tj`r`n0 -16 Td`r`n(collection, making it easy to track totals by room or category.) Tj`r`n0 -16 Td`r`n(All data is stored on your device. Automatic backups run at startup.) Tj`r`n0 -28 Td`r`n/F2 14 Tf`n(2. Add a New Item) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Tap the + button on the main screen to begin.) Tj`r`n0 -16 Td`r`n(Choose Camera to take a new photo or Gallery to import an image.) Tj`r`n0 -16 Td`r`n(Optionally check Do Not Include in Totals to exclude this item.) Tj`r`n0 -16 Td`r`n(Review and edit the item fields, then tap Save or Save and Add Another.) Tj`r`n0 -28 Td`r`n/F2 14 Tf`n(3. Valuation Fields) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Item Name: short title for the object.) Tj`r`n0 -16 Td`r`n(Description: your notes or key characteristics.) Tj`r`n0 -16 Td`r`n(Short AI Description: generated summary from image recognition.) Tj`r`n0 -16 Td`r`n(Estimated Value: market estimate when available.) Tj`r`n0 -16 Td`r`n(Notes: web or user-sourced descriptions.) Tj`r`n0 -16 Td`r`n(Tags: up to 6 comma-separated keywords for grouping and filtering.) Tj`r`n0 -16 Td`r`n(Collection: group name used for roll-up totals.) Tj`r`nET`r`n"

$p2 = "BT`r`n/F2 14 Tf`n50 750 Td`r`n(4. Search and Collections) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Use the Search icon to find items across all collections or within one.) Tj`r`n0 -16 Td`r`n(Add multiple tags to filter results by keywords.) Tj`r`n0 -16 Td`r`n(Use the Return button to clear filters and view all items.) Tj`r`n0 -16 Td`r`n(The top summary card shows item count and total value.) Tj`r`n0 -28 Td`r`n/F2 14 Tf`n(5. Item Details and Editing) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Tap any item card to open full details.) Tj`r`n0 -16 Td`r`n(Tap the pencil icon to edit fields, or edit inline.) Tj`r`n0 -16 Td`r`n(Small x buttons in edit fields clear that field.) Tj`r`n0 -16 Td`r`n(Use Lookup to fetch fresh web descriptions.) Tj`r`n0 -16 Td`r`n(Status chip shows if data was Web sourced or User sourced.) Tj`r`n0 -28 Td`r`n/F2 14 Tf`r`n(6. Collections Management) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Create new collections when adding an item or via Manage Collections.) Tj`r`n0 -16 Td`r`n(Edit or delete collections from the Tools menu.) Tj`r`n0 -16 Td`r`n(When you check Do Not Include in Totals, an Other tag is auto-added.) Tj`r`n0 -16 Td`r`n(You can still place the item in any collection - the choice is yours.) Tj`r`n0 -16 Td`r`n(Removing the Do Not Include check will automatically remove the Other tag.) Tj`r`nET`r`n"

$p3 = "BT`r`n/F2 14 Tf`n50 750 Td`r`n(7. Backup and Restore) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Automatic backups run at app startup using A/B rotation.) Tj`r`n0 -16 Td`r`n(Tap Backup in Tools to manually export a ZIP of all items and photos.) Tj`r`n0 -16 Td`r`n(Tap Restore to import a backup ZIP - replaces current database.) Tj`r`n0 -16 Td`r`n(Tap Merge to import a backup ZIP and combine with current database.) Tj`r`n0 -16 Td`r`n(Last backup time is shown in the About screen.) Tj`r`n0 -28 Td`r`n/F2 14 Tf`r`n(8. About and Tools) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(About: app version, database size, photo folder size, last backup time.) Tj`r`n0 -16 Td`r`n(Export PDF generates a collection summary report.) Tj`r`n0 -16 Td`r`n(Email Database shares your entire database as a ZIP attachment.) Tj`r`n0 -16 Td`r`n(Dark Mode toggle: choose Light, Dark, or System theme.) Tj`r`n0 -28 Td`r`n/F2 14 Tf`r`n(9. Sharing and Collaboration) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Share photos via the item details screen.) Tj`r`n0 -16 Td`r`n(Merge databases from other devices: export ZIP on device A,) Tj`r`n0 -16 Td`r`n(then import/merge on device B. Photos renamed to avoid conflicts.) Tj`r`n0 -28 Td`r`n/F2 14 Tf`r`n(10. Tips and Best Practices) Tj`r`n/F1 11 Tf`r`n0 -18 Td`r`n(Use clear collection names: Lounge Room, Garage, Jewelry, Kitchen.) Tj`r`n0 -16 Td`r`n(Use tags to cross-reference items across collections.) Tj`r`n0 -16 Td`r`n(Manual backups recommended before bulk edits or large changes.) Tj`r`n0 -16 Td`r`n(Treat estimated values as guidance; verify high-value items externally.) Tj`r`n0 -20 Td`r`n/F2 12 Tf`r`n(ValuePics - Wally Horsman  0418 889 633  |  Dedicated to Carmen.) Tj`r`nET`r`n"

$p1b = $enc.GetBytes($p1)
$p2b = $enc.GetBytes($p2)
$p3b = $enc.GetBytes($p3)

function MakeStream([byte[]]$data) {
    $hdr = $enc.GetBytes("<< /Length $($data.Length) >>`r`nstream`r`n")
    $ftr = $enc.GetBytes("`r`nendstream`r`nendobj`r`n")
    return @($hdr, $data, $ftr)
}

$obj1  = $enc.GetBytes("1 0 obj`r`n<< /Type /Catalog /Pages 2 0 R >>`r`nendobj`r`n")
$obj2  = $enc.GetBytes("2 0 obj`r`n<< /Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3 >>`r`nendobj`r`n")
$obj3  = $enc.GetBytes("3 0 obj`r`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 9 0 R /F2 10 0 R >> >> >>`r`nendobj`r`n")
$s4    = MakeStream $p1b
$obj5  = $enc.GetBytes("5 0 obj`r`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 6 0 R /Resources << /Font << /F1 9 0 R /F2 10 0 R >> >> >>`r`nendobj`r`n")
$s6    = MakeStream $p2b
$obj7  = $enc.GetBytes("7 0 obj`r`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 8 0 R /Resources << /Font << /F1 9 0 R /F2 10 0 R >> >> >>`r`nendobj`r`n")
$s8    = MakeStream $p3b
$obj9  = $enc.GetBytes("9 0 obj`r`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>`r`nendobj`r`n")
$obj10 = $enc.GetBytes("10 0 obj`r`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>`r`nendobj`r`n")
$hdr   = $enc.GetBytes("%PDF-1.4`r`n")

function Len([object]$x) {
    if ($x -is [byte[]]) { return $x.Length }
    return ($x | Measure-Object -Property Length -Sum).Sum
}

$offsets = @{}
$pos = $hdr.Length
$offsets[1]  = $pos; $pos += $obj1.Length
$offsets[2]  = $pos; $pos += $obj2.Length
$offsets[3]  = $pos; $pos += $obj3.Length
$offsets[4]  = $pos; $pos += (Len $s4[0]) + (Len $s4[1]) + (Len $s4[2])
$offsets[5]  = $pos; $pos += $obj5.Length
$offsets[6]  = $pos; $pos += (Len $s6[0]) + (Len $s6[1]) + (Len $s6[2])
$offsets[7]  = $pos; $pos += $obj7.Length
$offsets[8]  = $pos; $pos += (Len $s8[0]) + (Len $s8[1]) + (Len $s8[2])
$offsets[9]  = $pos; $pos += $obj9.Length
$offsets[10] = $pos; $pos += $obj10.Length

$xrefPos = $pos
$xrefStr = "xref`r`n0 11`r`n0000000000 65535 f `r`n"
for ($i = 1; $i -le 10; $i++) {
    $xrefStr += ("{0:D10} 00000 n `r`n" -f $offsets[$i])
}
$xrefStr += "trailer`r`n<< /Size 11 /Root 1 0 R >>`r`nstartxref`r`n$xrefPos`r`n%%EOF`r`n"

$out = [System.IO.FileStream]::new("C:\wrhor\DataBase\app\src\main\assets\how_to.pdf", [System.IO.FileMode]::Create)
foreach ($chunk in @($hdr, $obj1, $obj2, $obj3, $s4[0], $s4[1], $s4[2], $obj5, $s6[0], $s6[1], $s6[2], $obj7, $s8[0], $s8[1], $s8[2], $obj9, $obj10)) {
    if ($chunk -ne $null) { $out.Write($chunk, 0, $chunk.Length) }
}
$xb = $enc.GetBytes($xrefStr)
$out.Write($xb, 0, $xb.Length)
$out.Close()

Write-Host "Done: $((Get-Item 'C:\wrhor\DataBase\app\src\main\assets\how_to.pdf').Length) bytes"

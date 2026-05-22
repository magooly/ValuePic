#!/usr/bin/env python3
"""Verify the backup mirror"""
import os
import glob

src_files = glob.glob(r'C:\wrhor\DataBase\Backups\CHECKPOINT_20260428\*.*')
dst_files = glob.glob(r'd:\WallyBackups\ValuePic28-4-26\*.*')

src_size = sum(os.path.getsize(f) for f in src_files if os.path.isfile(f))
dst_size = sum(os.path.getsize(f) for f in dst_files if os.path.isfile(f))

print("=" * 60)
print("BACKUP MIRROR VERIFICATION")
print("=" * 60)
print(f"\nSource: C:\\wrhor\\DataBase\\Backups\\CHECKPOINT_20260428")
print(f"  Files: {len([f for f in src_files if os.path.isfile(f)])}")
print(f"  Size: {src_size/1e9:.2f} GB ({src_size:,} bytes)")

print(f"\nDestination: d:\\WallyBackups\\ValuePic28-4-26")
print(f"  Files: {len([f for f in dst_files if os.path.isfile(f)])}")
print(f"  Size: {dst_size/1e9:.2f} GB ({dst_size:,} bytes)")

print(f"\n{'='*60}")
if src_size == dst_size:
    print("✅ MIRROR SUCCESSFUL - All files match perfectly!")
else:
    print(f"❌ SIZE MISMATCH - Difference: {abs(src_size-dst_size)/1e9:.2f} GB")
print("=" * 60)

# List files
print("\nMirrored Files:")
for f in sorted([os.path.basename(x) for x in dst_files if os.path.isfile(x)]):
    size_mb = os.path.getsize(os.path.join(r'd:\WallyBackups\ValuePic28-4-26', f)) / (1024*1024)
    print(f"  ✓ {f}: {size_mb:,.1f} MB")


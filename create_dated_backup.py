#!/usr/bin/env python3
"""
Create a full checkpoint backup with today's date
Includes all source code, documentation, and audit reports
"""

import os
import shutil
import zipfile
import json
from datetime import datetime
from pathlib import Path

def create_backup_manifest(source_path, backup_date):
    """Create a manifest of what's included in the backup"""
    manifest = {
        "backup_date": backup_date.isoformat(),
        "backup_timestamp": int(backup_date.timestamp()),
        "purpose": "Full checkpoint backup - Code audit complete",
        "sections": {
            "source_code": "Complete Kotlin/Android source files",
            "build_files": "Gradle configuration and build artifacts",
            "documentation": "Project documentation and guides",
            "audit_reports": "Complete code audit reports (Markdown & PDF)",
            "database_schemas": "Room database schema files"
        },
        "included_items": []
    }

    # Scan what's being backed up
    for root, dirs, files in os.walk(source_path):
        # Skip certain directories
        skip_dirs = {'.gradle', 'build', '.idea', 'temp', '__pycache__', 'releases'}
        dirs[:] = [d for d in dirs if d not in skip_dirs]

        for file in files:
            rel_path = os.path.relpath(os.path.join(root, file), source_path)
            manifest["included_items"].append(rel_path)

    manifest["total_files"] = len(manifest["included_items"])

    return manifest

def create_zip_backup(source_path, backup_zip_path, backup_date):
    """Create a zip backup of the entire database"""
    print(f"\n📦 Creating ZIP backup: {backup_zip_path}")

    try:
        with zipfile.ZipFile(backup_zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            skip_dirs = {'.gradle', 'build', '.idea', 'temp', '__pycache__', '.git', 'releases'}
            skip_files = {'.DS_Store', '*.tmp', '*.log'}

            for root, dirs, files in os.walk(source_path):
                dirs[:] = [d for d in dirs if d not in skip_dirs]

                for file in files:
                    file_path = os.path.join(root, file)
                    rel_path = os.path.relpath(file_path, source_path)

                    # Skip certain file types
                    if file.endswith('.tmp') or file.endswith('.log'):
                        continue

                    try:
                        zipf.write(file_path, rel_path)
                    except Exception as e:
                        print(f"   ⚠️  Skipped: {rel_path} ({e})")

        zip_size_mb = os.path.getsize(backup_zip_path) / (1024 * 1024)
        print(f"   ✅ ZIP created: {zip_size_mb:.1f} MB")
        return True

    except Exception as e:
        print(f"   ❌ Error creating ZIP: {e}")
        return False

def create_backup_index(backup_root, backup_date):
    """Create an index file listing all backups"""
    index_file = os.path.join(backup_root, "BACKUPS_INDEX.md")

    # Scan for existing backups
    backups = []
    for item in os.listdir(backup_root):
        item_path = os.path.join(backup_root, item)
        if os.path.isdir(item_path) and item.startswith("CHECKPOINT_"):
            try:
                # Try to read metadata
                manifest_path = os.path.join(item_path, "MANIFEST.json")
                if os.path.exists(manifest_path):
                    with open(manifest_path, 'r') as f:
                        manifest = json.load(f)
                        backups.append({
                            "folder": item,
                            "date": manifest.get("backup_date", "Unknown"),
                            "files": manifest.get("total_files", "Unknown")
                        })
            except:
                pass

    # Sort by date (newest first)
    backups.sort(key=lambda x: x["date"], reverse=True)

    with open(index_file, 'w') as f:
        f.write("# ValueFinder Backup Checkpoints\n\n")
        f.write(f"**Last Updated:** {datetime.now().strftime('%B %d, %Y at %H:%M:%S')}\n\n")
        f.write("## Available Backups\n\n")

        if backups:
            f.write("| Folder | Date | Files | Description |\n")
            f.write("|--------|------|-------|-------------|\n")

            for backup in backups:
                date_str = backup["date"][:10]
                f.write(f"| {backup['folder']} | {date_str} | {backup['files']} | Full checkpoint |\n")
        else:
            f.write("*No backups found*\n")

        f.write("\n## Directory Structure\n\n")
        f.write("```\nBackups/\n")
        for backup in backups[:5]:  # Show latest 5
            f.write(f"├── {backup['folder']}/\n")
            f.write(f"│   ├── MANIFEST.json\n")
            f.write(f"│   ├── checkpoint.zip\n")
            f.write(f"│   ├── src/\n")
            f.write(f"│   └── ...\n")
        f.write("```\n")

    return index_file

def main():
    """Main backup function"""

    # Use today's date
    backup_date = datetime(2026, 4, 28)
    date_str = backup_date.strftime("%Y%m%d")

    source_path = r"C:\wrhor\DataBase"
    backup_root = os.path.join(source_path, "Backups")
    backup_folder = os.path.join(backup_root, f"CHECKPOINT_{date_str}")

    print("=" * 70)
    print("CREATING FULL CHECKPOINT BACKUP")
    print("=" * 70)
    print(f"📅 Backup Date: {backup_date.strftime('%B %d, %Y')}")
    print(f"📁 Source: {source_path}")
    print(f"📦 Destination: {backup_folder}")

    # Create backup directory structure
    print(f"\n📂 Creating backup directory structure...")
    try:
        os.makedirs(backup_folder, exist_ok=True)
        print(f"   ✅ Directory created: {backup_folder}")
    except Exception as e:
        print(f"   ❌ Error creating directory: {e}")
        return False

    # Create manifest
    print(f"\n📋 Creating backup manifest...")
    try:
        manifest = create_backup_manifest(source_path, backup_date)
        manifest_path = os.path.join(backup_folder, "MANIFEST.json")
        with open(manifest_path, 'w') as f:
            json.dump(manifest, f, indent=2)
        print(f"   ✅ Manifest created: {manifest['total_files']} files")
    except Exception as e:
        print(f"   ❌ Error creating manifest: {e}")

    # Create ZIP backup
    backup_zip = os.path.join(backup_folder, f"checkpoint_{date_str}.zip")
    if not create_zip_backup(source_path, backup_zip, backup_date):
        return False

    # Copy key files directly (for quick access)
    print(f"\n📄 Copying key files...")
    key_files = [
        "README.md",
        "CODE_AUDIT_REPORT.md",
        "CODE_AUDIT_REPORT.pdf",
        "AUDIT_SUMMARY.md",
        "AUDIT_SUMMARY.pdf",
        "AUDIT_INDEX.md",
        "AUDIT_INDEX.md.pdf",
        "AUDIT_EXECUTIVE_SUMMARY.txt",
        "AUDIT_EXECUTIVE_SUMMARY.pdf",
        "build.gradle.kts",
        "settings.gradle.kts"
    ]

    for file in key_files:
        src = os.path.join(source_path, file)
        if os.path.exists(src):
            dst = os.path.join(backup_folder, file)
            try:
                shutil.copy2(src, dst)
                print(f"   ✅ {file}")
            except Exception as e:
                print(f"   ⚠️  {file}: {e}")

    # Create README for this backup
    print(f"\n📝 Creating backup README...")
    readme_path = os.path.join(backup_folder, "BACKUP_README.md")
    with open(readme_path, 'w') as f:
        f.write(f"# Checkpoint Backup - {backup_date.strftime('%B %d, %Y')}\n\n")
        f.write(f"## Backup Information\n\n")
        f.write(f"- **Date Created:** {backup_date.strftime('%B %d, %Y at %H:%M:%S')}\n")
        f.write(f"- **Checkpoint Type:** Full Code Audit Checkpoint\n")
        f.write(f"- **Status:** ✅ Code audit complete - No code changes made\n")
        f.write(f"- **Purpose:** Complete snapshot after comprehensive code audit review\n\n")

        f.write(f"## Contents\n\n")
        f.write(f"### Files in This Backup\n\n")
        f.write(f"- `checkpoint_{date_str}.zip` - Complete compressed backup of all source files\n")
        f.write(f"- `MANIFEST.json` - Detailed manifest of all backed up files\n")
        f.write(f"- `BACKUP_README.md` - This file\n\n")

        f.write(f"### Audit Reports (Quick Access)\n\n")
        f.write(f"- `CODE_AUDIT_REPORT.md` / `.pdf` - Comprehensive 12-section audit\n")
        f.write(f"- `AUDIT_SUMMARY.md` / `.pdf` - Quick reference summary\n")
        f.write(f"- `AUDIT_INDEX.md` / `.pdf` - Navigation guide\n")
        f.write(f"- `AUDIT_EXECUTIVE_SUMMARY.txt` / `.pdf` - Executive summary\n\n")

        f.write(f"### Key Configuration Files\n\n")
        f.write(f"- `build.gradle.kts` - App build configuration\n")
        f.write(f"- `settings.gradle.kts` - Project settings\n")
        f.write(f"- `README.md` - Project documentation\n\n")

        f.write(f"## Recovering from This Backup\n\n")
        f.write(f"### Option 1: Extract ZIP\n")
        f.write(f"```bash\n")
        f.write(f"unzip checkpoint_{date_str}.zip -d recovery_directory\n")
        f.write(f"```\n\n")

        f.write(f"### Option 2: Review Audit Reports\n")
        f.write(f"1. Read `AUDIT_EXECUTIVE_SUMMARY.txt` for overview (5 min)\n")
        f.write(f"2. Review `AUDIT_SUMMARY.md` for priorities (10 min)\n")
        f.write(f"3. Reference `CODE_AUDIT_REPORT.md` for detailed analysis\n\n")

        f.write(f"## Next Steps\n\n")
        f.write(f"Following code audit recommendations:\n")
        f.write(f"1. **Phase 1 (Weeks 1-2):** Security & quick wins\n")
        f.write(f"2. **Phase 2 (Weeks 3-4):** Code cleanup & refactoring\n")
        f.write(f"3. **Phase 3 (Weeks 5-6):** Testing & performance\n")
        f.write(f"4. **Phase 4 (Ongoing):** Long-term sustainability\n\n")

        f.write(f"See `CODE_AUDIT_REPORT.md` for detailed roadmap.\n")

    print(f"   ✅ README created")

    # Create backups index
    print(f"\n🗂️  Creating backup index...")
    try:
        index_file = create_backup_index(backup_root, backup_date)
        print(f"   ✅ Index created: {index_file}")
    except Exception as e:
        print(f"   ⚠️  Error creating index: {e}")

    # Print summary
    print("\n" + "=" * 70)
    print("✅ BACKUP COMPLETE")
    print("=" * 70)
    print(f"\n📦 Backup Location: {backup_folder}")
    print(f"\n📋 Backup Contents:")
    print(f"   ├── checkpoint_{date_str}.zip")
    print(f"   ├── MANIFEST.json")
    print(f"   ├── BACKUP_README.md")
    print(f"   ├── Audit Reports (MD + PDF)")
    print(f"   ├── Key Configuration Files")
    print(f"   └── Full Source Code\n")

    print(f"🔍 Backups Index: {backup_root}/BACKUPS_INDEX.md")
    print(f"📊 You can find all checkpoints in: {backup_root}\n")

    return True

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)


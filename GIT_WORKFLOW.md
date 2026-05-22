# Git Workflow (Simple)

This is a beginner-friendly workflow for working across multiple computers.

## 1) One-time setup (this computer)

```powershell
cd C:\wrhor\DataBase
git init
git branch -M main
```

If Git asks for identity:

```powershell
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## 2) First commit

```powershell
cd C:\wrhor\DataBase
git add .
git commit -m "Initial project snapshot"
```

## 3) Connect to GitHub (optional, recommended)

Create an empty repo on GitHub first, then:

```powershell
cd C:\wrhor\DataBase
git remote add origin https://github.com/<your-user>/<your-repo>.git
git push -u origin main
```

## 4) Daily routine (every computer)

Start of day:

```powershell
cd C:\wrhor\DataBase
git pull --rebase
```

During work:

```powershell
git add .
git commit -m "Short description of change"
```

End of day:

```powershell
git push
```

## 5) Safe feature workflow (recommended)

```powershell
cd C:\wrhor\DataBase
git checkout -b feat/<short-name>
# work, commit
git checkout main
git pull --rebase
git merge feat/<short-name>
git push
```

## 6) Version tags for releases

```powershell
cd C:\wrhor\DataBase
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

## 7) Quick status and history

```powershell
git status
git --no-pager log --oneline -n 15
```


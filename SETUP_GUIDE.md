# Wildlife FieldOps — Foolproof Setup Guide
> Version 3.0.0 | Last Updated: June 20, 2026

---

## PART 1: Get Your API Keys (15 minutes)

You need **3 free API keys**. Each step below gives you the exact clicks.

---

### 1A. Supabase Key (Database + Storage + Auth)

**What it does:** Stores your jobs, photos, inspections, and customer data in the cloud so your data syncs across all devices.

| Step | Action | What You See |
|------|--------|-------------|
| 1 | Go to **supabase.com** on your phone browser | Homepage with "Start your project" button |
| 2 | Tap **"Start your project"** | Sign-up page (use Google or email) |
| 3 | Create a new project. Name it: **`wildlife-fieldops`** | Project dashboard loads |
| 4 | Wait ~2 minutes for the project to finish initializing | Green checkmark appears |
| 5 | Tap **Project Settings** (gear icon) → **API** | Page with "Project URL" and "anon/public" key |
| 6 | **Copy the Project URL** (looks like `https://xxxxx.supabase.co`) | Save to your Notes app |
| 7 | **Copy the `anon/public` key** (starts with `eyJhbG...`) | Save to your Notes app |
| 8 | Go to **SQL Editor** (left sidebar) | Empty query editor |
| 9 | Paste [this SQL file](supabase/migrations/20240620_fix_pack.sql) into the editor | Full schema code |
| 10 | Tap **Run** | Success message |
| 11 | Go to **Storage** (left sidebar) → **New Bucket** | Create bucket form |
| 12 | Name: **`job-photos`** → Check **Public bucket** → Tap **Save** | Bucket created |

You now have:
- `VITE_SUPABASE_URL=https://xxxxx.supabase.co`
- `VITE_SUPABASE_ANON_KEY=eyJhbG...`

---

### 1B. Google Maps Key (GPS Map + Navigation)

**What it does:** Shows job locations on a map with GPS pins, satellite view, and turn-by-turn navigation.

| Step | Action | What You See |
|------|--------|-------------|
| 1 | Go to **console.cloud.google.com** on your phone browser | Google Cloud Console |
| 2 | Sign in with your Google account | Dashboard appears |
| 3 | Tap the **project selector** (top left) → **New Project** | Create project form |
| 4 | Project name: **`wildlife-fieldops`** → Tap **Create** | Project created |
| 5 | Tap the **hamburger menu** (3 lines) → **APIs & Services** → **Credentials** | Credentials page |
| 6 | Tap **+ CREATE CREDENTIALS** → **API key** | A long key appears (like `AIzaSyC...`) |
| 7 | **Copy the key** to your Notes app | Save it |
| 8 | Tap **Library** (left side) → Search for **"Maps SDK for Android"** | API details page |
| 9 | Tap **Enable** | Enabled confirmation |
| 10 | Optional: enable **"Places API"** only if you use address/place search | Enabled confirmation |

You now have:
- `GOOGLE_MAPS_API=AIzaSyC...`

For Android key restrictions, use this application ID:

`com.strobingn.wildlifefieldops`

The key must allow **Maps SDK for Android**. A key restricted only to browser/JavaScript APIs will build successfully but the native map will remain blank.

---

### 1C. OpenWeather Key (Optional — Weather on Jobs)

**What it does:** Shows current weather conditions on job detail pages so you know what gear to bring.

| Step | Action |
|------|--------|
| 1 | Go to **openweathermap.org** |
| 2 | Tap **Sign Up** (free tier) |
| 3 | After signup, go to **API Keys** in your account |
| 4 | Copy the default key (starts with a long hex string) |

You now have:
- `VITE_OPENWEATHER_API_KEY=xxxxxxxxxx...`

---

## PART 2: Add Keys to GitHub (5 minutes)

This is the **ONLY time** you paste your keys. GitHub stores them encrypted and injects them into your builds.

| Step | Action | Screenshot |
|------|--------|-----------|
| 1 | Open **github.com** on your phone → Go to your **`wildlife-fieldops` repo** | Repo main page |
| 2 | Tap **Settings** tab (scroll right if needed) | Settings page |
| 3 | Scroll down → tap **Secrets and variables** → **Actions** | Secrets management page |
| 4 | Tap **New repository secret** | Name/value form |
| 5 | Name: **`VITE_SUPABASE_URL`** / Value: *your Supabase URL* → **Add secret** | Secret added |
| 6 | Tap **New repository secret** again | New form |
| 7 | Name: **`VITE_SUPABASE_ANON_KEY`** / Value: *your Supabase anon key* → **Add secret** | Secret added |
| 8 | Tap **New repository secret** | New form |
| 9 | Name: **`GOOGLE_MAPS_API`** / Value: *your Google Maps key* → **Add secret** | Secret added |
| 10 | (Optional) Name: **`VITE_OPENWEATHER_API_KEY`** / Value: *your weather key* → **Add secret** | Secret added |

Your secrets page should now look like this:

```
Repository secrets
------------------
VITE_SUPABASE_URL          ********
VITE_SUPABASE_ANON_KEY     ********
GOOGLE_MAPS_API             ********
VITE_OPENWEATHER_API_KEY   ********  (optional)
```

---

## PART 3: Build Your APK (3 minutes, fully automatic)

| Step | Action |
|------|--------|
| 1 | In your GitHub repo, tap **Actions** tab |
| 2 | Tap **"Build Android APK"** workflow |
| 3 | Tap the **gray "Run workflow"** button → **dropdown** → **"Run workflow"** |
| 4 | Wait ~3-5 minutes (green checkmark = success) |
| 5 | Tap the completed run → scroll to **Artifacts** |
| 6 | Download **`wildlife-fieldops-apk`** |
| 7 | Open the ZIP, install the APK |

---

## PART 4: Verify Everything Works (2 minutes)

Open the app on your Android phone and check:

| Feature | What to Test | Expected Result |
|---------|-------------|-----------------|
| GPS | Tap GPS tab → "My Location" | Shows your location on map |
| New Job | Tap Jobs → "+ New Job" → Fill form → Save | Job appears in list |
| Inspection | Tap Inspections → "+ New Inspection" → Save | Appears in schedule |
| Photo | Open a job → "Quick Photo" | Photo saves to job |
| Sync | Settings → "Sync Now" | Green checkmark appears |

---

## Troubleshooting

### "Build failed" in GitHub Actions
| Symptom | Fix |
|---------|-----|
| `npm ERR!` | Tap "Re-run jobs" — temporary network issue |
| `gradlew permission denied` | Already fixed in our workflow — re-run |
| Build passes but APK crashes | Make sure all 3 secrets are added (Part 2) |

### "Google Maps not showing"
- Go back to Google Cloud Console
- Make sure **Maps JavaScript API** AND **Places API** are both enabled
- Check that the key has no restrictions (or add `*.wildlifewhispererllc.com`)

### "Supabase sync not working"
- Check the SQL was run in Supabase SQL Editor (Step 9 of 1A)
- Check Storage bucket `job-photos` exists (Step 12 of 1A)
- Verify secrets are spelled exactly: `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`

---

## Security Notes

Your API keys are stored **encrypted** in GitHub Secrets. They:
- Cannot be read by anyone browsing your repo
- Cannot be extracted from the built APK (compiled away)
- Are only used during the GitHub build process
- Never appear in logs or code

---

## Need Help?

If you get stuck at any step, tell me:
1. What step you're on
2. What you see on your screen
3. Any error messages

I'll get you unstuck immediately.

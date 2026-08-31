# Building the APK locally

The Celias font files are NOT in this repository (commercial licence). Copy
them into `app/src/main/res/font/` first, named:

    celias_regular.otf   celias_light.otf   celias_thin.otf

Then:

```bash
python tools/bundle_data.py
gradle :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

# AutoTune EQ AI 0.3.3 candidate

**Yeh final release nahi hai.** Android 8.0 (API 26) se configuration support hai; Vivo Android 16 par installation ka exact error abhi phone ke ADB logs se confirm karna hai.

- **Full:** notification access ki clear consent ke saath automatic song metadata, profiles, optional FFT aur experimental external EQ. YouTube/Spotify EQ guaranteed nahi.
- **Lite:** manually gaane/artist ka naam, saved/optional online profiles aur app ki apni test-tone EQ. Ismein notification listener, recording permission, FFT ya external-player EQ nahi hai.
- Dono evaluation APKs development certificate se signed hain. Valid signature aur successful Gradle build Google Play Protect approval nahi hoti.
- Play Protect ya Android security band/bypass na karein. `docs/INSTALLATION_DIAGNOSTICS.md` ke commands se exact `INSTALL_FAILED` output collect karein. Script koi app uninstall ya security setting change nahi karti.

Build: JDK 17, SDK 35, `./gradlew assembleFullReview assembleLiteReview`. Tafseeli tests, limitations aur hashes `docs/BUILD_AND_TEST_REPORT.md` mein hain. Car park karke low volume par apni tone se test karein; audible EQ abhi physical hardware par verify hona baqi hai.


## 0.3.4: Lite par "Blind A/B" listening check (Vivo)
1. APKs GitHub Actions se banao: `docs/GITHUB_ACTIONS_GUIDE.md` (repo mein project ka *contents* upload karo → Actions → "Android build and verify" → Run workflow → Artifacts se `autotuneeq-apks-N` download → SHA-256 verify). Ye workflow abhi tak chala nahi, pehla run hi pehli real build hogi.
2. Volume **kam** rakho, headphones behtar hain, baaqi audio band karo, phone ki "sound enhancement" (Dolby/Hi-Fi/Audio effects) off rakho.
3. Lite kholo → "Start blind A/B check" → Start. ~25 chhoti tone pairs aayengi. Har pair ke baad batao: pehli quiet thi, dusri quiet thi, ya farq nahi.
4. End par band-wise result (AUDIBLE / INCONCLUSIVE / NOT HEARD) aur "false alarms" aayenge. "Restoration check" bhi chalao.
5. "Copy diagnostic report" dabao aur text mujhe bhejo. Isme sirf device model, bands aur jawab hote hain.
Detail: `docs/DEVICE_TEST_INSTRUCTIONS.md`.
